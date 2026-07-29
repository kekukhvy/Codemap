package dev.codemap.cli;

/**
 * Process exit codes.
 *
 * <p>Named rather than inline so the contract is visible in one place: scripts and
 * CI steps depend on these, so they are part of the tool's public surface.
 */
public final class ExitCode {

    /** The run completed. Degraded results still count as success. */
    public static final int SUCCESS = 0;

    /** The inputs could not be acted on — bad arguments, or a missing project. */
    public static final int INVALID_INPUT = 2;

    /** An unexpected failure that the run could not degrade around. */
    public static final int INTERNAL_ERROR = 70;

    private ExitCode() {
    }
}
