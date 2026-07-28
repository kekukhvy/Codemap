package dev.codemap.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the dependency rule: {@code codemap-core} depends on no other module in
 * this project.
 *
 * <p>The rule is what keeps the pipeline stages independently testable, and it is
 * the kind of thing that erodes silently — one convenient import and the boundary
 * is gone with nothing to notice it. Asserting on source imports catches that at
 * the moment it happens, and reports the offending file.
 */
class ModuleBoundaryTest {

    private static final String SOURCE_DIRECTORY = "src/main/java";
    private static final String JAVA_SUFFIX = ".java";
    private static final String IMPORT_PREFIX = "import ";

    private static final List<String> FORBIDDEN_PACKAGES = List.of(
            "dev.codemap.cli",
            "dev.codemap.render",
            "dev.codemap.ai");

    @Test
    @DisplayName("core imports nothing from cli, render, or ai")
    void coreDoesNotDependOnOtherModules() throws IOException {
        Path sourceRoot = Path.of(SOURCE_DIRECTORY);

        assertThat(sourceRoot)
                .as("core source directory must exist for this guard to mean anything")
                .isDirectory();

        try (Stream<Path> sources = Files.walk(sourceRoot)) {
            List<Path> javaFiles = sources
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(JAVA_SUFFIX))
                    .toList();

            assertThat(javaFiles).as("core must contain sources to guard").isNotEmpty();

            for (Path javaFile : javaFiles) {
                assertNoForbiddenImports(javaFile);
            }
        }
    }

    private void assertNoForbiddenImports(Path javaFile) throws IOException {
        List<String> offendingImports = Files.readAllLines(javaFile).stream()
                .map(String::strip)
                .filter(line -> line.startsWith(IMPORT_PREFIX))
                .filter(this::importsForbiddenPackage)
                .toList();

        assertThat(offendingImports)
                .as("%s must not import another codemap module", javaFile)
                .isEmpty();
    }

    private boolean importsForbiddenPackage(String importLine) {
        return FORBIDDEN_PACKAGES.stream().anyMatch(importLine::contains);
    }
}
