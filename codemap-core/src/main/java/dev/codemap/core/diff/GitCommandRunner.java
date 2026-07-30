package dev.codemap.core.diff;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Runs the {@code git} binary as a subprocess and captures its output.
 *
 * <p>This is the only place in Codemap that shells out. It never fails the run:
 * a missing binary, a non-repository directory, or a rejected command all
 * become a failed {@link GitCommandResult} the caller can degrade on, per the
 * project's rule that a git problem warns rather than crashes.
 */
public final class GitCommandRunner {

    private static final Logger log = LoggerFactory.getLogger(GitCommandRunner.class);

    private static final String GIT_BINARY = "git";
    private static final long TIMEOUT_SECONDS = 30;
    private static final String TIMED_OUT = "command timed out after " + TIMEOUT_SECONDS + "s";
    private static final String INTERRUPTED = "command was interrupted";

    /**
     * Runs one git command in the given working directory.
     *
     * @param workingDirectory directory git is invoked from
     * @param arguments the git subcommand and its arguments, e.g. {@code "log", "--oneline"}
     * @return the captured result; {@link GitCommandResult#succeeded()} is
     *         {@code false} for a missing binary, a non-zero exit, or a timeout
     */
    public GitCommandResult run(Path workingDirectory, String... arguments) {
        return runTool(GIT_BINARY, workingDirectory, arguments);
    }

    /**
     * Runs an arbitrary command-line tool with the same guarantees as git: both
     * streams drained concurrently, a hard timeout, and a missing binary
     * reported as a failed result rather than an exception.
     *
     * @param binary executable to run, resolved on {@code PATH}
     * @param workingDirectory directory to run it in
     * @param arguments arguments passed to the binary
     * @return the outcome, never {@code null}
     */
    public GitCommandResult runTool(String binary, Path workingDirectory, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(binary);
        command.addAll(List.of(arguments));

        ProcessBuilder processBuilder = new ProcessBuilder(command).directory(workingDirectory.toFile());
        try {
            return execute(processBuilder, command);
        } catch (IOException e) {
            log.warn("Could not run {}: {}", binary, e.getMessage());
            return new GitCommandResult(false, "", binary + " executable not found on PATH");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new GitCommandResult(false, "", INTERRUPTED);
        }
    }

    private GitCommandResult execute(ProcessBuilder processBuilder, List<String> command)
            throws IOException, InterruptedException {
        Process process = processBuilder.start();
        // Read both streams concurrently with waiting: a large diff can fill the
        // stdout pipe buffer, and waiting on the process first would deadlock
        // against a child that is itself blocked writing it.
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readFully(process.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readFully(process.getErrorStream()));

        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return new GitCommandResult(false, "", TIMED_OUT);
        }

        boolean succeeded = process.exitValue() == 0;
        log.debug("{} -> exit {}", command, process.exitValue());
        return new GitCommandResult(succeeded, stdout.join().strip(), stderr.join().strip());
    }

    private String readFully(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
