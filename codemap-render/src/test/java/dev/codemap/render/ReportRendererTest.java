package dev.codemap.render;

import dev.codemap.core.CodemapOptions;
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
import dev.codemap.core.model.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ReportRenderer} assembles a single self-contained {@code report.html}
 * from a {@link CodeIndex}. Built from a small fixture index — this stage
 * needs no git and no AST (spec §6.1) — and asserted against the generated
 * HTML/JS payload, since the interactive behaviour itself is JavaScript.
 */
class ReportRendererTest {

    private static final String MODULE_ID = "kairos-api";
    private static final String OTHER_MODULE_ID = "common";
    private static final String CLASS_ID = "com.example.TaskController";
    private static final String OTHER_CLASS_ID = "com.example.TaskRepository";
    /** D3, the embedded data, and the report script — the only three the template opens. */
    private static final int EXPECTED_SCRIPT_ELEMENTS = 3;

    private static final String METHOD_ID = "com.example.TaskController#create()";
    private static final String OTHER_METHOD_ID = "com.example.TaskRepository#save()";
    /**
     * A method body shaped like the worst thing an analysed repository can
     * contain: the variants that end a script element without matching the
     * literal {@code </script>} — a space-separated end tag, an uppercase one,
     * and a self-closing one — each followed by live markup. A plain code
     * comment is enough to carry this, so it needs no unusual syntax to survive
     * review in the repository being mapped.
     */
    private static final String SOURCE_WITH_SCRIPT_TAG =
            "public void create() {\n"
                    + "    // </script> should not break the page\n"
                    + "    // </script foo><img src=x onerror=alert(1)>\n"
                    + "    // </SCRIPT><img src=x onerror=alert(2)>\n"
                    + "    // </script/><img src=x onerror=alert(3)>\n"
                    + "    save();\n}";

    private final ReportRenderer renderer = new ReportRenderer();

    @TempDir
    Path projectRoot;

    @Nested
    @DisplayName("self-containment")
    class SelfContainment {

        @Test
        @DisplayName("produces one HTML file with no external references")
        void hasNoExternalReferences() throws IOException {
            String html = render(fixtureIndex());

            assertThat(html).doesNotContain("src=\"http");
            assertThat(html).doesNotContain("href=\"http");
            assertThat(html).doesNotContain("<script src=");
            assertThat(html).doesNotContain("<link rel=\"stylesheet\" href=");
        }

        @Test
        @DisplayName("inlines the vendored D3 bundle")
        void inlinesD3() throws IOException {
            String html = render(fixtureIndex());

            assertThat(html).contains("d3");
            assertThat(html.length()).isGreaterThan(50_000);
        }
    }

    @Nested
    @DisplayName("embedded data")
    class EmbeddedData {

        @Test
        @DisplayName("embeds module roots and entry points beneath them")
        void embedsModulesAndEntryPoints() throws IOException {
            String html = render(fixtureIndex());

            assertThat(html).contains(MODULE_ID);
            assertThat(html).contains("POST /api/v1/tasks");
        }

        @Test
        @DisplayName("embeds the real method source, not a placeholder")
        void embedsMethodSource() throws IOException {
            String html = render(fixtureIndex());

            assertThat(html).contains("save();");
        }

        @Test
        @DisplayName("escapes a </script> occurring inside embedded source so the script block is not broken")
        void escapesEmbeddedScriptTag() throws IOException {
            String html = render(fixtureIndex());

            long scriptOpenTags = countOccurrences(html, "<script");
            long scriptCloseTags = countOccurrences(html, "</script>");
            assertThat(scriptCloseTags).isEqualTo(scriptOpenTags);
        }

        @Test
        @DisplayName("declares a content security policy that denies everything the report does not inline")
        void declaresARestrictiveContentSecurityPolicy() throws IOException {
            String html = render(fixtureIndex());

            assertThat(html).contains("Content-Security-Policy");
            assertThat(html).contains("default-src 'none'");
        }

