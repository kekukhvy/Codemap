package dev.codemap.render.viewmodel;

import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.CodeIndex;
import dev.codemap.core.model.EntryPoint;
import dev.codemap.core.model.IndexedClass;
import dev.codemap.core.model.IndexedMethod;
import dev.codemap.core.model.IndexedModule;
import dev.codemap.core.model.ModuleDependency;
import dev.codemap.core.model.RemovedMethod;
import dev.codemap.core.parse.ClassSourceReader;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Projects a {@link CodeIndex} into the {@link ReportViewModel} the front-end
 * consumes.
 *
 * <p>This is a pure projection: no I/O, no git, no AST — only reshaping data
 * already computed by earlier pipeline stages, which is what keeps this stage
 * testable from a small fixture index (spec §6.1).
 */
public final class ReportViewModelBuilder {

    /**
     * Projects the entire index into a view model ready for serialisation and embedding.
     *
     * @param index the analysis result to project
     * @return view model containing modules, entry points, types, methods, edges, and diffs
     *         suitable for JSON serialisation
     */
    public ReportViewModel build(CodeIndex index) {
        Path projectRoot = Path.of(index.root());
        return new ReportViewModel(
                projectModules(index.modules()),
                projectEntryPoints(index.entryPoints()),
                projectClasses(index.classes(), projectRoot),
                projectMethods(index.methods()),
                projectEdges(index.calls()),
                projectModuleDependencies(index.callGraph().moduleDependencies()),
                projectRemovedMethods(index.removedMethods()));
    }

    private List<ModuleView> projectModules(List<IndexedModule> modules) {
        return modules.stream()
                .map(module -> new ModuleView(module.id(), module.name(), module.path()))
                .toList();
    }

    private List<EntryPointView> projectEntryPoints(List<EntryPoint> entryPoints) {
        return entryPoints.stream()
                .map(entryPoint -> new EntryPointView(
                        entryPoint.id(),
                        entryPoint.moduleId(),
                        entryPoint.kind(),
                        entryPoint.label(),
                        entryPoint.methodId(),
                        entryPoint.detectedBy()))
                .toList();
    }

    private List<ClassView> projectClasses(List<IndexedClass> classes, Path projectRoot) {
        return classes.stream()
                .map(indexedClass -> new ClassView(
                        indexedClass.id(),
                        indexedClass.moduleId(),
                        indexedClass.fqn(),
                        indexedClass.simpleName(),
                        indexedClass.packageName(),
                        indexedClass.kind(),
                        indexedClass.layer(),
                        indexedClass.file(),
                        indexedClass.lineStart(),
                        indexedClass.lineEnd(),
                        indexedClass.javadoc(),
                        indexedClass.status(),
                        classSourceOf(indexedClass, projectRoot)))
                .toList();
    }

    /**
     * Reads the class's verbatim declaration text for the side panel.
     *
     * <p>Read here rather than stored on {@link IndexedClass} (spec 007 §5.2):
     * an unreadable file or an invalid line range degrades to an empty string,
     * never throws, so one odd class must not fail the whole report.
     */
    private String classSourceOf(IndexedClass indexedClass, Path projectRoot) {
        return ClassSourceReader.read(
                projectRoot, indexedClass.file(), indexedClass.lineStart(), indexedClass.lineEnd());
    }

    private List<MethodView> projectMethods(List<IndexedMethod> methods) {
        return methods.stream()
                .map(method -> new MethodView(
                        method.id(),
                        method.classId(),
                        method.name(),
                        method.signature(),
                        method.file(),
                        method.lineStart(),
                        method.lineEnd(),
                        method.javadoc(),
                        method.source(),
                        method.constructor(),
                        method.visibility(),
                        method.status()))
                .toList();
    }

    private List<EdgeView> projectEdges(List<CallEdge> edges) {
        return edges.stream()
                .map(edge -> new EdgeView(
                        edge.from(), edge.to(), edge.kind(), edge.resolved(), edge.line(),
                        edge.fromModuleId(), edge.toModuleId()))
                .toList();
    }

    private List<ModuleDependencyView> projectModuleDependencies(Set<ModuleDependency> dependencies) {
        return dependencies.stream()
                .map(dependency -> new ModuleDependencyView(dependency.fromModuleId(), dependency.toModuleId()))
                .toList();
    }

    private List<RemovedMethodView> projectRemovedMethods(List<RemovedMethod> removedMethods) {
        return removedMethods.stream()
                .map(removed -> new RemovedMethodView(removed.file(), removed.lineStart(), removed.lineEnd()))
                .toList();
    }
}
