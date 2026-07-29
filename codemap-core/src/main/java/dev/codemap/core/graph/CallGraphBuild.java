package dev.codemap.core.graph;

import dev.codemap.core.model.CallGraph;
import dev.codemap.core.model.EntryPoint;

import java.util.List;
import java.util.Objects;

/**
 * The result of one {@link CallGraphBuilder#build} pass: the call graph and the
 * entry points detected alongside it.
 *
 * <p>Both come from resolving the same compilation units with the symbol solver
 * attached, so they are produced together rather than as two independent builds.
 *
 * @param callGraph every call, type-use, and implementation edge
 * @param entryPoints every entry point detected by rule
 */
public record CallGraphBuild(CallGraph callGraph, List<EntryPoint> entryPoints) {

    public CallGraphBuild {
        Objects.requireNonNull(callGraph, "callGraph");
        entryPoints = List.copyOf(Objects.requireNonNull(entryPoints, "entryPoints"));
    }
}
