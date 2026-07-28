package dev.codemap.cli;

import dev.codemap.core.CodemapOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives one analysis run from validated options.
 *
 * <p>The pipeline stages it will orchestrate — discover, parse, resolve, detect,
 * diff, render — do not exist yet. Until they do this reports the configuration
 * it resolved, which is what makes the argument handling verifiable on its own.
 *
 * <p>The class exists now rather than later so the CLI has a stable seam: each
 * stage is added behind this call without reshaping the entry point.
 */
public class CodemapRunner {

    private static final Logger log = LoggerFactory.getLogger(CodemapRunner.class);

    private static final String NOT_IMPLEMENTED_NOTICE =
            "Analysis is not implemented yet — this build resolves and reports configuration only.";

    /**
     * Runs the pipeline for the given options.
     *
     * @param options validated inputs for this run
     * @return the process exit code
     */
    public int run(CodemapOptions options) {
        log.info("Project root  : {}", options.root());
        log.info("Base revision : {}", options.base());
        options.since().ifPresent(since -> log.info("Since commit  : {}", since));
        log.info("Report output : {}", options.output());
        log.info("Index cache   : {}", options.indexPath());
        options.config().ifPresentOrElse(
                config -> log.info("Config file   : {}", config),
                () -> log.info("Config file   : none (using built-in rules)"));
        log.info("AI fallback   : {}", options.aiEnabled() ? "enabled" : "disabled");
        log.info("Rebuild index : {}", options.rebuild());

        log.warn(NOT_IMPLEMENTED_NOTICE);
        return ExitCode.SUCCESS;
    }
}
