package dev.codemap.core.diff;

import dev.codemap.core.ComparisonMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GitChangeSource} resolves the right diff for a run's {@link ComparisonMode}
 * (spec §5.1): BRANCH uses the merge base with the default branch (three-dot,
 * plus uncommitted work), REVISION diffs directly against the named commit
 * (two-dot). Every test runs against a hermetic {@code git init} repository so
 * the suite never depends on this project's own history.
 */
class GitChangeSourceTest {

    private final GitChangeSource changeSource = new GitChangeSource(new GitCommandRunner());

    @TempDir
    Path repoRoot;

    @Nested
    @DisplayName("BRANCH mode")
    class BranchMode {

        @Test
        @DisplayName("diffs from the merge base with an explicit --base, not a two-dot comparison")
        void usesMergeBaseWithExplicitBase() throws IOException, InterruptedException {
            initRepo();
            commit("A.java", "class A { void m() {} }", "init on main");
            checkoutBranch("feature");
            writeAndCommit("A.java", "class A { void m() { CHANGED; } }", "change on feature");

            GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty());

            assertThat(outcome.isResolved()).isTrue();
            assertThat(outcome.fileDiffs()).extracting(FileDiff::path).containsExactly("A.java");
        }

        @Test
        @DisplayName("excludes commits landed on the base branch after this one forked")
        void excludesCommitsLandedOnBaseAfterForking() throws IOException, InterruptedException {
            initRepo();
            commit("A.java", "class A {}", "init");
            checkoutBranch("feature");
            writeAndCommit("Feature.java", "class Feature {}", "feature work");
            checkoutBranch("main");
            writeAndCommit("Other.java", "class Other {}", "someone else's later commit on main");
            checkoutBranch("feature");

            GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty());

            assertThat(outcome.isResolved()).isTrue();
            assertThat(outcome.fileDiffs()).extracting(FileDiff::path)
                    .as("Other.java landed on main after the branch forked and must not be attributed to it")
                    .containsExactly("Feature.java");
        }

        @Test
        @DisplayName("includes uncommitted work in the working tree")
        void includesUncommittedWork() throws IOException, InterruptedException {
            initRepo();
            commit("A.java", "class A {}", "init on main");
            checkoutBranch("feature");
            Files.writeString(repoRoot.resolve("Uncommitted.java"), "class Uncommitted {}");
            runner().run(repoRoot, "add", "Uncommitted.java");

            GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty());

            assertThat(outcome.fileDiffs()).extracting(FileDiff::path).contains("Uncommitted.java");
        }

        @Test
        @DisplayName("fails readably when the named base branch does not exist")
        void failsForAnUnresolvableBase() throws IOException, InterruptedException {
            initRepo();
            commit("A.java", "class A {}", "init");

            GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.BRANCH, Optional.of("does-not-exist"), Optional.empty());

            assertThat(outcome.isResolved()).isFalse();
            assertThat(outcome.failureReason()).isNotBlank();
        }

        @Test
        @DisplayName("fails readably, not with an exception, for a repository with no commits")
        void failsForARepositoryWithNoCommits() throws IOException, InterruptedException {
            initRepo();

            GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty());

            assertThat(outcome.isResolved()).isFalse();
            assertThat(outcome.failureReason()).isNotBlank();
        }

        @Test
        @DisplayName("fails readably when no --base is given and no origin/HEAD can be auto-detected")
        void failsWhenAutoDetectionFindsNothing() throws IOException, InterruptedException {
            initRepo();
            commit("A.java", "class A {}", "init");

            GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.BRANCH, Optional.empty(), Optional.empty());

            assertThat(outcome.isResolved()).isFalse();
            assertThat(outcome.failureReason()).isNotBlank();
        }

        @Test
        @DisplayName("auto-detects the base branch from a real origin/HEAD when no --base is given")
        void autoDetectsBaseFromOriginHead(@TempDir Path bareRepo) throws IOException, InterruptedException {
            GitCommandRunner runner = runner();
            runner.run(bareRepo, "init", "-q", "--bare");
            runner.run(repoRoot, "init", "-q", "-b", "main");
            runner.run(repoRoot, "config", "user.email", "test@example.com");
            runner.run(repoRoot, "config", "user.name", "Test");
            writeAndCommit("A.java", "class A {}", "init");
            runner.run(repoRoot, "remote", "add", "origin", bareRepo.toString());
            runner.run(repoRoot, "push", "-q", "origin", "main");
            runner.run(repoRoot, "remote", "set-head", "origin", "main");
            checkoutBranch("feature");
            writeAndCommit("Feature.java", "class Feature {}", "feature work");

            GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.BRANCH, Optional.empty(), Optional.empty());

            assertThat(outcome.isResolved())
                    .as("origin/HEAD should be auto-detected without an explicit --base")
                    .isTrue();
            assertThat(outcome.fileDiffs()).extracting(FileDiff::path).containsExactly("Feature.java");
        }
    }

    @Nested
    @DisplayName("REVISION mode")
    class RevisionMode {

        @Test
        @DisplayName("diffs directly against the named commit with a two-dot comparison")
        void diffsAgainstNamedCommit() throws IOException, InterruptedException {
            initRepo();
            commit("A.java", "class A { void m() {} }", "first");
            writeAndCommit("A.java", "class A { void m() { CHANGED; } }", "second");
            writeAndCommit("B.java", "class B {}", "third");

            GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.REVISION, Optional.empty(), Optional.of("HEAD~2"));

            assertThat(outcome.isResolved()).isTrue();
            assertThat(outcome.fileDiffs()).extracting(FileDiff::path).contains("A.java", "B.java");
        }

        @Test
        @DisplayName("fails readably when the named commit does not exist")
        void failsForAnUnresolvableRevision() throws IOException, InterruptedException {
            initRepo();
            commit("A.java", "class A {}", "init");

            GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.REVISION, Optional.empty(), Optional.of("not-a-revision"));

            assertThat(outcome.isResolved()).isFalse();
            assertThat(outcome.failureReason()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("git absent")
    class GitAbsent {

        @Test
        @DisplayName("fails readably when the directory is not a git repository at all")
        void failsForANonRepository() {
            GitDiffOutcome outcome = changeSource.resolve(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty());

            assertThat(outcome.isResolved()).isFalse();
            assertThat(outcome.failureReason()).isNotBlank();
        }
    }

    private GitCommandRunner runner() {
        return new GitCommandRunner();
    }

    private void initRepo() throws IOException, InterruptedException {
        runner().run(repoRoot, "init", "-q", "-b", "main");
        runner().run(repoRoot, "config", "user.email", "test@example.com");
        runner().run(repoRoot, "config", "user.name", "Test");
    }

    private void commit(String fileName, String content, String message) throws IOException, InterruptedException {
        writeAndCommit(fileName, content, message);
    }

    private void writeAndCommit(String fileName, String content, String message) throws IOException, InterruptedException {
        Files.writeString(repoRoot.resolve(fileName), content);
        runner().run(repoRoot, "add", fileName);
        runner().run(repoRoot, "commit", "-q", "-m", message);
    }

    private void checkoutBranch(String branch) throws IOException, InterruptedException {
        GitCommandResult result = runner().run(repoRoot, "checkout", branch);
        if (!result.succeeded()) {
            runner().run(repoRoot, "checkout", "-b", branch);
        }
    }
}
