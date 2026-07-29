package dev.codemap.core.model;

import java.util.List;
import java.util.Locale;

/**
 * Architectural layer a class belongs to, inferred from its package path.
 *
 * <p>Layers drive the side-panel badge and the layer filter, and they make
 * dependency violations visible without a dedicated rule: a {@link #DOMAIN} class
 * calling into {@link #INFRASTRUCTURE} is an inward dependency pointing outward,
 * and the map shows it.
 *
 * <p>Inference is a heuristic over package names. It is deliberately conservative
 * — anything unrecognised becomes {@link #UNKNOWN} rather than being forced into
 * a layer, because a confidently wrong layer is worse than an honest blank.
 */
public enum Layer {

    /** Adapters the outside world reaches first: controllers, handlers, routes, UI. */
    ENTRY(List.of("api", "web", "rest", "controller", "handler", "resource", "endpoint", "ui", "view"),
            List.of("controller", "handler", "resource", "endpoint", "view", "screen", "page")),

    /** Use cases and orchestration. */
    APPLICATION(List.of("application", "usecase", "usecases", "service", "services", "command", "commands"),
            List.of("usecase", "service", "command", "query", "orchestrator")),

    /** Entities, value objects, and the rules that govern them. */
    DOMAIN(List.of("domain", "model", "entity", "entities"),
            List.of("entity", "aggregate")),

    /** Persistence, messaging, and other technical detail. */
    INFRASTRUCTURE(List.of("infrastructure", "infra", "persistence", "repository", "adapter", "adapters", "config"),
            List.of("repository", "adapter", "client", "gateway", "config", "configuration")),

    /** Cross-cutting helpers shared by the rest. */
    SUPPORT(List.of("common", "shared", "util", "utils", "helper", "helpers", "exception", "exceptions", "dto"),
            List.of("util", "utils", "helper", "exception", "dto", "request", "response", "mapper")),

    /** No recognised marker in the package path or type name. */
    UNKNOWN(List.of(), List.of());

    private static final String PACKAGE_SEPARATOR = "\\.";

    private final List<String> markers;
    private final List<String> nameSuffixes;

    Layer(List<String> markers, List<String> nameSuffixes) {
        this.markers = markers;
        this.nameSuffixes = nameSuffixes;
    }

    /**
     * Infers a layer from a fully-qualified package name.
     *
     * <p>Segments are examined from the most specific end backwards, so
     * {@code dev.app.domain.task} resolves as {@code DOMAIN} rather than being
     * decided by an early generic segment. Where several layers could match a
     * segment, the first declared here wins, which keeps the result stable.
     *
     * @param packageName dotted package name; may be empty for the default package
     * @return the inferred layer, or {@link #UNKNOWN} when nothing matches
     */
    public static Layer fromPackage(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return UNKNOWN;
        }
        String[] segments = packageName.toLowerCase(Locale.ROOT).split(PACKAGE_SEPARATOR);
        for (int i = segments.length - 1; i >= 0; i--) {
            Layer match = matching(segments[i]);
            if (match != UNKNOWN) {
                return match;
            }
        }
        return UNKNOWN;
    }

    /**
     * Infers a layer from the package, falling back to the type's name.
     *
     * <p>The fallback exists because feature-oriented codebases group by capability
     * rather than by layer — {@code app.feature.dashboard} says what the code is
     * about but nothing about where it sits. In those projects the layer is carried
     * by the type name instead ({@code DashboardView}, {@code TaskRepository}), and
     * reading it there is better than labelling a third of the map unknown.
     *
     * <p>The package still wins when it is decisive; the name is consulted only
     * when it is not.
     *
     * @param packageName dotted package name; may be empty
     * @param simpleName the type's own name
     * @return the inferred layer, or {@link #UNKNOWN} when neither is decisive
     */
    public static Layer fromPackageOrName(String packageName, String simpleName) {
        Layer fromPackage = fromPackage(packageName);
        if (fromPackage != UNKNOWN) {
            return fromPackage;
        }
        return fromTypeName(simpleName);
    }

    /** Reads a layer from conventional type-name suffixes. */
    private static Layer fromTypeName(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) {
            return UNKNOWN;
        }
        String name = simpleName.toLowerCase(Locale.ROOT);
        for (Layer layer : List.of(ENTRY, INFRASTRUCTURE, APPLICATION, DOMAIN, SUPPORT)) {
            if (layer.nameSuffixes.stream().anyMatch(name::endsWith)) {
                return layer;
            }
        }
        return UNKNOWN;
    }

    private static Layer matching(String segment) {
        for (Layer layer : values()) {
            if (layer.markers.contains(segment)) {
                return layer;
            }
        }
        return UNKNOWN;
    }
}
