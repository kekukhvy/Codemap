package dev.codemap.core.model;

/**
 * A method or class's relationship to the diff being highlighted (spec §5).
 *
 * <p>This is an overlay on the static map, never the organising principle — a
 * class or method with no status computed for it (git absent, no base
 * resolved) is simply {@code null} in the index rather than forced into
 * {@link #UNCHANGED}, which would claim knowledge the run does not have.
 */
public enum ChangeStatus {

    /** Exists now; every one of its lines is new in the diff. */
    ADDED,

    /** Some of its lines fall inside a diff hunk, but it already existed. */
    CHANGED,

    /** Present in the base revision, absent now. Recovered from the diff alone. */
    REMOVED,

    /** Unchanged itself, but one call hop from a changed method, either direction. */
    AFFECTED,

    /** Everything else. */
    UNCHANGED
}
