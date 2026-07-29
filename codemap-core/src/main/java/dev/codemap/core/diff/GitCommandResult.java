package dev.codemap.core.diff;

import java.util.Objects;

/**
 * Outcome of one {@code git} invocation.
 *
 * @param succeeded whether the process exited with status zero
 * @param stdout captured standard output, trimmed of a trailing newline
 * @param stderr captured standard error, for a readable failure message
 */
public record GitCommandResult(boolean succeeded, String stdout, String stderr) {

    public GitCommandResult {
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
    }
}
