package dev.codemap.core.diff;

import dev.codemap.core.model.ChangeStatus;
import dev.codemap.core.model.IndexedMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps diff line ranges onto method declarations to assign {@code added},
 * {@code changed}, and {@code removed} status (spec §5).
 *
 * <p>Does not compute {@link ChangeStatus#AFFECTED} — that needs the call
 * graph and is a separate pass over this result.
 */
public final class MethodStatusAssigner {

    /**
     * Assigns status to every given method from the given file diffs.
     *
     * @param methods currently indexed methods, across every file
     * @param fileDiffs per-file line ranges from {@link UnifiedDiffParser}
     * @return one status per method that overlaps a diff, plus removed ranges
     */
    public MethodStatusAssignment assign(List<IndexedMethod> methods, List<FileDiff> fileDiffs) {
        Map<String, List<IndexedMethod>> methodsByFile = methods.stream()
                .collect(Collectors.groupingBy(IndexedMethod::file));
        Map<String, ChangeStatus> statuses = new LinkedHashMap<>();
        List<RemovedMethodRange> removedRanges = new ArrayList<>();

        for (FileDiff fileDiff : fileDiffs) {
            List<IndexedMethod> methodsInFile = methodsByFile.getOrDefault(fileDiff.path(), List.of());
            assignChangedAndAdded(fileDiff, methodsInFile, statuses);
            removedRanges.addAll(toRemovedRanges(fileDiff));
        }
        return new MethodStatusAssignment(statuses, removedRanges);
    }

    private void assignChangedAndAdded(FileDiff fileDiff, List<IndexedMethod> methodsInFile, Map<String, ChangeStatus> statuses) {
        for (IndexedMethod method : methodsInFile) {
            LineRange methodRange = new LineRange(method.lineStart(), method.lineEnd());
            ChangeStatus status = statusOf(fileDiff, methodRange);
            if (status != ChangeStatus.UNCHANGED) {
                statuses.put(method.id(), status);
            }
        }
    }

    /**
     * {@code added} requires the whole file to be new (spec §5): a method that
     * happens to be entirely rewritten inside an existing file is still an
     * existing method, so it is {@code changed} rather than {@code added} even
     * when every one of its lines falls inside a changed range.
     */
    private ChangeStatus statusOf(FileDiff fileDiff, LineRange methodRange) {
        if (fileDiff.isNewFile() && isFullyCovered(methodRange, fileDiff.changedRanges())) {
            return ChangeStatus.ADDED;
        }
        boolean touchesAnyChangedRange = fileDiff.changedRanges().stream().anyMatch(methodRange::overlaps);
        return touchesAnyChangedRange ? ChangeStatus.CHANGED : ChangeStatus.UNCHANGED;
    }

    private boolean isFullyCovered(LineRange methodRange, List<LineRange> changedRanges) {
        return changedRanges.stream().anyMatch(methodRange::isContainedIn);
    }

    /**
     * Old-file ranges that represent a genuine deletion, reported against the old
     * path. A removed method has no current declaration to attach the range to
     * (spec §5: recovered from the diff alone, never by parsing the base
     * revision), so each surviving range is carried through as a placeholder.
     *
     * <p>Ranges overlapped by a new-file range are excluded. With {@code --unified=0}
     * git reports a rewritten line as a deletion and an addition at the same
     * place, so counting the deleted side would invent a phantom removal for every
     * ordinary edit — and that method is already reported as {@code changed}.
     */
    private List<RemovedMethodRange> toRemovedRanges(FileDiff fileDiff) {
        return fileDiff.removedRanges().stream()
                .filter(range -> !isRewrite(range, fileDiff))
                .map(range -> new RemovedMethodRange(fileDiff.oldPath(), range))
                .toList();
    }

    /**
     * Whether a deleted range was rewritten in place rather than dropped.
     *
     * <p>A rewrite replaces its lines: the range that came back is at least as
     * long as the one that went away. When fewer lines return than were removed,
     * something was genuinely deleted even though the remainder was rewritten —
     * deleting a method usually also edits the line above or below it.
     *
     * <p>This is as far as line numbers can take it. Telling "rewrote this method"
     * apart from "deleted a method and touched its neighbour" would need the base
     * revision's text, which spec §5 deliberately does not parse.
     */
    private boolean isRewrite(LineRange removed, FileDiff fileDiff) {
        if (fileDiff.isDeletedFile()) {
            return false;
        }
        int replacementLines = fileDiff.changedRanges().stream()
                .filter(added -> added.overlaps(removed))
                .mapToInt(LineRange::lineCount)
                .sum();
        return replacementLines >= removed.lineCount();
    }
}
