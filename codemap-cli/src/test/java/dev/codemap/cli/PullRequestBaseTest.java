package dev.codemap.cli;

import dev.codemap.core.diff.GitCommandResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PullRequestBase} turns a pull-request number into the branch it
 * targets, so a reader does not have to know which branch that is.
 *
 * <p>Comparing against the wrong base is the difference between a map showing a
 * handful of real changes and one painted almost entirely green.
 */
class PullRequestBaseTest {

    @TempDir
    Path projectRoot;

    /** Records what was asked and answers with a canned result. */
    private static final class RecordingRunner implements PullRequestBase.ToolRunner {

        private final GitCommandResult result;
        private String binary;
        private List<String> arguments;

        private RecordingRunner(GitCommandResult result) {
            this.result = result;
        }

        @Override
        public GitCommandResult run(String binary, Path workingDirectory, String... arguments) {
            this.binary = binary;
            this.arguments = List.of(arguments);
            return result;
        }
    }

    @Nested
    @DisplayName("resolving a base branch")
    class Resolving {

        @Test
        @DisplayName("returns the branch the pull request targets")
        void returnsTheBaseBranch() {
            RecordingRunner runner = new RecordingRunner(new GitCommandResult(true, "develop", ""));

            Optional<String> base = new PullRequestBase(runner).resolve(projectRoot, 48);

            assertThat(base).contains("develop");
        }

        @Test
        @DisplayName("asks gh for exactly that pull request")
        void asksGhForThatPullRequest() {
            RecordingRunner runner = new RecordingRunner(new GitCommandResult(true, "main", ""));

            new PullRequestBase(runner).resolve(projectRoot, 16);

            assertThat(runner.binary).isEqualTo("gh");
            assertThat(runner.arguments).containsSubsequence("pr", "view", "16");
        }

        @Test
        @DisplayName("trims the surrounding whitespace gh leaves behind")
        void trimsWhitespace() {
            RecordingRunner runner = new RecordingRunner(new GitCommandResult(true, "  develop\n", ""));

            assertThat(new PullRequestBase(runner).resolve(projectRoot, 1)).contains("develop");
        }
    }

    @Nested
    @DisplayName("degrading")
    class Degrading {

        @Test
        @DisplayName("gives up quietly when gh is not installed")
        void ghMissing() {
            RecordingRunner runner = new RecordingRunner(
                    new GitCommandResult(false, "", "gh executable not found on PATH"));

            assertThat(new PullRequestBase(runner).resolve(projectRoot, 48)).isEmpty();
        }

        @Test
        @DisplayName("gives up quietly when the pull request is unknown")
        void unknownPullRequest() {
            RecordingRunner runner = new RecordingRunner(
                    new GitCommandResult(false, "", "could not find pull request"));

            assertThat(new PullRequestBase(runner).resolve(projectRoot, 9999)).isEmpty();
        }

        /** A success with nothing in it is still nothing to compare against. */
        @Test
        @DisplayName("gives up quietly when gh reports no branch")
        void emptyAnswer() {
            RecordingRunner runner = new RecordingRunner(new GitCommandResult(true, "   ", ""));

            assertThat(new PullRequestBase(runner).resolve(projectRoot, 48)).isEmpty();
        }
    }
}
