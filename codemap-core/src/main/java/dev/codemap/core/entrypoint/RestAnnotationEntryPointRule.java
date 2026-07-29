package dev.codemap.core.entrypoint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import dev.codemap.core.model.DetectedBy;
import dev.codemap.core.model.EntryPoint;
import dev.codemap.core.model.EntryPointKind;
import dev.codemap.core.model.SourceLocation;
import dev.codemap.core.parse.MethodOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Recognises Spring MVC and JAX-RS route methods as {@link EntryPointKind#REST}
 * entry points (spec §4.1).
 *
 * <p>A method only qualifies when its declaring type also carries a controller
 * marker ({@code @RestController}/{@code @Controller}, or JAX-RS {@code @Path}) —
 * a mapping annotation alone, without that marker, is not wired into a server.
 */
final class RestAnnotationEntryPointRule implements EntryPointRule {

    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of("RestController", "Controller", "Path");
    private static final int UNKNOWN_LINE = 0;

    @Override
    public List<EntryPoint> detect(CompilationUnit unit, String moduleId, String relativePath) {
        List<EntryPoint> entryPoints = new ArrayList<>();
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            if (!(type instanceof ClassOrInterfaceDeclaration) || !isController(type)) {
                continue;
            }
            for (MethodDeclaration method : type.getMethods()) {
                toEntryPoint(method, moduleId, relativePath).ifPresent(entryPoints::add);
            }
        }
        return entryPoints;
    }

    private boolean isController(TypeDeclaration<?> type) {
        return type.getAnnotations().stream()
                .map(AnnotationNames::simpleNameOf)
                .anyMatch(CONTROLLER_ANNOTATIONS::contains);
    }

    private Optional<EntryPoint> toEntryPoint(MethodDeclaration method, String moduleId, String relativePath) {
        Optional<RouteMapping> mapping = RouteMapping.from(method);
        if (mapping.isEmpty()) {
            return Optional.empty();
        }
        String methodId = MethodOwner.idOf(method);
        if (methodId == null) {
            return Optional.empty();
        }
        int line = method.getBegin().map(position -> position.line).orElse(UNKNOWN_LINE);
        RouteMapping route = mapping.get();
        return Optional.of(new EntryPoint(
                EntryPointIds.of(moduleId, EntryPointKind.REST, methodId),
                moduleId,
                EntryPointKind.REST,
                route.label(),
                methodId,
                DetectedBy.RULE,
                new SourceLocation(relativePath, line)));
    }
}
