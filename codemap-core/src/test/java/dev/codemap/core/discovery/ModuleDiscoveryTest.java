package dev.codemap.core.discovery;

import dev.codemap.core.model.IndexedModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleDiscoveryTest {

    private static final String MAIN_SOURCES = "src/main/java";
    private static final String TEST_SOURCES = "src/test/java";

    private final ModuleDiscovery discovery = new ModuleDiscovery();

    @TempDir
    Path projectRoot;

    @Nested
    @DisplayName("Gradle projects")
    class Gradle {

        @Test
        @DisplayName("reads modules from settings.gradle, including colon-separated paths")
        void readsGradleSettings() throws IOException {
            writeSettings("""
                    rootProject.name = 'demo'
                    include 'core'
                    include 'adapters:kafka'
                    """);
            createSources("core");
            createSources("adapters/kafka");

            List<IndexedModule> modules = discovery.discover(projectRoot);

            assertThat(modules).extracting(IndexedModule::path)
                    .containsExactly("core", "adapters/kafka");
            assertThat(modules).extracting(IndexedModule::name)
                    .containsExactly("core", "kafka");
        }

        @Test
        @DisplayName("keeps a declared module that has no sources yet")
        void keepsEmptyDeclaredModule() throws IOException {
            writeSettings("include 'core'\ninclude 'planned'\n");
            createSources("core");
            Files.createDirectories(projectRoot.resolve("planned"));

            List<IndexedModule> modules = discovery.discover(projectRoot);

            assertThat(modules).hasSize(2);
            assertThat(modules.get(1).hasSources())
                    .as("a module declared but not yet written is still part of the project")
                    .isFalse();
        }

        @Test
        @DisplayName("handles the multi-argument include form")
        void handlesMultiArgumentInclude() throws IOException {
            writeSettings("include 'a', 'b'\n");
            createSources("a");
            createSources("b");

            assertThat(discovery.discover(projectRoot))
                    .extracting(IndexedModule::path)
                    .containsExactly("a", "b");
        }

        @Test
        @DisplayName("includes the root project when it holds sources of its own")
        void includesRootWhenItHasSources() throws IOException {
            writeSettings("include 'core'\n");
            createSources("core");
            Files.createDirectories(projectRoot.resolve(MAIN_SOURCES));

            assertThat(discovery.discover(projectRoot))
                    .extracting(IndexedModule::path)
                    .containsExactly("", "core");
        }
    }

    @Nested
    @DisplayName("Maven projects")
    class Maven {

        @Test
        @DisplayName("reads modules from pom.xml")
        void readsMavenModules() throws IOException {
            Files.writeString(projectRoot.resolve("pom.xml"), """
                    <project>
                      <modules>
                        <module>service</module>
                        <module>library</module>
                      </modules>
                    </project>
                    """);
            createSources("service");
            createSources("library");

            assertThat(discovery.discover(projectRoot))
                    .extracting(IndexedModule::path)
                    .containsExactly("service", "library");
        }
    }

    @Nested
    @DisplayName("projects without a build file")
    class NoBuildFile {

        @Test
        @DisplayName("treats a plain source tree as a single module")
        void treatsPlainTreeAsSingleModule() throws IOException {
            Files.createDirectories(projectRoot.resolve(MAIN_SOURCES));

            List<IndexedModule> modules = discovery.discover(projectRoot);

            assertThat(modules).hasSize(1);
            assertThat(modules.get(0).sourceRoots()).containsExactly(MAIN_SOURCES);
        }

        @Test
        @DisplayName("still yields one module when there is nothing to find")
        void yieldsOneModuleForEmptyDirectory() {
            List<IndexedModule> modules = discovery.discover(projectRoot);

            assertThat(modules).hasSize(1);
            assertThat(modules.get(0).hasSources()).isFalse();
        }
    }

    @Nested
    @DisplayName("source roots")
    class SourceRoots {

        @Test
        @DisplayName("never returns test sources")
        void excludesTestSources() throws IOException {
            Files.createDirectories(projectRoot.resolve(MAIN_SOURCES));
            Files.createDirectories(projectRoot.resolve(TEST_SOURCES));

            assertThat(discovery.discover(projectRoot).get(0).sourceRoots())
                    .containsExactly(MAIN_SOURCES)
                    .doesNotContain(TEST_SOURCES);
        }
    }

    private void writeSettings(String content) throws IOException {
        Files.writeString(projectRoot.resolve("settings.gradle"), content);
    }

    private void createSources(String modulePath) throws IOException {
        Files.createDirectories(projectRoot.resolve(modulePath).resolve(MAIN_SOURCES));
    }
}
