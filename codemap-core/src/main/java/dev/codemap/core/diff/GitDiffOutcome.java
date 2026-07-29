package dev.codemap.core.diff;

import java.util.List;
import java.util.Objects;

/**
 * Result of resolving the diff for one run: either the per-file changes to
 * assign statuses from, or a reason none could be produced.
 *
 * <p>A failure here is never a crashed run (spec §5, and the project's
 * degrade-don't-fail rule): a repository with no commits, an unresolvable
 * base, or a missing {@code git} binary all land here with a message meant to
 * be shown to the user, and the index is produced with no statuses.
 */
public final class GitDiffOutcome {

    private final boolean resolved;
    private final List<FileDiff> fileDiffs;
    private final String mergeBase;
    private final String base;
    private final String failureReason;

    private GitDiffOutcome(boolean resolved, List<FileDiff> fileDiffs, String mergeBase, String base, String failureReason) {
        this.resolved = resolved;
        this.fileDiffs = List.copyOf(fileDiffs);
        this.mergeBase = mergeBase;
        this.base = base;
        this.failureReason = failureReason;
    }

    /** A successful resolution, carrying the file diffs and the revision compared from. */
    public static GitDiffOutcome resolved(List<FileDiff> fileDiffs, String mergeBase) {
        return resolved(fileDiffs, mergeBase, null);
    }

    /**
     * A resolved diff that also names the branch it was taken against.
     *
     * @param base branch compared against, or {@code null} for a revision comparison
     */
    public static GitDiffOutcome resolved(List<FileDiff> fileDiffs, String mergeBase, String base) {
        Objects.requireNonNull(fileDiffs, "fileDiffs");
        Objects.requireNonNull(mergeBase, "mergeBase");
        return new GitDiffOutcome(true, fileDiffs, mergeBase, base, null);
    }

    /** A degraded outcome: no statuses can be computed, for the given reason. */
    public static GitDiffOutcome unresolved(String reason) {
        Objects.requireNonNull(reason, "reason");
        return new GitDiffOutcome(false, List.of(), null, null, reason);
    }

    /** Whether a diff was successfully computed. */
    public boolean isResolved() {
        return resolved;
    }

    /** The per-file changes, empty when {@link #isResolved()} is {@code false}. */
    public List<FileDiff> fileDiffs() {
        return fileDiffs;
    }

    /** The revision changes were measured from, present only when resolved. */
    /** Branch this comparison was taken against, or {@code null} for a revision comparison. */
    public String base() {
        return base;
    }

    public String mergeBase() {
        return mergeBase;
    }

    /** A message safe to show the user, present only when not resolved. */
    public String failureReason() {
        return failureReason;
    }
}
