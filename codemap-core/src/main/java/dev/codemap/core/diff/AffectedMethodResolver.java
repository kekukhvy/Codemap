package dev.codemap.core.diff;

import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.CallGraph;
import dev.codemap.core.model.ChangeStatus;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Marks the immediate callers and callees of every changed method
 * {@code affected} (spec §5) — deliberately one hop only. Two hops marks most
 * of a real codebase and stops meaning anything, so this resolver has no
 * recursion to disable by mistake: it visits each changed method's direct
 * edges and stops.
 */
public final class AffectedMethodResolver {

    /**
     * Resolves the one-hop {@code affected} set around every already-statused
     * method.
     *
     * @param statuses status already assigned to each method that has one
     *        ({@code added}, {@code changed}, or {@code removed})
     * @param callGraph the project's call graph, for O(1) caller/callee lookup
     * @return method ids that are exactly one call hop from a statused method,
     *         excluding any method that already has a status of its own
     */
    public Set<String> resolve(Map<String, ChangeStatus> statuses, CallGraph callGraph) {
        Set<String> affected = new LinkedHashSet<>();
        for (String methodId : statuses.keySet()) {
            for (CallEdge edge : callGraph.outgoingFrom(methodId)) {
                addIfUnstatused(edge.to(), affected, statuses);
            }
            for (CallEdge edge : callGraph.incomingTo(methodId)) {
                addIfUnstatused(edge.from(), affected, statuses);
            }
        }
        return affected;
    }

    private void addIfUnstatused(String methodId, Set<String> affected, Map<String, ChangeStatus> statuses) {
        if (!statuses.containsKey(methodId)) {
            affected.add(methodId);
        }
    }
}
