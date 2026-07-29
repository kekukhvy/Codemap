package dev.codemap.core.model;

import java.util.Objects;

/**
 * One edge of the call graph: a call, type reference, or implementation
 * relationship between two methods or types.
 *
 * <p>{@code resolved} tells a reader whether {@code to} was confirmed by the
 * symbol solver or is a best-effort name match — symbol resolution failures are
 * expected in real projects, and the graph must stay honest about which edges
 * are certain.
 *
 * @param from source method id
 * @param to target method id
 * @param kind what relationship this edge records
 * @param resolved whether the symbol solver resolved the target, or this edge
 *        degraded to a name-based match
 * @param line call-site line in the source file, 1-based
 * @param fromModuleId source module id, set only for {@link EdgeKind#CROSS_MODULE}
 * @param toModuleId target module id, set only for {@link EdgeKind#CROSS_MODULE}
 */
public record CallEdge(
        String from,
        String to,
        EdgeKind kind,
        boolean resolved,
        int line,
        String fromModuleId,
        String toModuleId) {

    public CallEdge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(kind, "kind");
    }
}
