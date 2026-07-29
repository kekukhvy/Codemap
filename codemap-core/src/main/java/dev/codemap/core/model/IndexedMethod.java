package dev.codemap.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

/**
 * One method or constructor.
 *
 * <p>The {@code source} field carries the method's real text. It is stored rather
 * than read on demand because the report runs over {@code file://}, where the
 * browser cannot open local files — so the code a reader clicks through to has to
 * travel inside the index.
 *
 * @param id stable identifier, unique across the index
 * @param classId owning class
 * @param name method name; a constructor carries its type's simple name
 * @param signature full signature including parameter types
 * @param file source file, relative to the project root
 * @param lineStart first line of the declaration, 1-based and inclusive
 * @param lineEnd last line of the declaration, inclusive
 * @param javadoc first sentence of the Javadoc, or {@code null} when absent
 * @param source the method's source text, as sliced from the file
 * @param constructor whether this is a constructor rather than a method
 */
public record IndexedMethod(
        String id,
        String classId,
        String name,
        String signature,
        String file,
        int lineStart,
        int lineEnd,
        String javadoc,
        String source,
        boolean constructor) {

    public IndexedMethod {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(classId, "classId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(file, "file");
    }

    /** Number of lines the declaration spans, including signature and braces. */
    @JsonIgnore
    public int lineCount() {
        return lineEnd - lineStart + 1;
    }
}
