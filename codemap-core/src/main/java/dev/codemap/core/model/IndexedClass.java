package dev.codemap.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

/**
 * One declared type: class, interface, enum, record, or annotation.
 *
 * <p>Nested types are indexed as their own entries rather than folded into the
 * enclosing type, since a nested class can hold methods worth navigating to. Their
 * {@code fqn} uses a dot separator ({@code Outer.Inner}), matching how a reader
 * writes the name rather than the binary {@code Outer$Inner} form.
 *
 * @param id stable identifier, unique across the index
 * @param moduleId owning module
 * @param fqn fully-qualified name
 * @param simpleName name without the package
 * @param packageName package, empty for the default package
 * @param kind what sort of type this is
 * @param layer architectural layer inferred from the package
 * @param file source file, relative to the project root
 * @param lineStart first line of the declaration, 1-based and inclusive
 * @param lineEnd last line of the declaration, inclusive
 * @param javadoc first sentence of the Javadoc, or {@code null} when absent
 * @param status change status derived from its methods (spec §5), or
 *        {@code null} when no diff was computed for this run
 */
public record IndexedClass(
        String id,
        String moduleId,
        String fqn,
        String simpleName,
        String packageName,
        TypeKind kind,
        Layer layer,
        String file,
        int lineStart,
        int lineEnd,
        String javadoc,
        ChangeStatus status) {

    public IndexedClass {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(fqn, "fqn");
        Objects.requireNonNull(simpleName, "simpleName");
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(file, "file");
    }

    /**
     * Convenience constructor for callers that do not yet know a status —
     * parsing, and every existing test written before status existed.
     */
    public IndexedClass(
            String id,
            String moduleId,
            String fqn,
            String simpleName,
            String packageName,
            TypeKind kind,
            Layer layer,
            String file,
            int lineStart,
            int lineEnd,
            String javadoc) {
        this(id, moduleId, fqn, simpleName, packageName, kind, layer, file, lineStart, lineEnd, javadoc, null);
    }

    /** Whether this type is declared inside another. */
    @JsonIgnore
    public boolean isNested() {
        return simpleName.indexOf('.') >= 0 || fqn.lastIndexOf('.') > packageName.length();
    }

    /** Returns a copy carrying the given status, aggregated by the diff stage. */
    public IndexedClass withStatus(ChangeStatus value) {
        return new IndexedClass(id, moduleId, fqn, simpleName, packageName, kind, layer, file, lineStart, lineEnd, javadoc, value);
    }
}
