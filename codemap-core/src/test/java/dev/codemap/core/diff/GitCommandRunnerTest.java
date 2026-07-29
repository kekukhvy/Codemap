package dev.codemap.core.diff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GitCommandRunner} shells out to the {@code git} binary. Tests run
 * against a hermetic {@code git init} repository in a temp directory — never
 * against this working tree, which changes as the suite runs elsewhere.
 */
class GitCommandRunnerTest {

    private final GitCommandRunner runner = new GitCommandRunner();

    @TempDir
    Path repoRoot;

    @Nested
    @DisplayName("successful commands")
    class SuccessfulCommands {

        @Test
        @DisplayName("captures stdout from a command that succeeds")
        void capturesStdout() throws IOException, InterruptedException {
            initRepo();
            commit("A.java", "class A {}", "init");

            GitCommandResult result = runner.run(repoRoot, "log", "--oneline");

            assertThat(result.succeeded()).isTrue();
            assertThat(result.stdout()).contains("init");
        }
    }

    @Nested
    @DisplayName("failing commands")
    class FailingCommands {

        @Test
        @DisplayName("reports failure with git's own message rather than throwing")
        void reportsFailureForAnUnknownRevision() throws IOException, InterruptedException {
            initRepo();
            commit("A.java", "class A {}", "init");

            GitCommandResult result = runner.run(repoRoot, "rev-parse", "--verify", "does-not-exist");

            assertThat(result.succeeded()).isFalse();
            assertThat(result.stderr()).isNotBlank();
        }

        @Test
        @DisplayName("reports failure, not an exception, when the directory is not a git repository")
        void reportsFailureForANonRepository() throws IOException, InterruptedException {
            GitCommandResult result = runner.run(repoRoot, "log");

            assertThat(result.succeeded()).isFalse();
        }
    }

    private void initRepo() throws IOException, InterruptedException {
        runner.run(repoRoot, "init", "-q");
        runner.run(repoRoot, "config", "user.email", "test@example.com");
        runner.run(repoRoot, "config", "user.name", "Test");
    }

    private void commit(String fileName, String content, String message) throws IOException, InterruptedException {
        Files.writeString(repoRoot.resolve(fileName), content);
        runner.run(repoRoot, "add", fileName);
        runner.run(repoRoot, "commit", "-q", "-m", message);
    }
}
