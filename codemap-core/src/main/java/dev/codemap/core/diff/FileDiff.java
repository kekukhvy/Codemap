package dev.codemap.core.diff;

import java.util.List;
import java.util.Objects;

/**
 * One file's worth of hunks from a {@code git diff --unified=0}, reduced to the
 * line ranges status assignment needs.
 *
 * <p>{@code changedRanges} are new-file line numbers (what a method's current
 * {@code lineStart}/{@code lineEnd} can be compared against); {@code
 * removedRanges} are old-file line numbers, since the lines they name no longer
 * exist in the current tree.
 *
 * @param path current path, relative to the repository root
 * @param oldPath path before a rename, equal to {@code path} when not renamed
 * @param changedRanges new-file line ranges touched by an addition or a
 *        replacement's added side
 * @param removedRanges old-file line ranges touched by a deletion or a
 *        replacement's removed side
 * @param newFile whether this file did not exist in the base revision
 * @param deletedFile whether this file no longer exists in the current tree
 */
public record FileDiff(
        String path,
        String oldPath,
        List<LineRange> changedRanges,
        List<LineRange> removedRanges,
        boolean newFile,
        boolean deletedFile) {

    public FileDiff {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(oldPath, "oldPath");
        changedRanges = List.copyOf(Objects.requireNonNull(changedRanges, "changedRanges"));
        removedRanges = List.copyOf(Objects.requireNonNull(removedRanges, "removedRanges"));
    }

    /** Whether this file did not exist in the base revision. */
    public boolean isNewFile() {
        return newFile;
    }

    /** Whether this file no longer exists in the current tree. */
    public boolean isDeletedFile() {
        return deletedFile;
    }

    /** Whether the current path differs from the base-revision path. */
    public boolean isRenamed() {
        return !path.equals(oldPath);
    }
}
