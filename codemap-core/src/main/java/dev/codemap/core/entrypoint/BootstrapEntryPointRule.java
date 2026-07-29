package dev.codemap.core.entrypoint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.MethodDeclaration;
import dev.codemap.core.model.DetectedBy;
import dev.codemap.core.model.EntryPoint;
import dev.codemap.core.model.EntryPointKind;
import dev.codemap.core.model.SourceLocation;
import dev.codemap.core.parse.MethodOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Recognises {@code public static void main(String[] args)} as a {@link EntryPointKind#BOOTSTRAP}
 * root (spec §4.1).
 */
final class BootstrapEntryPointRule implements EntryPointRule {

    private static final String MAIN_METHOD_NAME = "main";
    private static final int UNKNOWN_LINE = 0;
    private static final String LABEL_PREFIX = "main() — ";

    @Override
    public List<EntryPoint> detect(CompilationUnit unit, String moduleId, String relativePath) {
        List<EntryPoint> entryPoints = new ArrayList<>();
        for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
            toEntryPoint(method, moduleId, relativePath).ifPresent(entryPoints::add);
        }
        return entryPoints;
    }

    private Optional<EntryPoint> toEntryPoint(MethodDeclaration method, String moduleId, String relativePath) {
        if (!isMainMethod(method)) {
            return Optional.empty();
        }
        String methodId = MethodOwner.idOf(method);
        String classId = MethodOwner.classIdOf(method);
        if (methodId == null || classId == null) {
            return Optional.empty();
        }
        int line = method.getBegin().map(position -> position.line).orElse(UNKNOWN_LINE);
        return Optional.of(new EntryPoint(
                EntryPointIds.of(moduleId, EntryPointKind.BOOTSTRAP, methodId),
                moduleId,
                EntryPointKind.BOOTSTRAP,
                LABEL_PREFIX + simpleName(classId),
                methodId,
                DetectedBy.RULE,
                new SourceLocation(relativePath, line)));
    }

    private boolean isMainMethod(MethodDeclaration method) {
        return method.getNameAsString().equals(MAIN_METHOD_NAME)
                && method.hasModifier(Modifier.Keyword.STATIC)
                && method.hasModifier(Modifier.Keyword.PUBLIC);
    }

    private String simpleName(String classId) {
        int lastDot = classId.lastIndexOf('.');
        return lastDot < 0 ? classId : classId.substring(lastDot + 1);
    }
}
