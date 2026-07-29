package dev.codemap.core.parse;

import dev.codemap.core.model.FileFingerprint;
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
 * Exercises {@link FileFingerprints}: content-based identity for the
 * incremental cache, and its behaviour when a file cannot be read at all.
 */
class FileFingerprintsTest {

    private static final String FILE_NAME = "Fingerprinted.java";
    private static final String MISSING_FILE_NAME = "Missing.java";
    private static final String CONTENT = "public class Fingerprinted {}";
    private static final String OTHER_CONTENT = "public class Fingerprinted { void go() {} }";

    @TempDir
    Path projectRoot;

    @Nested
    @DisplayName("readable files")
    class ReadableFiles {

        @Test
        @DisplayName("produces a stable, non-blank hash for identical content")
        void producesStableHashForIdenticalContent() throws IOException {
            Path file = writeFile(FILE_NAME, CONTENT);

            FileFingerprint first = FileFingerprints.of(file);
            FileFingerprint second = FileFingerprints.of(file);

            assertThat(first.hash()).isNotBlank();
            assertThat(first.matches(second)).isTrue();
        }

        @Test
        @DisplayName("produces a different hash when the content differs")
        void producesDifferentHashForDifferentContent() throws IOException {
            FileFingerprint first = FileFingerprints.of(writeFile(FILE_NAME, CONTENT));
            FileFingerprint second = FileFingerprints.of(writeFile(FILE_NAME, OTHER_CONTENT));

            assertThat(first.matches(second)).isFalse();
        }
    }

    @Nested
    @DisplayName("unreadable files")
    class UnreadableFiles {

        @Test
        @DisplayName("returns a fingerprint that never matches, rather than throwing")
        void returnsNonMatchingFingerprintForMissingFile() {
            Path missing = projectRoot.resolve(MISSING_FILE_NAME);

            FileFingerprint fingerprint = FileFingerprints.of(missing);

            assertThat(fingerprint).isNotNull();
            assertThat(fingerprint.hash()).isNotBlank();
        }

        @Test
        @DisplayName("never matches another unreadable-file fingerprint, so it is always reparsed")
        void unreadableFingerprintNeverMatchesAnotherUnreadableFingerprint() {
            Path missing = projectRoot.resolve(MISSING_FILE_NAME);

            FileFingerprint first = FileFingerprints.of(missing);
            FileFingerprint second = FileFingerprints.of(missing);

            assertThat(first.matches(second))
                    .as("an unreadable file must be reparsed every run, never treated as unchanged")
                    .isFalse();
        }
    }

    private Path writeFile(String fileName, String content) throws IOException {
        Path file = projectRoot.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
