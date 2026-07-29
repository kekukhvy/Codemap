package dev.codemap.core.parse;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A source file held in memory, sliceable by line range.
 *
 * <p>The file is read once and reused for every declaration in it: the parser
 * needs the whole text, and each indexed method needs its own slice, so re-reading
 * per method would multiply I/O by the number of methods in the file.
 */
final class SourceText {

    private static final String LINE_SEPARATOR = "\n";

    private final String content;
    private final List<String> lines;

    private SourceText(String content) {
        this.content = content;
        this.lines = content.lines().toList();
    }

    /**
     * Reads a file as UTF-8, falling back to ISO-8859-1 for non-UTF-8 sources.
     *
     * <p>The fallback matters on older codebases: a single file saved in a legacy
     * encoding would otherwise be dropped from the map for a reason that has
     * nothing to do with its code.
     *
     * @param file the file to read
     * @return the text, or {@code null} when the file cannot be read at all
     */
    static SourceText read(Path file) {
        try {
            return new SourceText(Files.readString(file));
        } catch (MalformedInputException e) {
            return readWithFallbackCharset(file);
        } catch (IOException e) {
            return null;
        }
    }

    private static SourceText readWithFallbackCharset(Path file) {
        try {
            return new SourceText(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            return null;
        }
    }

    String content() {
        return content;
    }

    /**
     * Extracts an inclusive range of lines.
     *
     * <p>Line numbers come from the parser and are 1-based. Out-of-range values are
     * clamped rather than rejected, so an unexpected position yields a shorter
     * snippet instead of ending the run.
     *
     * @param lineStart first line, 1-based and inclusive
     * @param lineEnd last line, inclusive
     * @return the requested lines joined by newlines
     */
    String slice(int lineStart, int lineEnd) {
        int from = Math.max(1, lineStart) - 1;
        int to = Math.min(lines.size(), lineEnd);
        if (from >= to) {
            return "";
        }
        return String.join(LINE_SEPARATOR, lines.subList(from, to));
    }
}
