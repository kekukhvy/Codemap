package dev.codemap.core.parse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.InvalidPathException;
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
 * <p>Degrades to an empty string rather than throwing: an unreadable file, a
 * malformed path, or an invalid line range must not fail report generation.
 *
 * <p>Reads are confined to the project root. The path comes from
 * {@code index.json}, which is a cache file rather than reviewed code, so it is
 * treated as untrusted input — see {@code resolveInsideRoot}.
 */
public final class ClassSourceReader {

    private static final Logger log = LoggerFactory.getLogger(ClassSourceReader.class);

    private ClassSourceReader() {
    }

    /**
     * Reads the text of one declaration, sliced from its file.
     *
     * @param projectRoot absolute project root, as recorded on the index
     * @param relativeFile source file, relative to {@code projectRoot}
     * @param lineStart first line of the declaration, 1-based and inclusive
     * @param lineEnd last line of the declaration, inclusive
     * @return the sliced text, or an empty string when the file resolves outside
     *         {@code projectRoot}, cannot be read, or the range does not resolve
     *         to any lines
     */
    public static String read(Path projectRoot, String relativeFile, int lineStart, int lineEnd) {
        Path file = resolveInsideRoot(projectRoot, relativeFile);
        if (file == null) {
            return "";
        }
        SourceText sourceText = SourceText.read(file);
        if (sourceText == null) {
            log.warn("Could not read source of {}; its box will render without a body", relativeFile);
            return "";
        }
        return sourceText.slice(lineStart, lineEnd);
    }

    /**
     * Resolves {@code relativeFile} against the project root, refusing anything
     * that lands outside it.
     *
     * <p>{@link Path#resolve} alone is not enough: it neither normalises
     * {@code ..} segments nor rejects absolute paths, and an absolute argument
     * discards the root entirely.
     *
     * <p>{@link Path#normalize} is not enough either — it is purely lexical, so
     * a path that looks contained can still be a symlink pointing anywhere. That
     * needs no tampering to reach: a repository can simply contain
     * {@code Config.java} as a link to {@code ~/.ssh/id_rsa}, and the file walk
     * enumerates symlinked files. The reviewer then attaches a report with the
     * key inside it to the pull request, so the secret leaves by hand and the
     * report's network restrictions never come into it. Resolution therefore
     * goes through {@link Path#toRealPath}, which follows links before the
     * containment check.
     *
     * @return the resolved file, or {@code null} when it escapes the root, does
     *         not exist, or the path is malformed
     */
    private static Path resolveInsideRoot(Path projectRoot, String relativeFile) {
        try {
            Path root = projectRoot.toRealPath();
            Path resolved = root.resolve(relativeFile).toRealPath();
            if (!resolved.startsWith(root)) {
                log.warn("Refusing to read {}: it resolves outside the project root", relativeFile);
                return null;
            }
            return resolved;
        } catch (InvalidPathException e) {
            log.warn("Refusing to read malformed path {}: {}", relativeFile, e.getMessage());
            return null;
        } catch (IOException e) {
            // toRealPath also fails for a file that simply is not there, which is
            // the ordinary "source moved since the index was written" case.
            log.debug("Could not resolve {}: {}", relativeFile, e.getMessage());
            return null;
        }
    }
}
