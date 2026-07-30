package dev.codemap.core.diff;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves a pull-request number to the branch that pull request targets.
 *
 * <p>Comparing against the wrong base is the difference between a map that
 * highlights a handful of real changes and one that paints almost everything
 * green: a project whose pull requests target {@code develop}, compared against
 * {@code main}, reports every file as new. The number is the one thing a reader
 * always knows, so it is a better question to ask them than "which branch is
 * this pull request based on".
 *
 * <p>Asks the {@code gh} CLI, which already holds the reader's credentials.
 * Every failure — no {@code gh}, not logged in, no network, unknown number —
 * degrades to {@link Optional#empty()} so the caller can fall back to the
 * ordinary base-branch behaviour instead of failing the run.
 */
public final class PullRequestBase {

    private static final Logger log = LoggerFactory.getLogger(PullRequestBase.class);
    private static final String GH_BINARY = "gh";

    /** How the resolver reaches a command-line tool; substitutable in tests. */
    @FunctionalInterface
    public interface ToolRunner {
        GitCommandResult run(String binary, Path workingDirectory, String... arguments);
    }

    private final ToolRunner toolRunner;

    public PullRequestBase(GitCommandRunner commandRunner) {
        this(commandRunner::runTool);
    }

    public PullRequestBase(ToolRunner toolRunner) {
        this.toolRunner = toolRunner;
    }

    /**
     * Looks up the base branch of one pull request.
     *
     * @param projectRoot repository to ask about
     * @param pullRequestNumber the pull request's number
     * @return the branch it targets, or empty when it cannot be determined
     */
    public Optional<String> resolve(Path projectRoot, int pullRequestNumber) {
        GitCommandResult result = toolRunner.run(GH_BINARY, projectRoot,
                "pr", "view", String.valueOf(pullRequestNumber), "--json", "baseRefName", "--jq", ".baseRefName");

        if (!result.succeeded()) {
            log.warn("Could not resolve the base branch of PR #{}: {}", pullRequestNumber, result.stderr());
            return Optional.empty();
        }
        String branch = result.stdout().strip();
        if (branch.isEmpty()) {
            log.warn("PR #{} reported no base branch", pullRequestNumber);
            return Optional.empty();
        }
        log.debug("PR #{} targets {}", pullRequestNumber, branch);
        return Optional.of(branch);
    }
}
