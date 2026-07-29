package dev.codemap.core.graph;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.EdgeKind;
import dev.codemap.core.parse.MethodOwner;
import dev.codemap.core.parse.ResolvedMethodIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Walks one compilation unit's method bodies and resolves each call expression to
 * an edge, classified by whether it stays inside the declaring class.
 *
 * <p>Only {@link EdgeKind#CALL_INTERNAL} and {@link EdgeKind#CALL_EXTERNAL} are
 * decided here — a single compilation unit cannot tell whether its target lives in
 * another module. {@code CallGraphBuilder} reclassifies edges that cross a module
 * boundary once every file's class-to-module mapping is known.
 */
final class CallExpressionResolver {

    private static final Logger log = LoggerFactory.getLogger(CallExpressionResolver.class);
    private static final int UNKNOWN_LINE = 0;

    /**
     * Resolves every call expression in every method/constructor body of a
     * compilation unit.
     *
     * @param unit a compilation unit parsed with a symbol solver attached
     * @param projectClassIds ids of every class indexed in the project; a resolved
     *        call whose target class is not in this set is a JDK or third-party
     *        call and produces no edge
     * @return one edge per in-project call expression, plus one per unresolvable
     *         call (degraded, {@code resolved: false})
     */
    List<CallEdge> resolve(CompilationUnit unit, Set<String> projectClassIds) {
        List<CallEdge> edges = new ArrayList<>();
        for (CallableDeclaration<?> callable : unit.findAll(CallableDeclaration.class)) {
            String fromId = MethodOwner.idOf(callable);
            if (fromId == null) {
                continue;
            }
            String fromClassId = MethodOwner.classIdOf(callable);
            for (MethodCallExpr call : callable.findAll(MethodCallExpr.class)) {
                toEdge(call, fromId, fromClassId, projectClassIds).ifPresent(edges::add);
            }
            for (ObjectCreationExpr creation : callable.findAll(ObjectCreationExpr.class)) {
                toConstructorEdge(creation, fromId, fromClassId, projectClassIds).ifPresent(edges::add);
            }
        }
        return edges;
    }

    /**
     * Records a {@code new Foo(...)} as an edge to the constructor.
     *
     * <p>Construction is a call like any other, and the chain it belongs to —
     * "who builds this object" — is exactly what a reader follows. Collecting only
     * method calls would leave every constructor looking uncalled.
     *
     * <p>Unlike a method call there is no degraded fallback: an unresolvable
     * {@code new} names a type that is not on the classpath, so it is third-party
     * by definition and produces nothing.
     */
    private Optional<CallEdge> toConstructorEdge(
            ObjectCreationExpr creation, String fromId, String fromClassId, Set<String> projectClassIds) {
        int line = creation.getBegin().map(position -> position.line).orElse(UNKNOWN_LINE);
        ResolvedConstructorDeclaration target;
        try {
            target = creation.resolve();
        } catch (RuntimeException e) {
            log.debug("Could not resolve constructor {} at line {}: {}",
                    creation.getTypeAsString(), line, e.getMessage());
            return Optional.empty();
        }

        String toClassId = ResolvedMethodIds.classIdOf(target);
        if (!projectClassIds.contains(toClassId)) {
            return Optional.empty();
        }

        EdgeKind kind = toClassId.equals(fromClassId) ? EdgeKind.CALL_INTERNAL : EdgeKind.CALL_EXTERNAL;
        return Optional.of(new CallEdge(fromId, ResolvedMethodIds.idOf(target), kind, true, line, null, null));
    }

    private Optional<CallEdge> toEdge(
            MethodCallExpr call, String fromId, String fromClassId, Set<String> projectClassIds) {
        int line = call.getBegin().map(position -> position.line).orElse(UNKNOWN_LINE);
        Optional<ResolvedMethodDeclaration> resolved = tryResolve(call);

        if (resolved.isEmpty()) {
            return degradedEdge(call, fromId, line);
        }

        ResolvedMethodDeclaration target = resolved.get();
        String toClassId = ResolvedMethodIds.classIdOf(target);
        if (!projectClassIds.contains(toClassId)) {
            return Optional.empty();
        }

        String toId = ResolvedMethodIds.idOf(target);
        EdgeKind kind = toClassId.equals(fromClassId) ? EdgeKind.CALL_INTERNAL : EdgeKind.CALL_EXTERNAL;
        return Optional.of(new CallEdge(fromId, toId, kind, true, line, null, null));
    }

    /**
     * Keeps an unresolvable call only when it might still be one of ours.
     *
     * <p>A call with no receiver — {@code validate(...)} rather than
     * {@code grid.validate(...)} — targets the enclosing type or something it
     * inherits, so it is worth recording as a degraded edge for a reader to
     * follow up.
     *
     * <p>A call through a receiver that the solver could not identify is almost
     * always a library call whose jar is not on the classpath. Recording it would
     * add an edge keyed by a bare method name like {@code getValue}, which can
     * never join to an indexed method and merely inflates the map — exactly the
     * third-party noise the tool is meant to keep out.
     */
    private Optional<CallEdge> degradedEdge(MethodCallExpr call, String fromId, int line) {
        if (call.getScope().isPresent()) {
            log.debug("Dropping unresolvable call through a receiver: {} at line {}",
                    call.getNameAsString(), line);
            return Optional.empty();
        }
        return Optional.of(
                new CallEdge(fromId, call.getNameAsString(), EdgeKind.CALL_EXTERNAL, false, line, null, null));
    }

    private Optional<ResolvedMethodDeclaration> tryResolve(MethodCallExpr call) {
        try {
            return Optional.of(call.resolve());
        } catch (RuntimeException e) {
            log.debug("Could not resolve call {} at line {}: {}",
                    call.getNameAsString(), call.getBegin().map(position -> position.line).orElse(UNKNOWN_LINE),
                    e.getMessage());
            return Optional.empty();
        }
    }
}
