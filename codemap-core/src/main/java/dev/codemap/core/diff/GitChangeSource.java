package dev.codemap.core.diff;

import dev.codemap.core.ComparisonMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the diff a run measures changes against (spec §5.1).
 *
 * <p>{@link ComparisonMode#BRANCH} diffs from the merge base with the base
 * branch — {@code git diff <base>...HEAD}, three dots, plus uncommitted work —
 * so commits landed on the base branch after this one forked are never
 * attributed to it. {@link ComparisonMode#REVISION} is a direct two-dot
 * comparison against the named commit.
 *
 * <p>Never throws: a repository with no commits, an unresolvable base, or a
 * missing {@code git} binary all degrade to an unresolved {@link GitDiffOutcome}
 * carrying a message meant for the user, per the project's degrade-don't-fail
 * rule.
 */
public final class GitChangeSource {

    private static final Logger log = LoggerFactory.getLogger(GitChangeSource.class);

    private static final String UNIFIED_ZERO = "--unified=0";

    /**
     * {@code symbolic-ref} needs the full ref path — the {@code origin/HEAD}
     * shorthand that resolves everywhere else in git is rejected here with
     * "not a symbolic ref", even though the ref exists and is exactly that.
     */
    private static final String ORIGIN_HEAD_REF = "refs/remotes/origin/HEAD";
    private static final String MERGE_BASE_FAILED =
            "Could not resolve a merge base with '%s'. Pass --base <branch> to name one explicitly.";
    private static final String NO_BASE_AVAILABLE =
            "Could not auto-detect the repository's default branch (no origin/HEAD). Pass --base <branch>.";
    private static final String REVISION_FAILED =
            "Could not resolve revision '%s'. %s";
    private static final String NOT_A_REPOSITORY =
            "Not a git repository, or git is unavailable: %s";

    private final GitCommandRunner gitCommandRunner;
    private final UnifiedDiffParser diffParser;

    public GitChangeSource(GitCommandRunner gitCommandRunner) {
        this.gitCommandRunner = gitCommandRunner;
        this.diffParser = new UnifiedDiffParser();
    }

    /**
     * Resolves the diff for one run.
     *
     * @param repoRoot the project root, expected to be inside a git repository
     * @param mode which comparison the run requested
     * @param base explicit {@code --base} branch, empty to auto-detect (BRANCH only)
     * @param since explicit {@code --since} commit (REVISION only)
     * @return the resolved diff, or a readable reason it could not be produced
     */
    public GitDiffOutcome resolve(Path repoRoot, ComparisonMode mode, Optional<String> base, Optional<String> since) {
        if (!isGitRepository(repoRoot)) {
            return GitDiffOutcome.unresolved(NOT_A_REPOSITORY.formatted(repoRoot));
        }
        return switch (mode) {
            case BRANCH -> resolveBranch(repoRoot, base);
            case REVISION -> resolveRevision(repoRoot, since.orElseThrow());
        };
    }

    private boolean isGitRepository(Path repoRoot) {
        return gitCommandRunner.run(repoRoot, "rev-parse", "--git-dir").succeeded();
    }

    private GitDiffOutcome resolveBranch(Path repoRoot, Optional<String> base) {
        Optional<String> resolvedBase = base.or(() -> autoDetectBaseBranch(repoRoot));
        if (resolvedBase.isEmpty()) {
            return GitDiffOutcome.unresolved(NO_BASE_AVAILABLE);
        }

        Optional<String> mergeBase = findMergeBase(repoRoot, resolvedBase.get());
        if (mergeBase.isEmpty()) {
            return GitDiffOutcome.unresolved(MERGE_BASE_FAILED.formatted(resolvedBase.get()));
        }

        return runDiff(repoRoot, mergeBase.get(), resolvedBase.get());
    }

    private GitDiffOutcome resolveRevision(Path repoRoot, String since) {
        GitCommandResult verify = gitCommandRunner.run(repoRoot, "rev-parse", "--verify", since + "^{commit}");
        if (!verify.succeeded()) {
            return GitDiffOutcome.unresolved(REVISION_FAILED.formatted(since, verify.stderr()));
        }
        return runDiff(repoRoot, since, null);
    }

    /**
     * Runs the diff from {@code baseRevision} to the working tree, including
     * uncommitted work.
     *
     * @param baseRevision commit to diff from — the merge base in branch mode
     * @param base branch that merge base came from, or {@code null} when diffing
     *        against an explicit revision
     */
    private GitDiffOutcome runDiff(Path repoRoot, String baseRevision, String base) {
        GitCommandResult diff = gitCommandRunner.run(repoRoot, "diff", UNIFIED_ZERO, baseRevision);
        if (!diff.succeeded()) {
            log.warn("git diff against {} failed: {}", baseRevision, diff.stderr());
            return GitDiffOutcome.unresolved(REVISION_FAILED.formatted(baseRevision, diff.stderr()));
        }
        return GitDiffOutcome.resolved(diffParser.parse(diff.stdout()), baseRevision, base);
    }

    /**
     * The point this branch diverged from {@code base}. Diffing from this commit
     * to the working tree is equivalent to {@code base...HEAD} plus uncommitted
     * work, without git's own {@code A...B} diff syntax silently including
     * uncommitted changes to files outside the branch too.
     */
    private Optional<String> findMergeBase(Path repoRoot, String base) {
        // "--" separates options from revisions: the base can come from `gh`
        // (a branch name chosen by whoever opened the pull request), and git
        // would otherwise read a leading-dash name as an option.
        GitCommandResult result = gitCommandRunner.run(repoRoot, "merge-base", "--", base, "HEAD");
        return result.succeeded() ? Optional.of(result.stdout()) : Optional.empty();
    }

    private Optional<String> autoDetectBaseBranch(Path repoRoot) {
        GitCommandResult result = gitCommandRunner.run(repoRoot, "symbolic-ref", "--short", ORIGIN_HEAD_REF);
        if (!result.succeeded() || result.stdout().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(result.stdout());
    }
}
