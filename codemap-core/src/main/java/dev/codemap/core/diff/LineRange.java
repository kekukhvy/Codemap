package dev.codemap.core.diff;

/**
 * An inclusive, 1-based span of source lines.
 *
 * <p>The shared unit hunk parsing and method-status assignment both work in: a
 * diff hunk is one range, a method declaration is another, and status
 * assignment is entirely a question of how they overlap.
 *
 * @param startLine first line, inclusive
 * @param endLine last line, inclusive
 */
public record LineRange(int startLine, int endLine) {

    /** Whether this range shares at least one line with {@code other}. */
    public boolean overlaps(LineRange other) {
        return startLine <= other.endLine && other.startLine <= endLine;
    }

    /** Whether every line of this range falls within {@code other}. */
    public boolean isContainedIn(LineRange other) {
        return startLine >= other.startLine && endLine <= other.endLine;
    }

    /** Number of lines the range spans, inclusive of both ends. */
    public int lineCount() {
        return endLine - startLine + 1;
    }
}
