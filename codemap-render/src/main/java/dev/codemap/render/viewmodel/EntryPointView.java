package dev.codemap.render.viewmodel;

import dev.codemap.core.model.DetectedBy;
import dev.codemap.core.model.EntryPointKind;

import java.util.Objects;

/**
 * A root of the map: a place external behaviour enters the application
 * (spec §4), hanging beneath its {@link ModuleView}.
 *
 * @param id stable identifier
 * @param moduleId owning module
 * @param kind what sort of entry point this is
 * @param label human-readable summary, e.g. {@code "POST /api/v1/tasks"}
 * @param methodId the method implementing this entry point
 * @param detectedBy how this entry point was found, surfaced in the UI
 */
public record EntryPointView(
        String id,
        String moduleId,
        EntryPointKind kind,
        String label,
        String methodId,
        DetectedBy detectedBy) {

    public EntryPointView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(methodId, "methodId");
        Objects.requireNonNull(detectedBy, "detectedBy");
    }
}
