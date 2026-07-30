package dev.codemap.render.viewmodel;

import dev.codemap.core.model.ChangeStatus;

import java.util.Objects;

/**
 * One method or constructor, carrying its real embedded source text.
 *
 * <p>{@code source} travels inside the report itself rather than being read at
 * view time, since {@code report.html} runs over {@code file://} where the
 * browser cannot open local files (spec §6.6).
 *
 * @param id stable identifier
 * @param classId owning class
 * @param name method name; a constructor carries its type's simple name
 * @param signature full signature including parameter types
 * @param file source file, relative to the project root
 * @param lineStart first line of the declaration, 1-based and inclusive
 * @param lineEnd last line of the declaration, inclusive
 * @param javadoc first sentence of the Javadoc, or {@code null} when absent
 * @param source the method's real source text, sliced from the file
 * @param constructor whether this is a constructor rather than a method
 * @param status change status (spec §5), or {@code null} when no diff was computed
 */
public record MethodView(
        String id,
        String classId,
        String name,
        String signature,
        String file,
        int lineStart,
        int lineEnd,
        String javadoc,
        String source,
        boolean constructor,
        ChangeStatus status) {

    public MethodView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(classId, "classId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(file, "file");
    }
}
