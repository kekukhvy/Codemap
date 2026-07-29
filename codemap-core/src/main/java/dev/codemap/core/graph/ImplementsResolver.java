package dev.codemap.core.graph;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.types.ResolvedType;
import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.EdgeKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves {@link EdgeKind#IMPLEMENTS} edges from a class to each interface it
 * implements.
 *
 * <p>Because test sources are never indexed (spec §3.5), an interface's in-memory
 * test double is simply absent from the project, so this naturally resolves to
 * production implementations only. A port with several real adapters still
 * legitimately fans out to all of them — static analysis cannot say which is
 * wired at runtime.
 */
final class ImplementsResolver {

    private static final Logger log = LoggerFactory.getLogger(ImplementsResolver.class);
    private static final int NO_LINE = 0;

    /**
     * Resolves every {@code implements} clause declared in a compilation unit.
     *
     * @param unit a compilation unit parsed with a symbol solver attached
     * @param projectClassIds ids of every class indexed in the project; an
     *        interface outside this set produces no edge
     * @return one edge per interface implemented by an in-project class, directed
     *         from the interface to the implementation
     */
    List<CallEdge> resolve(CompilationUnit unit, Set<String> projectClassIds) {
        List<CallEdge> edges = new ArrayList<>();
        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            String implementationId = resolveTypeId(type);
            if (implementationId == null || !projectClassIds.contains(implementationId)) {
                continue;
            }
            for (ClassOrInterfaceType implemented : type.getImplementedTypes()) {
                addImplementsEdge(edges, implemented, implementationId, projectClassIds);
            }
        }
        return edges;
    }

    private void addImplementsEdge(
            List<CallEdge> edges, ClassOrInterfaceType implemented, String implementationId,
            Set<String> projectClassIds) {
        resolveTypeId(implemented)
                .filter(projectClassIds::contains)
                .ifPresent(interfaceId -> edges.add(
                        new CallEdge(interfaceId, implementationId, EdgeKind.IMPLEMENTS, true, NO_LINE, null, null)));
    }

    private String resolveTypeId(ClassOrInterfaceDeclaration type) {
        try {
            return type.resolve().getQualifiedName();
        } catch (RuntimeException e) {
            log.debug("Could not resolve type {}: {}", type.getNameAsString(), e.getMessage());
            return null;
        }
    }

    private Optional<String> resolveTypeId(ClassOrInterfaceType type) {
        try {
            ResolvedType resolved = type.resolve();
            return resolved.isReferenceType()
                    ? Optional.of(resolved.asReferenceType().getQualifiedName())
                    : Optional.empty();
        } catch (RuntimeException e) {
            log.debug("Could not resolve implemented type {}: {}", type.getNameAsString(), e.getMessage());
            return Optional.empty();
        }
    }
}
