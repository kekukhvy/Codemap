package dev.codemap.core.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the exclusion filter directly.
 *
 * <p>Worth testing on its own rather than only end to end: in the reference
 * project the generated sources sit at {@code src/main/generated}, a sibling of
 * {@code src/main/java} rather than a child, so they are already outside every
 * production root. A whole-project run therefore never exercises this filter, and
 * an end-to-end assertion that "no generated path appears" would pass even if the
 * filter did nothing at all.
 */
class SourceRootResolverTest {

    private static final String MAIN_SOURCES = "src/main/java";

    private final SourceRootResolver resolver = new SourceRootResolver();

    @TempDir
    Path projectRoot;

    @Nested
    @DisplayName("excluded paths")
    class ExcludedPaths {

        @ParameterizedTest
        @ValueSource(strings = {
                "src/test/java/com/example/ServiceTest.java",
                "src/it/java/com/example/ServiceIT.java",
                "src/integrationTest/java/com/example/ServiceIT.java",
                "src/testFixtures/java/com/example/Fixture.java",
                "build/generated/sources/Generated.java",
                "target/generated/sources/Generated.java",
                "module/generated-sources/annotations/Generated.java"
        })
        @DisplayName("rejects test and generated locations")
        void rejectsExcludedLocations(String path) {
            assertThat(SourceRootResolver.isProductionPath(path)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "src/main/java/com/example/Service.java",
                "core/src/main/java/com/example/domain/Task.java",
                "src/main/java/com/example/testing/TestSupport.java"
        })
        @DisplayName("accepts production locations, including packages whose names merely contain 'test'")
        void acceptsProductionLocations(String path) {
            assertThat(SourceRootResolver.isProductionPath(path)).isTrue();
        }

        @Test
        @DisplayName("matches regardless of case")
        void matchesRegardlessOfCase() {
            assertThat(SourceRootResolver.isProductionPath("SRC/TEST/java/A.java")).isFalse();
            assertThat(SourceRootResolver.isProductionPath("build/GENERATED/A.java")).isFalse();
        }
    }

    @Nested
    @DisplayName("resolving roots")
    class ResolvingRoots {

        @Test
        @DisplayName("returns the production root and never the test root")
        void returnsProductionRootOnly() throws IOException {
            Files.createDirectories(projectRoot.resolve(MAIN_SOURCES));
            Files.createDirectories(projectRoot.resolve("src/test/java"));

            assertThat(resolver.resolve(projectRoot, projectRoot)).containsExactly(MAIN_SOURCES);
        }

        @Test
        @DisplayName("returns nothing for a module with no production source")
        void returnsNothingWithoutProductionSource() throws IOException {
            Files.createDirectories(projectRoot.resolve("src/test/java"));

            assertThat(resolver.resolve(projectRoot, projectRoot)).isEmpty();
        }

        @Test
        @DisplayName("uses / separators so an index reads on any platform")
        void usesForwardSlashSeparators() throws IOException {
            Path module = projectRoot.resolve("core");
            Files.createDirectories(module.resolve(MAIN_SOURCES));

            assertThat(resolver.resolve(projectRoot, module))
                    .containsExactly("core/" + MAIN_SOURCES);
        }
    }
}
