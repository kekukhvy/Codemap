package dev.codemap.core.diff;

import java.util.Objects;

/**
 * A block of lines removed by the diff, kept as a placeholder rather than a
 * method.
 *
 * <p>Spec §5 deliberately recovers {@code removed} status from the diff alone
 * rather than by parsing the base revision — parsing the base tree would
 * double indexing cost for a rarely used status. The consequence is that a
 * removed method has no body, no id derived from a symbol, and only the old
 * file and line range to show.
 *
 * @param file old-revision path the removed lines belonged to
 * @param range old-file line range that was deleted
 */
public record RemovedMethodRange(String file, LineRange range) {

    public RemovedMethodRange {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(range, "range");
    }
}
