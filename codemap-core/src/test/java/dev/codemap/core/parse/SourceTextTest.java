package dev.codemap.core.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link SourceText} directly: the encoding fallback and the line
 * slicing used to embed method source into the index.
 */
class SourceTextTest {

    private static final String FILE_NAME = "Source.java";
    private static final String ISO_8859_1_ONLY_CONTENT = "String label = \"café\";";
    private static final String MISSING_FILE_NAME = "DoesNotExist.java";

    private static final String LINE_1 = "line one";
    private static final String LINE_2 = "line two";
    private static final String LINE_3 = "line three";
    private static final String THREE_LINE_CONTENT = LINE_1 + "\n" + LINE_2 + "\n" + LINE_3;

    private static final int FIRST_LINE = 1;
    private static final int SECOND_LINE = 2;
    private static final int THIRD_LINE = 3;
    private static final int BEFORE_FIRST_LINE = 0;
    private static final int WAY_PAST_LAST_LINE = 100;
    private static final int NEGATIVE_LINE = -5;

    @TempDir
    Path projectRoot;

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("reads a UTF-8 file as-is")
        void readsUtf8File() throws IOException {
            Path file = writeBytes(FILE_NAME, "café".getBytes(StandardCharsets.UTF_8));

            SourceText text = SourceText.read(file);

            assertThat(text.content()).isEqualTo("café");
        }

        @Test
        @DisplayName("falls back to ISO-8859-1 when the bytes are not valid UTF-8")
        void fallsBackToIso88591ForNonUtf8Bytes() throws IOException {
            byte[] latin1Bytes = ISO_8859_1_ONLY_CONTENT.getBytes(StandardCharsets.ISO_8859_1);
            Path file = writeBytes(FILE_NAME, latin1Bytes);

            SourceText text = SourceText.read(file);

            assertThat(text).as("a legacy-encoded file must still be readable").isNotNull();
            assertThat(text.content()).isEqualTo(ISO_8859_1_ONLY_CONTENT);
        }

        @Test
        @DisplayName("returns null for a file that cannot be read at all")
        void returnsNullForMissingFile() {
            Path missing = projectRoot.resolve(MISSING_FILE_NAME);

            SourceText text = SourceText.read(missing);

            assertThat(text).isNull();
        }
    }

    @Nested
    @DisplayName("slicing")
    class Slicing {

        @Test
        @DisplayName("returns the requested inclusive line range")
        void returnsRequestedRange() throws IOException {
            SourceText text = SourceText.read(writeBytes(FILE_NAME, THREE_LINE_CONTENT.getBytes(StandardCharsets.UTF_8)));

            assertThat(text.slice(FIRST_LINE, SECOND_LINE)).isEqualTo(LINE_1 + "\n" + LINE_2);
        }

        @Test
        @DisplayName("clamps a lineStart below 1 instead of rejecting it")
        void clampsLineStartBelowOne() throws IOException {
            SourceText text = SourceText.read(writeBytes(FILE_NAME, THREE_LINE_CONTENT.getBytes(StandardCharsets.UTF_8)));

            assertThat(text.slice(BEFORE_FIRST_LINE, FIRST_LINE)).isEqualTo(LINE_1);
            assertThat(text.slice(NEGATIVE_LINE, FIRST_LINE)).isEqualTo(LINE_1);
        }

        @Test
        @DisplayName("clamps a lineEnd past the last line instead of throwing")
        void clampsLineEndPastLastLine() throws IOException {
            SourceText text = SourceText.read(writeBytes(FILE_NAME, THREE_LINE_CONTENT.getBytes(StandardCharsets.UTF_8)));

            assertThat(text.slice(SECOND_LINE, WAY_PAST_LAST_LINE)).isEqualTo(LINE_2 + "\n" + LINE_3);
        }

        @Test
        @DisplayName("returns an empty string when the range is inverted or out of bounds")
        void returnsEmptyForInvertedRange() throws IOException {
            SourceText text = SourceText.read(writeBytes(FILE_NAME, THREE_LINE_CONTENT.getBytes(StandardCharsets.UTF_8)));

            assertThat(text.slice(THIRD_LINE, FIRST_LINE)).isEmpty();
            assertThat(text.slice(WAY_PAST_LAST_LINE, WAY_PAST_LAST_LINE + FIRST_LINE)).isEmpty();
        }
    }

    private Path writeBytes(String fileName, byte[] content) throws IOException {
        Path file = projectRoot.resolve(fileName);
        Files.write(file, content);
        return file;
    }
}
