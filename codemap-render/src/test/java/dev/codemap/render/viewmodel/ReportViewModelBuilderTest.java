package dev.codemap.render.viewmodel;

import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.ChangeStatus;
import dev.codemap.core.model.CodeIndex;
import dev.codemap.core.model.DetectedBy;
import dev.codemap.core.model.EdgeKind;
import dev.codemap.core.model.EntryPoint;
import dev.codemap.core.model.EntryPointKind;
import dev.codemap.core.model.IndexedClass;
import dev.codemap.core.model.IndexedMethod;
import dev.codemap.core.model.IndexedModule;
import dev.codemap.core.model.Layer;
import dev.codemap.core.model.SourceLocation;
import dev.codemap.core.model.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ReportViewModelBuilder} projects a {@link CodeIndex} into exactly the
 * shape the front-end consumes. Built from a small fixture index rather than a
 * real repo — this stage needs no git and no AST (spec §6.1).
 */
class ReportViewModelBuilderTest {

    private static final String MODULE_ID = "kairos-api";
    private static final String CLASS_ID = "com.example.TaskController";
    private static final String METHOD_ID = "com.example.TaskController#create()";

    private final ReportViewModelBuilder builder = new ReportViewModelBuilder();

    @Test
    @DisplayName("projects modules as top-level roots")
    void projectsModules() {
        CodeIndex index = CodeIndex.builder()
                .modules(List.of(new IndexedModule(MODULE_ID, "kairos-api", "kairos-api", List.of("src/main/java"))))
                .build();

        ReportViewModel viewModel = builder.build(index);

        assertThat(viewModel.modules()).hasSize(1);
        assertThat(viewModel.modules().get(0).id()).isEqualTo(MODULE_ID);
        assertThat(viewModel.modules().get(0).name()).isEqualTo("kairos-api");
    }

    @Test
    @DisplayName("projects entry points beneath their module")
    void projectsEntryPoints() {
        CodeIndex index = CodeIndex.builder()
                .modules(List.of(new IndexedModule(MODULE_ID, "kairos-api", "kairos-api", List.of("src/main/java"))))
                .entryPoints(List.of(new EntryPoint(
                        "entry-1", MODULE_ID, EntryPointKind.REST, "POST /api/v1/tasks",
                        METHOD_ID, DetectedBy.RULE, new SourceLocation("TaskController.java", 10))))
                .build();

        ReportViewModel viewModel = builder.build(index);

        assertThat(viewModel.entryPoints()).hasSize(1);
        assertThat(viewModel.entryPoints().get(0).moduleId()).isEqualTo(MODULE_ID);
        assertThat(viewModel.entryPoints().get(0).label()).isEqualTo("POST /api/v1/tasks");
        assertThat(viewModel.entryPoints().get(0).methodId()).isEqualTo(METHOD_ID);
    }

    @Test
    @DisplayName("embeds the method's real source text, not a placeholder")
    void embedsMethodSource() {
        String source = "public void create() {\n    save();\n}";
        CodeIndex index = CodeIndex.builder()
                .classes(List.of(new IndexedClass(
                        CLASS_ID, MODULE_ID, "com.example.TaskController", "TaskController", "com.example",
                        TypeKind.CLASS, Layer.ENTRY, "TaskController.java", 1, 20, null)))
                .methods(List.of(new IndexedMethod(
                        METHOD_ID, CLASS_ID, "create", "create()", "TaskController.java", 10, 12, null,
                        source, false, ChangeStatus.UNCHANGED)))
                .build();

        ReportViewModel viewModel = builder.build(index);

        assertThat(viewModel.methods()).hasSize(1);
        assertThat(viewModel.methods().get(0).source()).isEqualTo(source);
    }

    @Test
    @DisplayName("carries edge kind and resolution so internal calls render dashed and external solid")
    void projectsEdges() {
        CodeIndex index = CodeIndex.builder()
                .calls(List.of(new CallEdge(METHOD_ID, "com.example.TaskRepository#save()",
                        EdgeKind.CALL_EXTERNAL, true, 11, null, null)))
                .build();

        ReportViewModel viewModel = builder.build(index);

        assertThat(viewModel.edges()).hasSize(1);
        assertThat(viewModel.edges().get(0).kind()).isEqualTo(EdgeKind.CALL_EXTERNAL);
        assertThat(viewModel.edges().get(0).resolved()).isTrue();
    }

    @Test
    @DisplayName("carries the change status of classes and methods for outline colouring")
    void projectsChangeStatus() {
        CodeIndex index = CodeIndex.builder()
                .classes(List.of(new IndexedClass(
                        CLASS_ID, MODULE_ID, "com.example.TaskController", "TaskController", "com.example",
                        TypeKind.CLASS, Layer.ENTRY, "TaskController.java", 1, 20, null, ChangeStatus.CHANGED)))
                .methods(List.of(new IndexedMethod(
                        METHOD_ID, CLASS_ID, "create", "create()", "TaskController.java", 10, 12, null,
                        "void create() { }", false, ChangeStatus.ADDED)))
                .build();

        ReportViewModel viewModel = builder.build(index);

        assertThat(viewModel.classes().get(0).status()).isEqualTo(ChangeStatus.CHANGED);
        assertThat(viewModel.methods().get(0).status()).isEqualTo(ChangeStatus.ADDED);
    }

    @Test
    @DisplayName("aggregates real module dependencies from CROSS_MODULE edges")
    void projectsModuleDependencies() {
        CodeIndex index = CodeIndex.builder()
                .calls(List.of(new CallEdge(METHOD_ID, "com.example.Shared#validate()",
                        EdgeKind.CROSS_MODULE, true, 5, "kairos-api", "common")))
                .build();

        ReportViewModel viewModel = builder.build(index);

        assertThat(viewModel.moduleDependencies()).hasSize(1);
        assertThat(viewModel.moduleDependencies().get(0).fromModuleId()).isEqualTo("kairos-api");
        assertThat(viewModel.moduleDependencies().get(0).toModuleId()).isEqualTo("common");
    }
}
