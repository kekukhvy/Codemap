package dev.codemap.core.entrypoint;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;

import java.util.Map;
import java.util.Optional;

/**
 * An HTTP verb and path read from a Spring or JAX-RS mapping annotation on one
 * method (spec §4.1).
 *
 * @param verb the HTTP method the annotation maps
 * @param path the route path, taken from the annotation's {@code value}/{@code path} member
 */
record RouteMapping(HttpVerb verb, String path) {

    /** Spring mapping annotations that imply a fixed verb. */
    private static final Map<String, HttpVerb> SPRING_VERB_ANNOTATIONS = Map.of(
            "GetMapping", HttpVerb.GET,
            "PostMapping", HttpVerb.POST,
            "PutMapping", HttpVerb.PUT,
            "DeleteMapping", HttpVerb.DELETE,
            "PatchMapping", HttpVerb.PATCH);

    /** JAX-RS verb-marker annotations; the path comes from a separate {@code @Path}. */
    private static final Map<String, HttpVerb> JAX_RS_VERB_ANNOTATIONS = Map.of(
            "GET", HttpVerb.GET,
            "POST", HttpVerb.POST,
            "PUT", HttpVerb.PUT,
            "DELETE", HttpVerb.DELETE,
            "PATCH", HttpVerb.PATCH);

    private static final String PATH_MEMBER = "path";
    private static final String VALUE_MEMBER = "value";
    private static final String JAX_RS_PATH = "Path";
    private static final String EMPTY_PATH = "";

    /**
     * Reads the route mapping declared on a method, if any.
     *
     * @param method a candidate REST handler method
     * @return the mapping, or empty when the method carries no recognised annotation
     */
    static Optional<RouteMapping> from(MethodDeclaration method) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            Optional<RouteMapping> mapping = fromSpringVerbAnnotation(annotation)
                    .or(() -> fromJaxRsVerbAnnotation(method, annotation));
            if (mapping.isPresent()) {
                return mapping;
            }
        }
        return Optional.empty();
    }

    private static Optional<RouteMapping> fromSpringVerbAnnotation(AnnotationExpr annotation) {
        HttpVerb verb = SPRING_VERB_ANNOTATIONS.get(AnnotationNames.simpleNameOf(annotation));
        if (verb == null) {
            return Optional.empty();
        }
        return Optional.of(new RouteMapping(verb, pathOf(annotation).orElse(EMPTY_PATH)));
    }

    private static Optional<RouteMapping> fromJaxRsVerbAnnotation(MethodDeclaration method, AnnotationExpr annotation) {
        HttpVerb verb = JAX_RS_VERB_ANNOTATIONS.get(AnnotationNames.simpleNameOf(annotation));
        if (verb == null) {
            return Optional.empty();
        }
        String path = jaxRsPathAnnotation(method).flatMap(RouteMapping::pathOf).orElse(EMPTY_PATH);
        return Optional.of(new RouteMapping(verb, path));
    }

    private static Optional<AnnotationExpr> jaxRsPathAnnotation(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .filter(annotation -> AnnotationNames.simpleNameOf(annotation).equals(JAX_RS_PATH))
                .findFirst();
    }

    /** Reads the {@code value} (or {@code path}) member of a mapping annotation as a literal string. */
    private static Optional<String> pathOf(AnnotationExpr annotation) {
        return AnnotationMembers.valueOf(annotation, VALUE_MEMBER)
                .or(() -> AnnotationMembers.valueOf(annotation, PATH_MEMBER))
                .flatMap(RouteMapping::literalOf);
    }

    private static Optional<String> literalOf(Expression expression) {
        return expression.isStringLiteralExpr()
                ? Optional.of(expression.asStringLiteralExpr().asString())
                : Optional.empty();
    }

    /** Renders the label a reader sees, e.g. {@code "GET /api/v1/tasks"}. */
    String label() {
        return verb.labelFor(path);
    }
}
