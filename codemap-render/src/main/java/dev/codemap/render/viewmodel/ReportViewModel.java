package dev.codemap.render.viewmodel;

import java.util.List;
import java.util.Objects;

/**
 * Exactly the data the front-end needs, projected from {@link dev.codemap.core.model.CodeIndex}.
 *
 * <p>This is the boundary between the analysis model and the report: it carries
 * no rendering concerns of its own (no colours, no CSS classes, no D3 shapes),
 * only the facts the client-side script decides how to draw.
 *
 * @param modules build modules, the top-level roots of the map
 * @param entryPoints externally reachable triggers, each hanging under its module
 * @param classes every indexed type
 * @param methods every indexed method or constructor, including embedded source
 * @param edges every call, type-use, and implementation edge
 * @param moduleDependencies module-to-module dependencies aggregated from
 *        {@code CROSS_MODULE} edges — the module overview diagram
 * @param removedMethods line ranges the diff deleted, with no surviving declaration
 */
public record ReportViewModel(
        List<ModuleView> modules,
        List<EntryPointView> entryPoints,
        List<ClassView> classes,
        List<MethodView> methods,
        List<EdgeView> edges,
        List<ModuleDependencyView> moduleDependencies,
        List<RemovedMethodView> removedMethods) {

    public ReportViewModel {
        modules = List.copyOf(Objects.requireNonNull(modules, "modules"));
        entryPoints = List.copyOf(Objects.requireNonNull(entryPoints, "entryPoints"));
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        moduleDependencies = List.copyOf(Objects.requireNonNull(moduleDependencies, "moduleDependencies"));
        removedMethods = List.copyOf(Objects.requireNonNull(removedMethods, "removedMethods"));
    }
}
