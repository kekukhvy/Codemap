package dev.codemap.core.diff;

import dev.codemap.core.model.CodeIndex;

import java.util.Objects;

/**
 * Outcome of {@link GitChangeAnalyzer#analyze}: either the given index with
 * status attached to every class and method, or the same index untouched
 * alongside a reason no diff could be resolved.
 *
 * <p>The index is always present, resolved or not, so a caller never has to
 * special-case "no diff" to keep rendering the static map — change status is
 * an overlay, never the organising principle (spec §5).
 */
public final class ChangeAnalysisResult {

    private final CodeIndex index;
    private final boolean resolved;
    private final String failureReason;

    private ChangeAnalysisResult(CodeIndex index, boolean resolved, String failureReason) {
        this.index = index;
        this.resolved = resolved;
        this.failureReason = failureReason;
    }

    /** A successful analysis: {@code index} carries status on its classes and methods. */
    public static ChangeAnalysisResult resolved(CodeIndex index) {
        return new ChangeAnalysisResult(Objects.requireNonNull(index, "index"), true, null);
    }

    /** A degraded outcome: {@code index} is returned as given, with no status attached. */
    public static ChangeAnalysisResult unresolved(CodeIndex index, String reason) {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(reason, "reason");
        return new ChangeAnalysisResult(index, false, reason);
    }

    /** Whether change status was computed. */
    public boolean isResolved() {
        return resolved;
    }

    /** The index, with status attached when {@link #isResolved()}. */
    public CodeIndex index() {
        return index;
    }

    /** A message safe to show the user, present only when not resolved. */
    public String failureReason() {
        return failureReason;
    }
}
