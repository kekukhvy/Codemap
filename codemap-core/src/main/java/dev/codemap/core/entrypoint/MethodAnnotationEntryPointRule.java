package dev.codemap.core.entrypoint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
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
 * Recognises a method carrying one of a fixed set of marker annotations as an
 * entry point of a given {@link EntryPointKind} (spec §4.1): {@code @Scheduled}
 * for {@code JOB}; {@code @KafkaListener}/{@code @RabbitListener}/{@code @JmsListener}
 * for {@code MESSAGE}; {@code @MessageMapping} for {@code SOCKET}.
 *
 * <p>Unlike the REST rule, these annotations are self-sufficient — no separate
 * class-level marker is required, since a scheduler or broker invokes the method
 * directly rather than through a controller-style dispatch.
 */
final class MethodAnnotationEntryPointRule implements EntryPointRule {

    private static final int UNKNOWN_LINE = 0;

    private final EntryPointKind kind;
    private final Set<String> markerAnnotations;
    private final String labelPrefix;

    MethodAnnotationEntryPointRule(EntryPointKind kind, String labelPrefix, Set<String> markerAnnotations) {
        this.kind = kind;
        this.labelPrefix = labelPrefix;
        this.markerAnnotations = markerAnnotations;
    }

    @Override
    public List<EntryPoint> detect(CompilationUnit unit, String moduleId, String relativePath) {
        List<EntryPoint> entryPoints = new ArrayList<>();
        for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
            toEntryPoint(method, moduleId, relativePath).ifPresent(entryPoints::add);
        }
        return entryPoints;
    }

    private Optional<EntryPoint> toEntryPoint(MethodDeclaration method, String moduleId, String relativePath) {
        if (!hasMarkerAnnotation(method)) {
            return Optional.empty();
        }
        String methodId = MethodOwner.idOf(method);
        if (methodId == null) {
            return Optional.empty();
        }
        int line = method.getBegin().map(position -> position.line).orElse(UNKNOWN_LINE);
        return Optional.of(new EntryPoint(
                EntryPointIds.of(moduleId, kind, methodId),
                moduleId,
                kind,
                labelPrefix + method.getNameAsString(),
                methodId,
                DetectedBy.RULE,
                new SourceLocation(relativePath, line)));
    }

    private boolean hasMarkerAnnotation(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .map(AnnotationNames::simpleNameOf)
                .anyMatch(markerAnnotations::contains);
    }
}
