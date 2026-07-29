package dev.codemap.core.graph;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.types.ResolvedType;
import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.EdgeKind;
import dev.codemap.core.parse.MethodOwner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves {@link EdgeKind#USES_TYPE} edges from parameter and return types.
 *
 * <p>A signature is not an invocation (spec §3.3): the edge records that a type
 * appears, so the side panel can show where it is used, but it deliberately does
 * not continue a call chain.
 */
final class SignatureTypeResolver {

    private static final Logger log = LoggerFactory.getLogger(SignatureTypeResolver.class);
    private static final int NO_LINE = 0;

    /**
     * Resolves every parameter and return type referenced by the declarations in a
     * compilation unit.
     *
     * @param unit a compilation unit parsed with a symbol solver attached
     * @param projectClassIds ids of every class indexed in the project; a type
     *        outside this set (JDK, third-party) produces no edge
     * @return one edge per in-project type reference
     */
    List<CallEdge> resolve(CompilationUnit unit, Set<String> projectClassIds) {
        List<CallEdge> edges = new ArrayList<>();
        for (CallableDeclaration<?> callable : unit.findAll(CallableDeclaration.class)) {
            String fromId = MethodOwner.idOf(callable);
            if (fromId == null) {
                continue;
            }
            for (Parameter parameter : callable.getParameters()) {
                addTypeEdge(edges, fromId, parameter.getType(), projectClassIds);
            }
            if (callable instanceof MethodDeclaration method) {
                addTypeEdge(edges, fromId, method.getType(), projectClassIds);
            }
        }
        return edges;
    }

    private void addTypeEdge(List<CallEdge> edges, String fromId, Type type, Set<String> projectClassIds) {
        resolveClassId(type)
                .filter(projectClassIds::contains)
                .ifPresent(classId -> edges.add(
                        new CallEdge(fromId, classId, EdgeKind.USES_TYPE, true, NO_LINE, null, null)));
    }

    private Optional<String> resolveClassId(Type type) {
        try {
            ResolvedType resolved = type.resolve();
            return resolved.isReferenceType()
                    ? Optional.of(resolved.asReferenceType().getQualifiedName())
                    : Optional.empty();
        } catch (RuntimeException e) {
            log.debug("Could not resolve type {}: {}", type, e.getMessage());
            return Optional.empty();
        }
    }
}