        /**
         * Counting tags is not enough on its own: a payload can leave the counts
         * balanced and still close the element early. The page must contain
         * exactly the script elements the template opens — the three the
         * assembler writes — and nothing the embedded source smuggled in.
         */
        @Test
        @DisplayName("no script end tag from embedded source survives into the page")
        void embeddedSourceCannotCloseTheScriptElement() throws IOException {
            String html = render(fixtureIndex());

            assertThat(html)
                    .doesNotContainPattern("(?i)</script[\\s/]")
                    .doesNotContainPattern("(?i)<img");
            assertThat(countOccurrences(html, "</script>")).isEqualTo(EXPECTED_SCRIPT_ELEMENTS);
        }

        @Test
        @DisplayName("carries module dependencies for the module overview")
        void embedsModuleDependencies() throws IOException {
            String html = render(fixtureIndex());

            assertThat(html).contains(OTHER_MODULE_ID);
        }

        @Test
        @DisplayName("carries edge kinds so internal/external/cross-module calls render distinctly")
        void embedsEdgeKinds() throws IOException {
            String html = render(fixtureIndex());

            assertThat(html).contains(EdgeKind.CALL_EXTERNAL.name());
            assertThat(html).contains(EdgeKind.CROSS_MODULE.name());
        }

        @Test
        @DisplayName("carries change status so changed/affected/removed nodes can be outlined")
        void embedsChangeStatus() throws IOException {
            String html = render(fixtureIndex());

            assertThat(html).contains(ChangeStatus.CHANGED.name());
        }

        @Test
        @DisplayName("carries the layer of each class for the layer badge and filter")
        void embedsLayer() throws IOException {
            String html = render(fixtureIndex());

            assertThat(html).contains(Layer.ENTRY.name());
            assertThat(html).contains(Layer.INFRASTRUCTURE.name());
        }
    }

    private String render(CodeIndex index) throws IOException {
        Path output = projectRoot.resolve("codemap/report.html");
        CodemapOptions options = CodemapOptions.builder().root(projectRoot).output(output).build();
        renderer.render(options, index);
        return Files.readString(output);
    }

    private long countOccurrences(String text, String token) {
        long count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) != -1) {
            count++;
            index += token.length();
        }
        return count;
    }

    private CodeIndex fixtureIndex() {
        return CodeIndex.builder()
                .modules(List.of(
                        new IndexedModule(MODULE_ID, "kairos-api", "kairos-api", List.of("src/main/java")),
                        new IndexedModule(OTHER_MODULE_ID, "common", "common", List.of("src/main/java"))))
                .entryPoints(List.of(new EntryPoint(
                        "entry-1", MODULE_ID, EntryPointKind.REST, "POST /api/v1/tasks",
                        METHOD_ID, DetectedBy.RULE, new SourceLocation("TaskController.java", 10))))
                .classes(List.of(
                        new IndexedClass(CLASS_ID, MODULE_ID, "com.example.TaskController", "TaskController",
                                "com.example", TypeKind.CLASS, Layer.ENTRY, "TaskController.java", 1, 20, null),
                        new IndexedClass(OTHER_CLASS_ID, MODULE_ID, "com.example.TaskRepository", "TaskRepository",
                                "com.example", TypeKind.CLASS, Layer.INFRASTRUCTURE, "TaskRepository.java", 1, 20, null)))
                .methods(List.of(
                        new IndexedMethod(METHOD_ID, CLASS_ID, "create", "create()", "TaskController.java",
                                10, 13, null, SOURCE_WITH_SCRIPT_TAG, false, Visibility.PUBLIC, ChangeStatus.CHANGED),
                        new IndexedMethod(OTHER_METHOD_ID, OTHER_CLASS_ID, "save", "save()", "TaskRepository.java",
                                5, 7, null, "public void save() { }", false, Visibility.PUBLIC, ChangeStatus.UNCHANGED)))
                .calls(List.of(
                        new CallEdge(METHOD_ID, OTHER_METHOD_ID, EdgeKind.CALL_EXTERNAL, true, 12, null, null),
                        new CallEdge(METHOD_ID, "com.example.Shared#validate()",
                                EdgeKind.CROSS_MODULE, true, 6, MODULE_ID, OTHER_MODULE_ID)))
                .build();
    }
}
