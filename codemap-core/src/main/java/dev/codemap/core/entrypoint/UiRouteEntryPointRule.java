package dev.codemap.core.entrypoint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import dev.codemap.core.model.DetectedBy;
import dev.codemap.core.model.EntryPoint;
import dev.codemap.core.model.EntryPointKind;
import dev.codemap.core.model.SourceLocation;
import dev.codemap.core.parse.MethodOwner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Recognises a Vaadin {@code @Route} class as a {@link EntryPointKind#UI} entry
 * point (spec §4.1).
 *
 * <p>The route path is resolved by {@link ConstantFolder}, since Vaadin projects
 * conventionally hold the path in a constant declared alongside a page title —
 * often in a class of its own ({@code @Route(value = ScheduleRoutes.SCHEDULES)}).
 *
 * <p>Vaadin instantiates a view through its constructor, so the entry point links
 * there rather than to a synthetic class-level id: it is the one place in the
 * declaration where real code actually runs when the route is navigated to.
 */
final class UiRouteEntryPointRule implements EntryPointRule {

    private static final Logger log = LoggerFactory.getLogger(UiRouteEntryPointRule.class);
    private static final String ROUTE_ANNOTATION = "Route";
    private static final String VALUE_MEMBER = "value";
    /** Shown for the root route, which Vaadin declares as an empty string. */
    private static final String ROOT_ROUTE_LABEL = "/ (root)";

    private static final int UNKNOWN_LINE = 0;
    private static final String IMPLICIT_CONSTRUCTOR = "#<init>()";

    private final ConstantFolder constantFolder = new ConstantFolder();

    @Override
    public List<EntryPoint> detect(CompilationUnit unit, String moduleId, String relativePath) {
        List<EntryPoint> entryPoints = new ArrayList<>();
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            if (type instanceof ClassOrInterfaceDeclaration declaration) {
                toEntryPoint(declaration, moduleId, relativePath).ifPresent(entryPoints::add);
            }
        }
        return entryPoints;
    }

    private Optional<EntryPoint> toEntryPoint(
            ClassOrInterfaceDeclaration type, String moduleId, String relativePath) {
        Optional<Expression> routeValue = routeAnnotationValue(type);
        if (routeValue.isEmpty()) {
            return Optional.empty();
        }
        String classId = resolveClassId(type);
        if (classId == null) {
            return Optional.empty();
        }
        String methodId = entryConstructorId(type).orElse(classId + IMPLICIT_CONSTRUCTOR);
        String route = constantFolder.fold(routeValue.get()).orElseGet(() -> constantFolder.textOf(routeValue.get()));
        String label = displayLabel(route);
        int line = type.getBegin().map(position -> position.line).orElse(UNKNOWN_LINE);
        return Optional.of(new EntryPoint(
                EntryPointIds.of(moduleId, EntryPointKind.UI, methodId),
                moduleId,
                EntryPointKind.UI,
                label,
                methodId,
                DetectedBy.RULE,
                new SourceLocation(relativePath, line)));
    }

    private Optional<Expression> routeAnnotationValue(ClassOrInterfaceDeclaration type) {
        return type.getAnnotations().stream()
                .filter(annotation -> AnnotationNames.simpleNameOf(annotation).equals(ROUTE_ANNOTATION))
                .findFirst()
                .flatMap(annotation -> AnnotationMembers.valueOf(annotation, VALUE_MEMBER));
    }

    /** The id of the constructor Vaadin invokes to instantiate the view. */
    private Optional<String> entryConstructorId(ClassOrInterfaceDeclaration type) {
        return type.getConstructors().stream().findFirst().map(MethodOwner::idOf);
    }

    private String resolveClassId(ClassOrInterfaceDeclaration type) {
        try {
            return type.resolve().getQualifiedName();
        } catch (RuntimeException e) {
            log.debug("Could not resolve type {}: {}", type.getNameAsString(), e.getMessage());
            return null;
        }
    }

    /**
     * Renders a route for display, naming the root explicitly.
     *
     * <p>A Vaadin application's landing page is declared as {@code @Route("")},
     * and an empty string would leave that node unlabelled in the map — the one
     * view a reader is most likely to look for first.
     *
     * @param route the folded route value, possibly empty
     * @return a label that always reads as something
     */
    private static String displayLabel(String route) {
        return route == null || route.isBlank() ? ROOT_ROUTE_LABEL : route;
    }
}
