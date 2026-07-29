package dev.codemap.core.entrypoint;

import com.github.javaparser.ast.CompilationUnit;
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

/**
 * Recognises a Jakarta WebSocket {@code @ServerEndpoint} class's {@code @OnMessage}
 * method as a {@link EntryPointKind#SOCKET} entry point (spec §4.1).
 *
 * <p>Mirrors the REST controller shape: the class carries the endpoint marker, and
 * the entry point is the specific method the container dispatches an incoming
 * message to, not the class itself.
 */
final class ServerEndpointEntryPointRule implements EntryPointRule {

    private static final String ENDPOINT_ANNOTATION = "ServerEndpoint";
    private static final String ON_MESSAGE_ANNOTATION = "OnMessage";
    private static final String LABEL_PREFIX = "onMessage: ";
    private static final int UNKNOWN_LINE = 0;

    @Override
    public List<EntryPoint> detect(CompilationUnit unit, String moduleId, String relativePath) {
        List<EntryPoint> entryPoints = new ArrayList<>();
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            if (!isServerEndpoint(type)) {
                continue;
            }
            for (MethodDeclaration method : type.getMethods()) {
                toEntryPoint(method, moduleId, relativePath).ifPresent(entryPoints::add);
            }
        }
        return entryPoints;
    }

    private boolean isServerEndpoint(TypeDeclaration<?> type) {
        return type.getAnnotations().stream()
                .map(AnnotationNames::simpleNameOf)
                .anyMatch(ENDPOINT_ANNOTATION::equals);
    }

    private Optional<EntryPoint> toEntryPoint(MethodDeclaration method, String moduleId, String relativePath) {
        boolean isMessageHandler = method.getAnnotations().stream()
                .map(AnnotationNames::simpleNameOf)
                .anyMatch(ON_MESSAGE_ANNOTATION::equals);
        if (!isMessageHandler) {
            return Optional.empty();
        }
        String methodId = MethodOwner.idOf(method);
        if (methodId == null) {
            return Optional.empty();
        }
        int line = method.getBegin().map(position -> position.line).orElse(UNKNOWN_LINE);
        return Optional.of(new EntryPoint(
                EntryPointIds.of(moduleId, EntryPointKind.SOCKET, methodId),
                moduleId,
                EntryPointKind.SOCKET,
                LABEL_PREFIX + method.getNameAsString(),
                methodId,
                DetectedBy.RULE,
                new SourceLocation(relativePath, line)));
    }
}
