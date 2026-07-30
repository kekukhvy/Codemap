package dev.codemap.core.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ClassSourceReader} embeds a class's verbatim declaration text for the
 * report's side panel (spec 007 §5.2), reading on demand rather than storing
 * the text on {@link dev.codemap.core.model.IndexedClass} so {@code index.json}
 * does not double in size.
 */
class ClassSourceReaderTest {

    private static final String FILE_NAME = "Sample.java";

    @TempDir
    Path projectRoot;

    @Test
    @DisplayName("reads the verbatim declaration text for a valid range")
    void readsDeclarationText() throws IOException {
        String content = "public class Sample {\n    void run() {\n    }\n}\n";
        Files.writeString(projectRoot.resolve(FILE_NAME), content);

        String source = ClassSourceReader.read(projectRoot, FILE_NAME, 1, 4);

        assertThat(source).isEqualTo("public class Sample {\n    void run() {\n    }\n}");
    }

    @Test
    @DisplayName("degrades to an empty string when the file cannot be read")
    void degradesForMissingFile() {
        String source = ClassSourceReader.read(projectRoot, "DoesNotExist.java", 1, 4);

        assertThat(source).isEmpty();
    }

    @Test
    @DisplayName("degrades to an empty string when the line range does not resolve to any lines")
    void degradesForInvalidRange() throws IOException {
        Files.writeString(projectRoot.resolve(FILE_NAME), "public class Sample {\n}\n");

        String source = ClassSourceReader.read(projectRoot, FILE_NAME, 50, 60);

        assertThat(source).isEmpty();
    }

    @Test
    @DisplayName("refuses a relative path that escapes the project root")
    void refusesTraversalOutsideRoot() throws IOException {
        Path outside = projectRoot.getParent().resolve("outside-secret.txt");
        Files.writeString(outside, "a-secret-the-report-must-not-embed\n");

        String source = ClassSourceReader.read(projectRoot, "../" + outside.getFileName(), 1, 1);

        assertThat(source).isEmpty();
    }

    @Test
    @DisplayName("refuses an absolute path, which would discard the project root entirely")
    void refusesAbsolutePath() throws IOException {
        Path outside = projectRoot.getParent().resolve("outside-absolute.txt");
        Files.writeString(outside, "another-secret\n");

        String source = ClassSourceReader.read(projectRoot, outside.toAbsolutePath().toString(), 1, 1);

        assertThat(source).isEmpty();
    }

    @Test
    @DisplayName("degrades rather than throwing when the path itself is malformed")
    void degradesForMalformedPath() {
        String nameWithNulByte = "Sample\u0000.java";

        String source = ClassSourceReader.read(projectRoot, nameWithNulByte, 1, 4);

        assertThat(source).isEmpty();
    }

    @Test
    @DisplayName("still reads a file in a subdirectory of the project root")
    void readsNestedFile() throws IOException {
        Path nested = projectRoot.resolve("src/main/java");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve(FILE_NAME), "class Sample {\n}\n");

        String source = ClassSourceReader.read(projectRoot, "src/main/java/" + FILE_NAME, 1, 2);

        assertThat(source).isEqualTo("class Sample {\n}");
    }
}
