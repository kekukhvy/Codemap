package dev.codemap.cli;

import dev.codemap.core.CodemapOptions;
import dev.codemap.core.InvalidOptionsException;
import dev.codemap.core.diff.GitCommandRunner;
import dev.codemap.core.diff.PullRequestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Command-line entry point.
 *
 * <p>Its job is to turn arguments into validated {@link CodemapOptions} and hand
 * them to the pipeline — no analysis happens here. Keeping the parsing separate
 * means the pipeline can be exercised without an argument parser, and the exit
 * code for any argument combination can be asserted in a test.
 */
@Command(
        name = "codemap",
        mixinStandardHelpOptions = true,
        versionProvider = CodemapCommand.ManifestVersionProvider.class,
        sortOptions = false,
        header = "Interactive mindmap of a Java application, rooted in entry points.",
        description = """

                Analyses a Java project statically and renders a self-contained \
                HTML map: modules, entry points, classes, methods, and the call \
                chains between them, with git changes highlighted on top.

                Run with no arguments to map the current directory. By default \
                changes are measured from the point this branch diverged from the \
                repository's default branch — the same set of changes a pull \
                request shows.

                The analysed project is never built or executed.""",
        footer = """

                Examples:
                  codemap                              map here, diff this branch's changes
                  codemap --root ../service            map another project
                  codemap --pr 48                      compare against that pull request's base
                  codemap --base develop               compare against a different branch
                  codemap --since HEAD~5               compare against an exact commit
                  codemap --ai                         classify unrecognised entry points
                """
)
public final class CodemapCommand implements Callable<Integer> {

    private static final String REPORT_HINT = "codemap/report.html (inside --root)";

    @Option(
            names = {"-r", "--root"},
            paramLabel = "<path>",
            description = "Java project to analyse. Default: current directory."
    )
    private Path root = Path.of(".");

    @Option(
            names = {"-b", "--base"},
            paramLabel = "<branch>",
            description = "Branch to compare against. Default: the repository's default branch."
    )
    private String base;

    @Option(
            names = {"-p", "--pr"},
            paramLabel = "<number>",
            description = "Pull request to compare against; its base branch is read with the gh CLI."
    )
    private Integer pullRequest;

    @Option(
            names = {"-s", "--since"},
            paramLabel = "<commit>",
            description = "Compare against this exact commit instead of the branch point."
    )
    private String since;

    @Option(
            names = {"-o", "--out"},
            paramLabel = "<path>",
            description = "Where to write the report. Default: " + REPORT_HINT + "."
    )
    private Path out;

    @Option(
            names = {"-c", "--config"},
            paramLabel = "<path>",
            description = "Custom entry-point rules. Default: codemap.yml in the project root, if present."
    )
    private Path config;

    @Option(
            names = "--ai",
            description = "Let the local claude CLI classify entry points the rules could not resolve."
    )
    private boolean ai;

    @Option(
            names = "--rebuild",
            description = "Ignore the cached index and reparse everything."
    )
    private boolean rebuild;

    private static final Logger log = LoggerFactory.getLogger(CodemapCommand.class);

    private final CodemapRunner runner;
    private final PullRequestBase pullRequestBase;

    /** Creates a command backed by the real pipeline runner. */
    public CodemapCommand() {
        this(new CodemapRunner(), new PullRequestBase(new GitCommandRunner()));
    }

    /**
     * Creates a command with an explicit runner, so tests can assert on the
     * options produced without running an analysis.
     *
     * @param runner receives the validated options
     */
    CodemapCommand(CodemapRunner runner) {
        this(runner, new PullRequestBase(new GitCommandRunner()));
    }

    /**
     * Creates a command with both collaborators explicit, so a test can drive
     * {@code --pr} without a {@code gh} binary or a network.
     *
     * @param runner receives the validated options
     * @param pullRequestBase resolves a pull-request number to its base branch
     */
    CodemapCommand(CodemapRunner runner, PullRequestBase pullRequestBase) {
        this.runner = runner;
        this.pullRequestBase = pullRequestBase;
    }

    @Override
    public Integer call() {
        CodemapOptions options = CodemapOptions.builder()
                .root(root)
                .base(resolveBase())
                .since(since)
                .output(out)
                .config(config)
                .aiEnabled(ai)
                .rebuild(rebuild)
                .build();

        return runner.run(options);
    }

    /**
     * The branch to compare against, resolving {@code --pr} when given.
     *
     * <p>A reader always knows the pull-request number; which branch it targets
     * is exactly the detail that is easy to get wrong, and getting it wrong is
     * the difference between a map showing a handful of real changes and one
     * painted almost entirely green.
     *
     * <p>An explicit {@code --base} wins: it is the more specific instruction.
     * A pull request that cannot be resolved falls back to the default base
     * rather than failing, and says so.
     */
    private String resolveBase() {
        if (pullRequest == null) {
            return base;
        }
        if (base != null) {
            log.warn("Both --base and --pr given; comparing against {}", base);
            return base;
        }
        return pullRequestBase.resolve(root, pullRequest)
                .map(resolved -> {
                    log.info("PR #{} targets {}", pullRequest, resolved);
                    return resolved;
                })
                .orElseGet(() -> {
                    log.warn("Could not resolve PR #{}; comparing against the default base branch", pullRequest);
                    return null;
                });
    }

    public static void main(String[] args) {
        System.exit(execute(args, new CommandLine(new CodemapCommand())));
    }

    /**
     * Runs the command and maps failures onto exit codes.
     *
     * <p>A bare {@code codemap} maps the current directory rather than printing
     * usage: every option has a working default, so the no-argument case is the
     * common one, not a mistake. {@code --help} remains the way to see the flags.
     *
     * @param args raw command-line arguments
     * @param commandLine configured picocli instance
     * @return the process exit code
     */
    static int execute(String[] args, CommandLine commandLine) {
        commandLine.setExecutionExceptionHandler(CodemapCommand::handleExecutionException);
        commandLine.setParameterExceptionHandler(CodemapCommand::handleParameterException);
        return commandLine.execute(args);
    }

    /**
     * Reports an unusable configuration as a one-line message rather than a stack
     * trace; anything unexpected keeps its trace, because that is a real bug.
     */
    private static int handleExecutionException(Exception e, CommandLine commandLine, CommandLine.ParseResult ignored) {
        if (e instanceof InvalidOptionsException) {
            printError(commandLine, e.getMessage());
            return ExitCode.INVALID_INPUT;
        }
        e.printStackTrace(commandLine.getErr());
        return ExitCode.INTERNAL_ERROR;
    }

    /** Reports a malformed argument list with usage, so the fix is visible. */
    private static int handleParameterException(CommandLine.ParameterException e, String[] ignored) {
        CommandLine commandLine = e.getCommandLine();
        printError(commandLine, e.getMessage());
        commandLine.usage(commandLine.getErr());
        return ExitCode.INVALID_INPUT;
    }

    private static void printError(CommandLine commandLine, String message) {
        PrintWriter err = commandLine.getErr();
        err.println(commandLine.getColorScheme().errorText(message));
    }

    /** Reads the version from the jar manifest, falling back for non-packaged runs. */
    static final class ManifestVersionProvider implements CommandLine.IVersionProvider {

        private static final String DEVELOPMENT_VERSION = "codemap (development build)";

        @Override
        public String[] getVersion() {
            Package pkg = CodemapCommand.class.getPackage();
            String version = pkg == null ? null : pkg.getImplementationVersion();
            return new String[]{version == null ? DEVELOPMENT_VERSION : "codemap " + version};
        }
    }
}
