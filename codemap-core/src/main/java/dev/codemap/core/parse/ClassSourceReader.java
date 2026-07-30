package dev.codemap.core.parse;

import java.nio.file.Path;

/**
 * Reads a type's verbatim declaration text on demand.
 *
 * <p>Unlike a method's {@code source}, class source is not stored on the index
 * model — spec 007 §5.2 keeps it view-model-only so {@code index.json} does
 * not double in size by carrying every class body next to every method body.
 * The report still needs the text embedded (it runs over {@code file://}), so
 * {@code codemap-render} reads it at view-projection time through this class,
 * reusing the same {@link SourceText} slicing the parser uses for methods.
 *
 * <p>Degrades to an empty string rather than throwing: an unreadable file or an
 * invalid line range must not fail report generation.
 */
public final class ClassSourceReader {

    private ClassSourceReader() {
    }

    /**
     * Reads the text of one declaration, sliced from its file.
     *
     * @param projectRoot absolute project root, as recorded on the index
     * @param relativeFile source file, relative to {@code projectRoot}
     * @param lineStart first line of the declaration, 1-based and inclusive
     * @param lineEnd last line of the declaration, inclusive
     * @return the sliced text, or an empty string when the file cannot be read
     *         or the range does not resolve to any lines
     */
    public static String read(Path projectRoot, String relativeFile, int lineStart, int lineEnd) {
        SourceText sourceText = SourceText.read(projectRoot.resolve(relativeFile));
        if (sourceText == null) {
            return "";
        }
        return sourceText.slice(lineStart, lineEnd);
    }
}
