package dev.codemap.core.model;

import java.util.Objects;

/**
 * A root of the map: a place external behaviour enters the application (spec §4).
 *
 * <p>Entry points, not packages, organise the map — a reader asks "what can this
 * application do", and the answer is the set of these, hung under the module that
 * declares them.
 *
 * @param id stable identifier, unique across the index
 * @param moduleId module this entry point belongs to
 * @param kind what sort of entry point this is
 * @param label human-readable summary, e.g. {@code "POST /api/v1/tasks"}
 * @param methodId the method that implements this entry point, matching
 *        {@link IndexedMethod#id()} so the two join in the index
 * @param detectedBy how this entry point was found
 * @param source where in the source it was recognised
 */
public record EntryPoint(
        String id,
        String moduleId,
        EntryPointKind kind,
        String label,
        String methodId,
        DetectedBy detectedBy,
        SourceLocation source) {

    public EntryPoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(methodId, "methodId");
        Objects.requireNonNull(detectedBy, "detectedBy");
        Objects.requireNonNull(source, "source");
    }
}
