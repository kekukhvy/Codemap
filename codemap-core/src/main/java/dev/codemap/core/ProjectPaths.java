package dev.codemap.core;

import java.io.File;
import java.nio.file.Path;

/**
 * Path conventions shared across the pipeline.
 *
 * <p>Every path stored in the index is relative to the project root and uses
 * {@code /} as its separator, whatever the host platform. That keeps an index
 * written on one machine readable on another, and lets the report link to files
 * without rewriting them.
 */
public final class ProjectPaths {

    private static final char INDEX_SEPARATOR = '/';

    private ProjectPaths() {
    }

    /**
     * Converts an absolute path to the form stored in the index.
     *
     * @param projectRoot the analysed project's directory
     * @param path a path inside that project
     * @return the path relative to the root, separated by {@code /}
     */
    public static String relative(Path projectRoot, Path path) {
        return projectRoot.relativize(path).toString().replace(File.separatorChar, INDEX_SEPARATOR);
    }
}
