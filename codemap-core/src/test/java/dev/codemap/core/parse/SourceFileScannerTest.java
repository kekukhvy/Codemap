package dev.codemap.core.parse;

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

/**
 * Exercises {@link SourceFileScanner}: which files under a source root are
 * picked up, and which are excluded before parsing ever sees them.
 */
class SourceFileScannerTest {

    private static final String MODULE_ID = "app";
    private static final String MODULE_NAME = "app";
    private static final String MODULE_PATH = "";
    private static final String MAIN_SOURCES = "src/main/java";
    private static final String PACKAGE_PATH = "com/example";

    private static final String PRODUCTION_CLASS = "Service.java";
    private static final String PACKAGE_INFO = "package-info.java";
    private static final String MODULE_INFO = "module-info.java";
    private static final String NESTED_TEST_CLASS = "HelperTest.java";
    private static final String SOME_JAVA_CONTENT = "package com.example;\npublic class X {}\n";

    @TempDir
    Path projectRoot;

    private final SourceFileScanner scanner = new SourceFileScanner();

    @Nested
    @DisplayName("non-type files")
    class NonTypeFiles {

        @Test
        @DisplayName("excludes package-info.java, which declares no indexable type")
        void excludesPackageInfo() throws IOException {
            writeSourceFile(PACKAGE_PATH, PRODUCTION_CLASS);
            writeSourceFile(PACKAGE_PATH, PACKAGE_INFO);

            List<String> files = scan();

            assertThat(files).anyMatch(file -> file.endsWith(PRODUCTION_CLASS));
            assertThat(files).noneMatch(file -> file.endsWith(PACKAGE_INFO));
        }

        @Test
        @DisplayName("excludes module-info.java, which declares no indexable type")
        void excludesModuleInfo() throws IOException {
            writeSourceFile("", MODULE_INFO);
            writeSourceFile(PACKAGE_PATH, PRODUCTION_CLASS);

            List<String> files = scan();

            assertThat(files).noneMatch(file -> file.endsWith(MODULE_INFO));
        }
    }

    @Nested
    @DisplayName("nested test directories")
    class NestedTestDirectories {

        @Test
        @DisplayName("excludes a test directory nested underneath a production source root")
        void excludesNestedTestDirectory() throws IOException {
            writeSourceFile(PACKAGE_PATH, PRODUCTION_CLASS);
            writeSourceFile(PACKAGE_PATH + "/src/test/java", NESTED_TEST_CLASS);

            List<String> files = scan();

            assertThat(files).anyMatch(file -> file.endsWith(PRODUCTION_CLASS));
            assertThat(files).noneMatch(file -> file.contains("src/test"));
        }
    }

    @Nested
    @DisplayName("missing source roots")
    class MissingSourceRoots {

        @Test
        @DisplayName("returns no files for a declared source root that does not exist on disk")
        void returnsNoFilesForMissingRoot() {
            IndexedModule module = new IndexedModule(MODULE_ID, MODULE_NAME, MODULE_PATH, List.of(MAIN_SOURCES));

            List<String> files = scanner.scan(projectRoot, module);

            assertThat(files).isEmpty();
        }
    }

    private List<String> scan() {
        IndexedModule module = new IndexedModule(MODULE_ID, MODULE_NAME, MODULE_PATH, List.of(MAIN_SOURCES));
        return scanner.scan(projectRoot, module);
    }

    private void writeSourceFile(String packagePath, String fileName) throws IOException {
        Path directory = projectRoot.resolve(MAIN_SOURCES).resolve(packagePath);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(fileName), SOME_JAVA_CONTENT);
    }
}
