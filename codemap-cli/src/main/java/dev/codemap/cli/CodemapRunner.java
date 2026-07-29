package dev.codemap.cli;

import dev.codemap.core.CodemapOptions;
import dev.codemap.core.diff.ChangeAnalysisResult;
import dev.codemap.core.diff.GitChangeAnalyzer;
import dev.codemap.core.diff.GitChangeSource;
import dev.codemap.core.diff.GitCommandRunner;
import dev.codemap.core.index.IndexStore;
import dev.codemap.core.index.ProjectIndexer;
import dev.codemap.core.model.CodeIndex;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Locale;
import dev.codemap.core.model.IndexedMethod;
import dev.codemap.core.model.ChangeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives one analysis run from validated options.
 *
 * <p>Runs the indexing stage, then the diff stage on top of it, and persists the
 * result. Rendering is added behind this same call once it is built, so the
 * entry point does not reshape each time.
 */
public class CodemapRunner {

    private static final Logger log = LoggerFactory.getLogger(CodemapRunner.class);

    private static final String SENTENCE_END = ".";
    private static final String NO_CHANGES = "No changes against the comparison point";
    private static final String COUNTED_STATUS = "%d %s";
    private static final String SUMMARY_SEPARATOR = ", ";
    private static final String REMOVED_LABEL = "removed";

    private static final String RENDER_PENDING =
            "Rendering is not implemented yet — the index was written, but there is no report to open.";
    private static final String DIFF_UNRESOLVED =
            "Change status was not computed: {} The map will show no git overlay.";

    private final ProjectIndexer indexer;
    private final IndexStore indexStore;
    private final GitChangeAnalyzer changeAnalyzer;

    public CodemapRunner() {
        this(new ProjectIndexer(), new IndexStore(), new GitChangeAnalyzer(new GitChangeSource(new GitCommandRunner())));
    }

    CodemapRunner(ProjectIndexer indexer, IndexStore indexStore, GitChangeAnalyzer changeAnalyzer) {
        this.indexer = indexer;
        this.indexStore = indexStore;
        this.changeAnalyzer = changeAnalyzer;
    }

    /**
     * Runs the pipeline for the given options.
     *
     * @param options validated inputs for this run
     * @return the process exit code
     */
    public int run(CodemapOptions options) {
        log.info("Project root  : {}", options.root());
        log.info("Comparison    : {}", describeComparison(options));
        log.info("Index         : {}", options.indexPath());

        CodeIndex index = indexer.index(options.root());
        index = analyzeChanges(options, index);
        indexStore.write(index, options.indexPath());

        log.warn(RENDER_PENDING);
        return ExitCode.SUCCESS;
    }

    /**
     * Attaches change status to the index, degrading to the plain static index
     * with a warning when the diff cannot be resolved rather than failing the run.
     */
    private CodeIndex analyzeChanges(CodemapOptions options, CodeIndex index) {
        ChangeAnalysisResult result = changeAnalyzer.analyze(
                options.root(), options.comparisonMode(), options.base(), options.since(), index);
        if (!result.isResolved()) {
            log.warn(DIFF_UNRESOLVED, endWithPeriod(result.failureReason()));
            return result.index();
        }
        logChangeSummary(result.index());
        return result.index();
    }

    /**
     * Ensures a reason reads as a sentence before it is joined to what follows.
     *
     * <p>Reasons come from several call sites and some already end in a period.
     * Appending one unconditionally produced "Pass --base &lt;branch&gt;.. The map",
     * which reads as a typo in the tool rather than in the repository.
     */
    private static String endWithPeriod(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        return reason.endsWith(SENTENCE_END) ? reason : reason + SENTENCE_END;
    }

    /**
     * Reports what the diff found.
     *
     * <p>Change status is the reason many runs happen at all, so it belongs on the
     * console beside the index counts rather than only inside the file. Statuses
     * with no members are left out, so an untouched project stays quiet instead of
     * printing a row of zeroes.
     */
    private void logChangeSummary(CodeIndex index) {
        Map<ChangeStatus, Long> byStatus = index.methods().stream()
                .map(IndexedMethod::status)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(status -> status, Collectors.counting()));

        String summary = Stream.of(ChangeStatus.CHANGED, ChangeStatus.ADDED, ChangeStatus.AFFECTED)
                .filter(status -> byStatus.getOrDefault(status, 0L) > 0)
                .map(status -> COUNTED_STATUS.formatted(byStatus.get(status), status.name().toLowerCase(Locale.ROOT)))
                .collect(Collectors.joining(SUMMARY_SEPARATOR));

        int removed = index.removedMethods().size();
        if (summary.isEmpty() && removed == 0) {
            log.info(NO_CHANGES);
            return;
        }
        if (removed > 0) {
            String removedPart = COUNTED_STATUS.formatted(removed, REMOVED_LABEL);
            summary = summary.isEmpty() ? removedPart : summary + SUMMARY_SEPARATOR + removedPart;
        }
        log.info("Changes: {}", summary);
    }

    /**
     * Describes the comparison in the terms the user chose it, so the log shows
     * what will be measured rather than which flags were parsed.
     */
    private String describeComparison(CodemapOptions options) {
        return switch (options.comparisonMode()) {
            case REVISION -> "changes since %s".formatted(options.since().orElseThrow());
            case BRANCH -> options.base()
                    .map("changes on this branch since it diverged from %s"::formatted)
                    .orElse("changes on this branch since it diverged from the default branch");
        };
    }
}
