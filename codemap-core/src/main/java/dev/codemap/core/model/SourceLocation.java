package dev.codemap.core.model;

import java.util.Objects;

/**
 * Where in the source an {@link EntryPoint} was recognised — the annotation site,
 * or the route-registration call.
 *
 * @param file source file, relative to the project root
 * @param line 1-based line of the detected declaration or call
 */
public record SourceLocation(String file, int line) {

    public SourceLocation {
        Objects.requireNonNull(file, "file");
    }
}
