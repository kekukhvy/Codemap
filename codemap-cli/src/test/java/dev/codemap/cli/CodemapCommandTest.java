package dev.codemap.cli;

import dev.codemap.core.CodemapOptions;
import dev.codemap.core.ComparisonMode;
import dev.codemap.core.diff.GitCommandResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CodemapCommandTest {

    private static final String[] DOCUMENTED_FLAGS =
            {"--root", "--base", "--pr", "--since", "--out", "--config", "--ai", "--rebuild"};

    private final RecordingRunner runner = new RecordingRunner();
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private int run(String... args) {
        return run(new PullRequestBase((binary, directory, arguments) -> new GitCommandResult(false, "", "no gh")), args);
    }

    private int run(PullRequestBase pullRequestBase, String... args) {
        CommandLine commandLine = new CommandLine(new CodemapCommand(runner, pullRequestBase))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true));
        return CodemapCommand.execute(args, commandLine);
    }

    @Nested
    @DisplayName("--pr")
    class PullRequestFlag {

        @Test
        @DisplayName("compares against the branch the pull request targets")
        void usesThePullRequestBase() {
            PullRequestBase resolving = new PullRequestBase(
                    (binary, directory, arguments) -> new GitCommandResult(true, "develop", ""));

            run(resolving, "--root", ".", "--pr", "48");

            assertThat(runner.options().base()).contains("develop");
        }

        /**
         * A pull request that cannot be resolved — no gh, not logged in, no
         * network — must leave the reader with the ordinary default rather than
         * failing the run.
         */
        @Test
        @DisplayName("falls back to the default base when the pull request cannot be resolved")
        void degradesWhenUnresolvable() {
            PullRequestBase failing = new PullRequestBase(
                    (binary, directory, arguments) -> new GitCommandResult(false, "", "gh not found"));

            int exitCode = run(failing, "--root", ".", "--pr", "48");

            assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
            assertThat(runner.options().base()).isEmpty();
        }

        /** The more specific instruction wins. */
        @Test
        @DisplayName("an explicit --base beats --pr")
        void explicitBaseWins() {
            PullRequestBase resolving = new PullRequestBase(
                    (binary, directory, arguments) -> new GitCommandResult(true, "develop", ""));

            run(resolving, "--root", ".", "--pr", "48", "--base", "main");

            assertThat(runner.options().base()).contains("main");
        }
    }

    @Nested
    @DisplayName("usage")
    class Usage {

        @Test
        @DisplayName("--help documents every flag the README promises")
        void helpDocumentsEveryFlag() {
            int exitCode = run("--help");

            assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
            assertThat(out.toString()).contains(DOCUMENTED_FLAGS);
        }

        @Test
        @DisplayName("no arguments maps the current directory instead of printing usage")
        void noArgumentsMapsCurrentDirectory() {
            int exitCode = run();

            assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
            assertThat(runner.wasInvoked()).as("a bare run should analyse, not just explain").isTrue();
            assertThat(runner.options().root())
                    .isEqualTo(Path.of(".").toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("--version reports something rather than crashing outside a jar")
        void versionIsAvailable() {
            int exitCode = run("--version");

            assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
            assertThat(out.toString()).contains("codemap");
        }
    }

    @Nested
    @DisplayName("argument errors")
    class ArgumentErrors {

        @Test
        @DisplayName("a missing --root exits non-zero with a readable message, not a stack trace")
        void missingRootExitsNonZero(@TempDir Path projectRoot) {
            int exitCode = run("--root", projectRoot.resolve("nope").toString());

            assertThat(exitCode).isEqualTo(ExitCode.INVALID_INPUT);
            assertThat(err.toString())
                    .contains("does not exist")
                    .doesNotContain("Exception", "\tat ");
        }

        @Test
        @DisplayName("an unknown flag exits non-zero and shows usage")
        void unknownFlagExitsNonZero() {
            int exitCode = run("--nonsense");

            assertThat(exitCode).isEqualTo(ExitCode.INVALID_INPUT);
            assertThat(err.toString()).contains("Unknown option", "Usage:");
        }

        @Test
        @DisplayName("a flag missing its value exits non-zero")
        void missingValueExitsNonZero() {
            int exitCode = run("--root");

            assertThat(exitCode).isEqualTo(ExitCode.INVALID_INPUT);
            assertThat(err.toString()).contains("Missing required parameter");
        }
    }

    @Nested
    @DisplayName("option wiring")
    class OptionWiring {

        @Test
        @DisplayName("passes a valid root through to the runner")
        void passesRootThrough(@TempDir Path projectRoot) {
            int exitCode = run("--root", projectRoot.toString());

            assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
            assertThat(runner.options().root()).isEqualTo(projectRoot.toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("defaults to this branch's changes, which is what a pull request shows")
        void defaultsToBranchComparison(@TempDir Path projectRoot) {
            run("--root", projectRoot.toString());

            assertThat(runner.options().comparisonMode()).isEqualTo(ComparisonMode.BRANCH);
            assertThat(runner.options().base()).isEmpty();
        }

        @Test
        @DisplayName("carries every flag into the options")
        void carriesEveryFlag(@TempDir Path projectRoot) {
            int exitCode = run(
                    "--root", projectRoot.toString(),
                    "--base", "main",
                    "--since", "HEAD~3",
                    "--out", "target/map.html",
                    "--config", "rules.yml",
                    "--ai",
                    "--rebuild");

            assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);

            CodemapOptions options = runner.options();
            assertThat(options.base()).contains("main");
            assertThat(options.since()).contains("HEAD~3");
            assertThat(options.comparisonMode()).isEqualTo(ComparisonMode.REVISION);
            // Compared as a string: the report does not exist yet, and AssertJ's
            // Path#endsWith resolves the real file on disk.
            assertThat(options.output().toString()).endsWith("target/map.html");
            assertThat(options.config()).isPresent();
            assertThat(options.aiEnabled()).isTrue();
            assertThat(options.rebuild()).isTrue();
        }

        @Test
        @DisplayName("supports short flag forms")
        void supportsShortFlags(@TempDir Path projectRoot) {
            int exitCode = run("-r", projectRoot.toString(), "-b", "develop");

            assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
            assertThat(runner.options().base()).contains("develop");
        }

        @Test
        @DisplayName("leaves AI off unless asked")
        void leavesAiOffByDefault(@TempDir Path projectRoot) {
            run("--root", projectRoot.toString());

            assertThat(runner.options().aiEnabled()).isFalse();
        }
    }

    /** Captures the options instead of running an analysis. */
    private static final class RecordingRunner extends CodemapRunner {

        private CodemapOptions captured;

        @Override
        public int run(CodemapOptions options) {
            this.captured = options;
            return ExitCode.SUCCESS;
        }

        CodemapOptions options() {
            assertThat(captured).as("runner was not invoked").isNotNull();
            return captured;
        }

        boolean wasInvoked() {
            return captured != null;
        }
    }
}
