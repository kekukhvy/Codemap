package dev.codemap.core.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The project-internal call graph: every {@link CallEdge} produced from resolving
 * method bodies, signatures, and interface implementations.
 *
 * <p>Callers as well as callees must be O(1) lookups — the side panel's "Called
 * by" cannot afford to scan every edge — so a reverse index is built once here
 * rather than computed on demand.
 */
public final class CallGraph {

    private final List<CallEdge> edges;
    private final Map<String, List<CallEdge>> outgoingByFrom;
    private final Map<String, List<CallEdge>> incomingByTo;
    private final Set<ModuleDependency> moduleDependencies;

    public CallGraph(List<CallEdge> edges) {
        this.edges = List.copyOf(edges);
        this.outgoingByFrom = this.edges.stream().collect(Collectors.groupingBy(CallEdge::from));
        this.incomingByTo = this.edges.stream().collect(Collectors.groupingBy(CallEdge::to));
        this.moduleDependencies = aggregateModuleDependencies(this.edges);
    }

    /**
     * Rolls every {@link EdgeKind#CROSS_MODULE} edge up into the set of module
     * pairs it connects — the "which module depends on which" overview (spec
     * §3.2.2), independent of how many individual calls cross the boundary.
     */
    private static Set<ModuleDependency> aggregateModuleDependencies(List<CallEdge> edges) {
        Set<ModuleDependency> dependencies = new LinkedHashSet<>();
        for (CallEdge edge : edges) {
            if (edge.kind() == EdgeKind.CROSS_MODULE) {
                dependencies.add(new ModuleDependency(edge.fromModuleId(), edge.toModuleId()));
            }
        }
        return Set.copyOf(dependencies);
    }

    /** Every edge in the graph, in build order. */
    public List<CallEdge> edges() {
        return edges;
    }

    /** Edges leaving a method: what it calls, uses, or implements. */
    public List<CallEdge> outgoingFrom(String methodOrClassId) {
        return outgoingByFrom.getOrDefault(methodOrClassId, List.of());
    }

    /**
     * Edges arriving at a method: its callers. This is the reverse index the
     * "Called by" panel needs, resolved in O(1) rather than scanning every edge.
     */
    public List<CallEdge> incomingTo(String methodOrClassId) {
        return incomingByTo.getOrDefault(methodOrClassId, List.of());
    }

    /** Module-to-module dependencies aggregated from every {@code CROSS_MODULE} edge. */
    public Set<ModuleDependency> moduleDependencies() {
        return moduleDependencies;
    }
}
