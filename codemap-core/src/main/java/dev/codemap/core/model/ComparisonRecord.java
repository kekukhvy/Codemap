package dev.codemap.core.model;

import dev.codemap.core.ComparisonMode;

import java.util.Objects;

/**
 * What a run's change status was measured against.
 *
 * <p>Stored in the index so a report records how it was produced. Without it a
 * reader cannot tell whether green means "changed on this branch" or "changed
 * since some commit", and two reports of the same project become impossible to
 * compare.
 *
 * @param mode whether changes were measured from the branch point or one revision
 * @param base the branch compared against, resolved from {@code --base} or
 *        auto-detected; {@code null} in {@link ComparisonMode#REVISION} mode
 * @param mergeBase commit where this branch diverged from {@code base}, or the
 *        revision itself in {@link ComparisonMode#REVISION} mode
 */
public record ComparisonRecord(ComparisonMode mode, String base, String mergeBase) {

    public ComparisonRecord {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(mergeBase, "mergeBase");
    }
}
