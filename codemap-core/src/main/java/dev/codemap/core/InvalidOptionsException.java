package dev.codemap.core;

/**
 * Thrown when the inputs for a run cannot be acted on at all — a missing project
 * root, or contradictory revisions.
 *
 * <p>This is deliberately narrow. Codemap's rule is to degrade rather than fail:
 * a malformed source file, an unresolvable symbol, or a stale cache each produce
 * a warning and a usable result. This exception is for the cases where there is
 * nothing to produce, so the message is written for the user rather than for a
 * stack trace.
 */
public class InvalidOptionsException extends RuntimeException {

    public InvalidOptionsException(String message) {
        super(message);
    }
}
