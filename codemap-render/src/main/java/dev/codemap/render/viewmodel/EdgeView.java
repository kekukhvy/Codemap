package dev.codemap.render.viewmodel;

import dev.codemap.core.model.EdgeKind;

import java.util.Objects;

/**
 * One call, type-use, or implementation edge (spec §3.3), carrying enough to
 * decide its rendering client-side: dashed for same-class calls, solid for
 * cross-class, heavy and collapsed for cross-module.
 *
 * @param from source method or class id
 * @param to target method or class id, or a bare name when unresolved
 * @param kind what relationship this edge records
 * @param resolved whether the symbol solver confirmed the target
 * @param line call-site line, 1-based; 0 for type references
 * @param fromModuleId source module id, set only for {@code CROSS_MODULE} edges
 * @param toModuleId target module id, set only for {@code CROSS_MODULE} edges
 */
public record EdgeView(
        String from,
        String to,
        EdgeKind kind,
        boolean resolved,
        int line,
        String fromModuleId,
        String toModuleId) {

    public EdgeView {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(kind, "kind");
    }
}
