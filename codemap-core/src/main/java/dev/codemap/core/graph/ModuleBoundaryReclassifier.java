package dev.codemap.core.graph;

import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.EdgeKind;

import java.util.List;
import java.util.Map;

/**
 * Reclassifies edges that cross a module boundary as {@link EdgeKind#CROSS_MODULE}.
 *
 * <p>A single compilation unit only knows its own module, so
 * {@link CallExpressionResolver} can decide same-class versus cross-class but not
 * cross-module — that needs every file's class-to-module mapping known at once,
 * which only exists once the whole project has been visited. Kept as its own
 * collaborator so that mapping-building responsibility does not blur into either
 * the per-file resolver or the top-level builder.
 */
final class ModuleBoundaryReclassifier {

    /** Matches {@code MethodSignatures}' separator between a class id and a method's name/parameters. */
    private static final char ID_SEPARATOR = '#';

    /**
     * Reclassifies {@link EdgeKind#CALL_INTERNAL} and {@link EdgeKind#CALL_EXTERNAL}
     * edges whose endpoints belong to different modules.
     *
     * @param edges edges produced by {@link CallExpressionResolver}, module-unaware
     * @param moduleIdByClassId every indexed class's owning module, by class id
     * @return the same edges, with cross-module ones reclassified and carrying
     *         both module ids
     */
    List<CallEdge> reclassify(List<CallEdge> edges, Map<String, String> moduleIdByClassId) {
        return edges.stream().map(edge -> reclassify(edge, moduleIdByClassId)).toList();
    }

    private CallEdge reclassify(CallEdge edge, Map<String, String> moduleIdByClassId) {
        if (!edge.resolved() || !isReclassifiable(edge.kind())) {
            return edge;
        }
        String fromModuleId = moduleIdByClassId.get(classIdOf(edge.from()));
        String toModuleId = moduleIdByClassId.get(classIdOf(edge.to()));
        if (fromModuleId == null || toModuleId == null || fromModuleId.equals(toModuleId)) {
            return edge;
        }
        return new CallEdge(edge.from(), edge.to(), EdgeKind.CROSS_MODULE, true, edge.line(),
                fromModuleId, toModuleId);
    }

    private boolean isReclassifiable(EdgeKind kind) {
        return kind == EdgeKind.CALL_INTERNAL || kind == EdgeKind.CALL_EXTERNAL;
    }

    private String classIdOf(String methodId) {
        int separator = methodId.indexOf(ID_SEPARATOR);
        return separator < 0 ? methodId : methodId.substring(0, separator);
    }
}
