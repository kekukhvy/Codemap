package dev.codemap.core.parse;

import dev.codemap.core.model.IndexedClass;
import dev.codemap.core.model.IndexedMethod;

import java.util.List;
import java.util.Objects;

/**
 * What one source file contributed to the index.
 *
 * <p>A file may declare several top-level or nested types, so this groups them
 * rather than assuming one type per file. An unparseable file yields
 * {@link #skipped(String, String)}, which carries the reason instead of content.
 *
 * @param file path relative to the project root
 * @param classes types declared in the file
 * @param methods methods and constructors declared in those types
 * @param skipReason why the file was skipped, or {@code null} when it parsed
 */
public record ParsedFile(String file, List<IndexedClass> classes, List<IndexedMethod> methods, String skipReason) {

    public ParsedFile {
        Objects.requireNonNull(file, "file");
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
    }

    public static ParsedFile parsed(String file, List<IndexedClass> classes, List<IndexedMethod> methods) {
        return new ParsedFile(file, classes, methods, null);
    }

    /**
     * Records a file that could not be indexed.
     *
     * @param file path relative to the project root
     * @param reason short explanation, shown to the user
     */
    public static ParsedFile skipped(String file, String reason) {
        return new ParsedFile(file, List.of(), List.of(), Objects.requireNonNull(reason, "reason"));
    }

    public boolean wasSkipped() {
        return skipReason != null;
    }
}
