package dev.codemap.core.parse;

import dev.codemap.core.model.IndexedClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the blank-line gap limit of {@link OrphanJavadocIndex} end to end,
 * through {@link JavaSourceParser}: a detached Javadoc block is recovered up to
 * the allowed gap, and left unattached beyond it.
 *
 * <p>Driven through the parser rather than calling {@code OrphanJavadocIndex}
 * directly, since it is package-private supporting logic with no meaningful
 * behaviour apart from what a real parse produces.
 */
class OrphanJavadocIndexTest {

    private static final String FILE_NAME = "Detached.java";
    private static final String MODULE_ID = "app";
    private static final String CLASS_NAME = "Detached";
    private static final String SUMMARY = "Documented despite the gap.";

    /** Matches {@code OrphanJavadocIndex.MAX_BLANK_LINES}: the widest gap still recovered. */
    private static final int MAX_RECOVERABLE_BLANK_LINES = 3;

    private final JavaSourceParser parser = JavaSourceParser.withLatestLanguageLevel();

    @TempDir
    Path projectRoot;

    @Test
    @DisplayName("recovers a Javadoc block separated from its type by the maximum allowed blank lines")
    void recoversJavadocAtMaximumAllowedGap() throws IOException {
        IndexedClass indexed = parseWithGap(MAX_RECOVERABLE_BLANK_LINES);

        assertThat(indexed.javadoc()).isEqualTo(SUMMARY);
    }

    @Test
    @DisplayName("leaves Javadoc unattached once the gap exceeds the allowed number of blank lines")
    void leavesJavadocUnattachedBeyondMaximumGap() throws IOException {
        IndexedClass indexed = parseWithGap(MAX_RECOVERABLE_BLANK_LINES + 1);

        assertThat(indexed.javadoc()).isNull();
    }

    private IndexedClass parseWithGap(int blankLines) throws IOException {
        String gap = "\n".repeat(blankLines);
        String source = """
                package com.example;

                /**
                 * %s
                 */
                %s\
                public class %s {
                }
                """.formatted(SUMMARY, gap, CLASS_NAME);

        Files.writeString(projectRoot.resolve(FILE_NAME), source);
        ParsedFile parsed = parser.parse(projectRoot, FILE_NAME, MODULE_ID);

        return parsed.classes().stream()
                .filter(indexedClass -> indexedClass.simpleName().equals(CLASS_NAME))
                .findFirst()
                .orElseThrow();
    }
}
