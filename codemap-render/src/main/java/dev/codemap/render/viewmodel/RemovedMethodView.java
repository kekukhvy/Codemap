package dev.codemap.render.viewmodel;

import java.util.Objects;

/**
 * A block of lines the diff deleted, rendered as a gap rather than a node with
 * a signature — no declaration survives to name it (spec §5).
 *
 * @param file path the removed lines belonged to, in the base revision
 * @param lineStart first deleted line, 1-based and inclusive
 * @param lineEnd last deleted line, inclusive
 */
public record RemovedMethodView(String file, int lineStart, int lineEnd) {

    public RemovedMethodView {
        Objects.requireNonNull(file, "file");
    }
}
