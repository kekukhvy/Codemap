package dev.codemap.core.model;

import java.util.Objects;

/**
 * A block of lines the diff deleted, recovered as a {@code removed} node
 * without parsing the base revision (spec §5).
 *
 * <p>Unlike {@link IndexedMethod}, this carries no id, name, signature, or
 * source — none of that survives without reading the base tree, which the
 * spec deliberately avoids to keep indexing cost bounded. The consequence,
 * called out in the spec, is that a removed node has no body to display.
 *
 * @param file path the removed lines belonged to, in the base revision
 * @param lineStart first deleted line, 1-based and inclusive
 * @param lineEnd last deleted line, inclusive
 */
public record RemovedMethod(String file, int lineStart, int lineEnd) {

    public RemovedMethod {
        Objects.requireNonNull(file, "file");
    }
}
