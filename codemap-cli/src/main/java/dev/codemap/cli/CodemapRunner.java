package dev.codemap.cli;

import dev.codemap.core.CodemapOptions;
import dev.codemap.core.index.IndexStore;
import dev.codemap.core.index.ProjectIndexer;
import dev.codemap.core.model.CodeIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives one analysis run from validated options.
 *
 * <p>Currently runs the indexing stage and persists the result. The remaining
 * stages — call graph, entry-point detection, diff, render — are added behind this
 * same call as they are built, so the entry point does not reshape each time.
 */
public class CodemapRunner {

    private static final Logger log = LoggerFactory.getLogger(CodemapRunner.class);

    private static final String RENDER_PENDING =
            "Rendering is not implemented yet — the index was written, but there is no report to open.";

    private final ProjectIndexer indexer;
    private final IndexStore indexStore;

    public CodemapRunner() {
        this(new ProjectIndexer(), new IndexStore());
    }

    CodemapRunner(ProjectIndexer indexer, IndexStore indexStore) {
        this.indexer = indexer;
        this.indexStore = indexStore;
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
        indexStore.write(index, options.indexPath());

        log.warn(RENDER_PENDING);
        return ExitCode.SUCCESS;
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
