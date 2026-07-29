package dev.codemap.core.index;

import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.CodeIndex;
import dev.codemap.core.model.EdgeKind;
import dev.codemap.core.model.IndexedClass;
import dev.codemap.core.model.IndexedModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectIndexerTest {

    private static final String MAIN_SOURCES = "src/main/java";
    private static final String TEST_SOURCES = "src/test/java";
    private static final String PACKAGE_PATH = "com/example";

    private final ProjectIndexer indexer = new ProjectIndexer();

    @TempDir
    Path projectRoot;

    @Nested
    @DisplayName("what gets indexed")
    class Coverage {

        @Test
        @DisplayName("indexes production sources and records a fingerprint per file")
        void indexesProductionSources() throws IOException {
            writeProductionClass("Service", "public class Service { public void run() {} }");

            CodeIndex index = indexer.index(projectRoot);

            assertThat(index.classes()).hasSize(1);
            assertThat(index.methods()).hasSize(1);
            assertThat(index.files()).hasSize(1);
            assertThat(index.files().values().iterator().next().hash()).isNotBlank();
        }

        @Test
        @DisplayName("never indexes test sources")
        void excludesTestSources() throws IOException {
            writeProductionClass("Service", "public class Service {}");
            writeTestClass("ServiceTest", "public class ServiceTest {}");

            CodeIndex index = indexer.index(projectRoot);

            assertThat(index.classes()).extracting(IndexedClass::simpleName).containsExactly("Service");
            assertThat(index.files().keySet()).noneMatch(file -> file.contains("/test/"));
        }

        @Test
        @DisplayName("attributes every class to a module")
        void attributesEveryClassToAModule() throws IOException {
            writeProductionClass("Service", "public class Service {}");

            CodeIndex index = indexer.index(projectRoot);
            String moduleId = index.modules().get(0).id();

            assertThat(index.classes()).allSatisfy(indexed ->
                    assertThat(indexed.moduleId()).isEqualTo(moduleId));
            assertThat(index.classesOf(moduleId)).hasSize(1);
        }

        @Test
        @DisplayName("never indexes generated sources nested under a production root")
        void excludesGeneratedSources() throws IOException {
            writeProductionClass("Service", "public class Service {}");
            Path generated = projectRoot.resolve(MAIN_SOURCES).resolve("build/generated");
            Files.createDirectories(generated);
            Files.writeString(generated.resolve("Tables.java"), "package build.generated;\npublic class Tables {}");

            CodeIndex index = indexer.index(projectRoot);

            assertThat(index.classes()).extracting(IndexedClass::simpleName).containsExactly("Service");
            assertThat(index.files().keySet()).noneMatch(file -> file.contains("generated"));
        }

        @Test
        @DisplayName("skips package-info, which declares no type")
        void skipsPackageInfo() throws IOException {
            writeProductionClass("Service", "public class Service {}");
            Files.writeString(sourceDirectory().resolve("package-info.java"),
                    "/** Docs. */\npackage com.example;\n");

            assertThat(indexer.index(projectRoot).files().keySet())
                    .noneMatch(file -> file.endsWith("package-info.java"));
        }
    }

    @Nested
    @DisplayName("degradation")
    class Degradation {

        @Test
        @DisplayName("indexes the good files and records the bad one, rather than failing")
        void degradesOnUnparseableFile() throws IOException {
            writeProductionClass("Good", "public class Good { void go() {} }");
            writeProductionClass("Bad", "public class Bad { ### not java");

            CodeIndex index = indexer.index(projectRoot);

            assertThat(index.classes()).extracting(IndexedClass::simpleName).containsExactly("Good");
            assertThat(index.statistics().hasSkippedFiles()).isTrue();
            assertThat(index.statistics().skipped()).singleElement()
                    .satisfies(skipped -> {
                        assertThat(skipped.file()).endsWith("Bad.java");
                        assertThat(skipped.reason()).isNotBlank();
                    });
        }

        @Test
        @DisplayName("produces an empty index for a project with no sources")
        void handlesProjectWithoutSources() {
            CodeIndex index = indexer.index(projectRoot);

            assertThat(index.modules()).isNotEmpty();
            assertThat(index.classes()).isEmpty();
            assertThat(index.statistics().filesScanned()).isZero();
        }
    }

    @Nested
    @DisplayName("statistics")
    class Statistics {

        @Test
        @DisplayName("counts scanned, parsed, and indexed elements")
        void countsWhatItDid() throws IOException {
            writeProductionClass("A", "public class A { void one() {} void two() {} }");
            writeProductionClass("B", "public class B { void three() {} }");

            CodeIndex index = indexer.index(projectRoot);

            assertThat(index.statistics().filesScanned()).isEqualTo(2);
            assertThat(index.statistics().filesParsed()).isEqualTo(2);
            assertThat(index.statistics().classesIndexed()).isEqualTo(2);
            assertThat(index.statistics().methodsIndexed()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("entry points across modules")
    class EntryPointsPerModule {

        @Test
        @DisplayName("attributes each entry point to the module that declares it")
        void separatesEntryPointsByModule() throws IOException {
            Files.writeString(projectRoot.resolve("settings.gradle"), "include 'api'\ninclude 'admin'\ninclude 'shared'\n");
            writeClassIn("api", "ApiApplication", """
                    public class ApiApplication {
                        public static void main(String[] args) {
                        }
                    }
                    """);
            writeClassIn("admin", "AdminApplication", """
                    public class AdminApplication {
                        public static void main(String[] args) {
                        }
                    }
                    """);
            writeClassIn("shared", "Util", """
                    public class Util {
                        public static void help() {
                        }
                    }
                    """);

            CodeIndex index = indexer.index(projectRoot);

            assertThat(index.entryPointsOf("api")).singleElement()
                    .satisfies(entryPoint -> assertThat(entryPoint.moduleId()).isEqualTo("api"));
            assertThat(index.entryPointsOf("admin")).hasSize(1);
            assertThat(index.entryPointsOf("shared"))
                    .as("a library module reached by nothing external has no entry point, which is normal")
                    .isEmpty();
        }

        @Test
        @DisplayName("indexes a project where most modules have no entry point at all")
        void toleratesModulesWithoutEntryPoints() throws IOException {
            Files.writeString(projectRoot.resolve("settings.gradle"), "include 'app'\ninclude 'lib-a'\ninclude 'lib-b'\n");
            writeClassIn("app", "App", """
                    public class App {
                        public static void main(String[] args) {
                        }
                    }
                    """);
            writeClassIn("lib-a", "A", "public class A {}");
            writeClassIn("lib-b", "B", "public class B {}");

            CodeIndex index = indexer.index(projectRoot);

            assertThat(index.modules()).hasSize(3);
            assertThat(index.entryPoints()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("multi-module projects")
    class MultiModule {

        @Test
        @DisplayName("makes each declared module a root with its own classes")
        void indexesEachModuleSeparately() throws IOException {
            Files.writeString(projectRoot.resolve("settings.gradle"), "include 'api'\ninclude 'core'\n");
            writeClassIn("api", "Endpoint", "public class Endpoint {}");
            writeClassIn("core", "Engine", "public class Engine {}");

            CodeIndex index = indexer.index(projectRoot);

            assertThat(index.modules()).extracting(IndexedModule::name).containsExactly("api", "core");
            assertThat(index.classesOf("api")).extracting(IndexedClass::simpleName).containsExactly("Endpoint");
            assertThat(index.classesOf("core")).extracting(IndexedClass::simpleName).containsExactly("Engine");
        }
    }

    @Nested
    @DisplayName("call graph")
    class CallGraph {

        @Test
        @DisplayName("resolves calls across the indexed project and includes them in the index")
        void buildsCallGraphAlongsideClasses() throws IOException {
            writeProductionClass("TaskHandler", """
                    public class TaskHandler {
                        private final CreateTaskUseCase createTaskUseCase;

                        public TaskHandler(CreateTaskUseCase createTaskUseCase) {
                            this.createTaskUseCase = createTaskUseCase;
                        }

                        public void create() {
                            createTaskUseCase.execute();
                        }
                    }
                    """);
            writeProductionClass("CreateTaskUseCase", """
                    public class CreateTaskUseCase {
                        public void execute() {
                        }
                    }
                    """);

            CodeIndex index = indexer.index(projectRoot);

            assertThat(index.calls()).anySatisfy(edge -> {
                assertThat(edge.from()).isEqualTo("com.example.TaskHandler#create()");
                assertThat(edge.to()).isEqualTo("com.example.CreateTaskUseCase#execute()");
                assertThat(edge.kind()).isEqualTo(EdgeKind.CALL_EXTERNAL);
            });
        }

        @Test
        @DisplayName("builds no edges, rather than failing, when nothing calls anything")
        void handlesProjectWithNoCalls() throws IOException {
            writeProductionClass("Empty", "public class Empty {}");

            CodeIndex index = indexer.index(projectRoot);

            assertThat(index.calls()).isEmpty();
        }
    }

    @Nested
    @DisplayName("entry points")
    class EntryPoints {

        @Test
        @DisplayName("detects a BOOTSTRAP entry point and attributes it to the module")
        void detectsBootstrapEntryPoint() throws IOException {
            writeProductionClass("Application", """
                    public class Application {
                        public static void main(String[] args) {
                        }
                    }
                    """);

            CodeIndex index = indexer.index(projectRoot);
            String moduleId = index.modules().get(0).id();

            assertThat(index.entryPoints()).singleElement().satisfies(entryPoint -> {
                assertThat(entryPoint.kind()).isEqualTo(dev.codemap.core.model.EntryPointKind.BOOTSTRAP);
                assertThat(entryPoint.moduleId()).isEqualTo(moduleId);
                assertThat(entryPoint.detectedBy()).isEqualTo(dev.codemap.core.model.DetectedBy.RULE);
            });
        }

        @Test
        @DisplayName("every detected entry point's methodId joins to a method actually indexed")
        void everyEntryPointMethodIdJoinsToAnIndexedMethod() throws IOException {
            writeProductionClass("TaskHandler", """
                    public class TaskHandler {
                        public void create(String body) {
                        }
                    }
                    """);
            writeProductionClass("Router", """
                    public final class Router {
                        private static final String TASKS = "/api/v1/tasks";

                        public static void register(io.javalin.Javalin app, TaskHandler taskHandler) {
                            app.post(TASKS, taskHandler::create);
                        }
                    }
                    """);

            CodeIndex index = indexer.index(projectRoot);
            java.util.Set<String> indexedMethodIds = index.methods().stream()
                    .map(dev.codemap.core.model.IndexedMethod::id)
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(index.entryPoints()).isNotEmpty();
            assertThat(index.entryPoints())
                    .as("an entry point method id that does not join to an indexed method can never be navigated to")
                    .allSatisfy(entryPoint -> assertThat(indexedMethodIds).contains(entryPoint.methodId()));
        }

        @Test
        @DisplayName("produces no entry points, rather than failing, when nothing matches a rule")
        void handlesProjectWithNoEntryPoints() throws IOException {
            writeProductionClass("Plain", "public class Plain { void doWork() {} }");

            CodeIndex index = indexer.index(projectRoot);

            assertThat(index.entryPoints()).isEmpty();
        }
    }

    private Path sourceDirectory() throws IOException {
        Path directory = projectRoot.resolve(MAIN_SOURCES).resolve(PACKAGE_PATH);
        Files.createDirectories(directory);
        return directory;
    }

    private void writeProductionClass(String name, String body) throws IOException {
        Files.writeString(sourceDirectory().resolve(name + ".java"), "package com.example;\n" + body);
    }

    private void writeTestClass(String name, String body) throws IOException {
        Path directory = projectRoot.resolve(TEST_SOURCES).resolve(PACKAGE_PATH);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".java"), "package com.example;\n" + body);
    }

    private void writeClassIn(String modulePath, String name, String body) throws IOException {
        Path directory = projectRoot.resolve(modulePath).resolve(MAIN_SOURCES).resolve(PACKAGE_PATH);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".java"), "package com.example;\n" + body);
    }
}
