package dev.codemap.core.diff;

import dev.codemap.core.ComparisonMode;
import dev.codemap.core.model.ChangeStatus;
import dev.codemap.core.model.CodeIndex;
import dev.codemap.core.model.IndexedClass;
import dev.codemap.core.model.IndexedMethod;
import dev.codemap.core.model.RemovedMethod;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The diff pipeline stage (spec §6.1): resolves the git diff for a run and
 * attaches {@code added}/{@code changed}/{@code removed}/{@code affected}
 * status to an already built {@link CodeIndex}.
 *
 * <p>Composes the smaller diff collaborators — {@link GitChangeSource},
 * {@link MethodStatusAssigner}, {@link AffectedMethodResolver}, and
 * {@link ClassStatusAggregator} — each independently tested. Never fails the
 * run: a git problem degrades to {@link ChangeAnalysisResult#unresolved}, the
 * index returned exactly as it was passed in.
 */
public final class GitChangeAnalyzer {

    private final GitChangeSource gitChangeSource;
    private final MethodStatusAssigner methodStatusAssigner = new MethodStatusAssigner();
    private final AffectedMethodResolver affectedMethodResolver = new AffectedMethodResolver();
    private final ClassStatusAggregator classStatusAggregator = new ClassStatusAggregator();

    public GitChangeAnalyzer(GitChangeSource gitChangeSource) {
        this.gitChangeSource = gitChangeSource;
    }

    /**
     * Attaches change status to the given index.
     *
     * @param repoRoot project root, expected to be inside a git repository
     * @param mode which comparison the run requested
     * @param base explicit {@code --base} branch, empty to auto-detect (BRANCH only)
     * @param since explicit {@code --since} commit (REVISION only)
     * @param index the already built index to attach status to
     * @return the index with status attached, or unresolved with a readable reason
     */
    public ChangeAnalysisResult analyze(Path repoRoot, ComparisonMode mode, Optional<String> base,
            Optional<String> since, CodeIndex index) {
        GitDiffOutcome outcome = gitChangeSource.resolve(repoRoot, mode, base, since);
        if (!outcome.isResolved()) {
            return ChangeAnalysisResult.unresolved(index, outcome.failureReason());
        }

        MethodStatusAssignment assignment = methodStatusAssigner.assign(index.methods(), outcome.fileDiffs());
        Set<String> affected = affectedMethodResolver.resolve(assignment.statusesByMethodId(), index.callGraph());

        List<IndexedMethod> statusedMethods = index.methods().stream()
                .map(method -> withStatus(method, assignment, affected))
                .toList();
        List<IndexedClass> statusedClasses = withClassStatus(index.classes(), statusedMethods);
        List<RemovedMethod> removedMethods = toRemovedMethods(assignment.removedRanges());

        return ChangeAnalysisResult.resolved(
                rebuildWithStatus(index, statusedClasses, statusedMethods, removedMethods));
    }

    private List<RemovedMethod> toRemovedMethods(List<RemovedMethodRange> removedRanges) {
        return removedRanges.stream()
                .map(removed -> new RemovedMethod(removed.file(), removed.range().startLine(), removed.range().endLine()))
                .toList();
    }

    private IndexedMethod withStatus(IndexedMethod method, MethodStatusAssignment assignment, Set<String> affected) {
        if (assignment.statusesByMethodId().containsKey(method.id())) {
            return method.withStatus(assignment.statusOf(method.id()));
        }
        if (affected.contains(method.id())) {
            return method.withStatus(ChangeStatus.AFFECTED);
        }
        return method.withStatus(ChangeStatus.UNCHANGED);
    }

    private List<IndexedClass> withClassStatus(List<IndexedClass> classes, List<IndexedMethod> statusedMethods) {
        Map<String, List<ChangeStatus>> statusesByClassId = statusedMethods.stream()
                .collect(Collectors.groupingBy(IndexedMethod::classId,
                        Collectors.mapping(IndexedMethod::status, Collectors.toList())));
        return classes.stream()
                .map(indexedClass -> indexedClass.withStatus(
                        classStatusAggregator.aggregate(statusesByClassId.getOrDefault(indexedClass.id(), List.of()))))
                .toList();
    }

    private CodeIndex rebuildWithStatus(CodeIndex index, List<IndexedClass> classes, List<IndexedMethod> methods,
            List<RemovedMethod> removedMethods) {
        return CodeIndex.builder()
                .schemaVersion(index.schemaVersion())
                .generatedAt(index.generatedAt())
                .root(index.root())
                .modules(index.modules())
                .classes(classes)
                .methods(methods)
                .removedMethods(removedMethods)
                .entryPoints(index.entryPoints())
                .files(index.files())
                .statistics(index.statistics())
                .calls(index.calls())
                .build();
    }
}
