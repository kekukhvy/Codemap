package dev.codemap.render.viewmodel;

import dev.codemap.core.model.ChangeStatus;
import dev.codemap.core.model.Layer;
import dev.codemap.core.model.TypeKind;

import java.util.Objects;

/**
 * One declared type, projected for the report: layer and status drive badges
 * and outlines, never computed on the client from raw package strings.
 *
 * @param id stable identifier
 * @param moduleId owning module
 * @param fqn fully-qualified name
 * @param simpleName name without the package
 * @param packageName package, empty for the default package
 * @param kind what sort of type this is
 * @param layer architectural layer, drives the layer badge and filter
 * @param file source file, relative to the project root
 * @param lineStart first line of the declaration, 1-based and inclusive
 * @param lineEnd last line of the declaration, inclusive
 * @param javadoc first sentence of the Javadoc, or {@code null} when absent
 * @param status change status (spec §5), or {@code null} when no diff was computed
 */
public record ClassView(
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

    public ClassView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(fqn, "fqn");
        Objects.requireNonNull(simpleName, "simpleName");
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(file, "file");
    }
}
