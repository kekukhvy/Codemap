package dev.codemap.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Objects;

/**
 * What one indexing run produced, and what it could not.
 *
 * <p>The skipped-file list is the point of this type. Codemap degrades rather than
 * fails on a source file it cannot parse, and a silent partial result is worse
 * than a loud one — the reader has to know the map is incomplete, and which part
 * is missing.
 *
 * @param filesScanned source files considered
 * @param filesParsed files successfully parsed
 * @param classesIndexed types recorded
 * @param methodsIndexed methods and constructors recorded
 * @param skipped files that could not be parsed, with the reason
 */
public record IndexStatistics(
        int filesScanned,
        int filesParsed,
        int classesIndexed,
        int methodsIndexed,
        List<SkippedFile> skipped) {

    public IndexStatistics {
        skipped = List.copyOf(Objects.requireNonNull(skipped, "skipped"));
    }

    public static IndexStatistics empty() {
        return new IndexStatistics(0, 0, 0, 0, List.of());
    }

    /** Whether any file failed to parse. */
    @JsonIgnore
    public boolean hasSkippedFiles() {
        return !skipped.isEmpty();
    }

    /**
     * A file left out of the index, and why.
     *
     * @param file path relative to the project root
     * @param reason short human-readable explanation
     */
    public record SkippedFile(String file, String reason) {

        public SkippedFile {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
