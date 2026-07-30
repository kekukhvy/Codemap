package dev.codemap.cli;

import dev.codemap.core.diff.GitCommandResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
    private static final long TIMEOUT_SECONDS = 30;

    /** How the resolver reaches {@code gh}; substitutable so tests need no binary. */
    @FunctionalInterface
    public interface ToolRunner {
        GitCommandResult run(String binary, Path workingDirectory, String... arguments);
    }

    private final ToolRunner toolRunner;

    public PullRequestBase() {
        this(PullRequestBase::runProcess);
    }

    public PullRequestBase(ToolRunner toolRunner) {
        this.toolRunner = toolRunner;
    }

    /**
     * Runs one short-lived command and collects its output.
     *
     * <p>Deliberately not folded into {@code GitCommandRunner}: that class is
     * named for git and used throughout the analysis, and widening it to
     * arbitrary binaries for this one caller would leave a general capability
     * with a single user. Asking {@code gh} one question is small enough to own
     * here.
     */
    private static GitCommandResult runProcess(String binary, Path workingDirectory, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(binary);
        command.addAll(List.of(arguments));
        try {
            // No shell: arguments are passed as a vector, so nothing in them is
            // interpreted as syntax.
            Process process = new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new GitCommandResult(false, "", binary + " timed out");
            }
            return new GitCommandResult(process.exitValue() == 0, stdout.strip(), stderr.strip());
        } catch (IOException e) {
            return new GitCommandResult(false, "", binary + " is not on PATH");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new GitCommandResult(false, "", binary + " was interrupted");
        }
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
