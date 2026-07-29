package dev.codemap.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validation and normalisation rules for {@link CodemapOptions}.
 *
 * <p>Kept separate from the options themselves so the value object stays a plain
 * carrier and every rejection message lives in one place.
 */
final class OptionValidator {

    private static final String MISSING_ROOT = "No project directory given. Pass --root <path>.";
    private static final String ROOT_NOT_FOUND = "Project directory does not exist: %s";
    private static final String ROOT_NOT_A_DIRECTORY = "Project root is not a directory: %s";
    private static final String ROOT_NOT_READABLE = "Project directory is not readable: %s";
    private static final String UNRESOLVABLE_PATH = "Path cannot be resolved: %s";

    private OptionValidator() {
    }

    /**
     * Checks the project root exists, is a directory, and can be read.
     *
     * @param root candidate directory, possibly {@code null}
     * @return the root as an absolute, normalised path
     * @throws InvalidOptionsException if the root is unusable
     */
    static Path requireReadableDirectory(Path root) {
        if (root == null) {
            throw new InvalidOptionsException(MISSING_ROOT);
        }
        Path absolute = toAbsolute(root);
        if (!Files.exists(absolute)) {
            throw new InvalidOptionsException(ROOT_NOT_FOUND.formatted(absolute));
        }
        if (!Files.isDirectory(absolute)) {
            throw new InvalidOptionsException(ROOT_NOT_A_DIRECTORY.formatted(absolute));
        }
        if (!Files.isReadable(absolute)) {
            throw new InvalidOptionsException(ROOT_NOT_READABLE.formatted(absolute));
        }
        return absolute;
    }

    /**
     * Normalises an optional revision, treating blank input as absent.
     *
     * @param revision the requested revision, possibly {@code null}
     * @return the trimmed revision, or {@code null} when none was given
     */
    static String normaliseOptionalRevision(String revision) {
        if (revision == null || revision.isBlank()) {
            return null;
        }
        return revision.trim();
    }

    /**
     * Resolves where the report is written, defaulting to
     * {@code <root>/codemap/report.html}.
     *
     * <p>A relative {@code --out} is resolved against the project root rather than
     * the launch directory, so the same command produces the same file no matter
     * where it was typed.
     *
     * @param output requested location, possibly {@code null}
     * @param root validated project root
     * @return an absolute report path
     */
    static Path resolveOutput(Path output, Path root) {
        if (output == null) {
            return root.resolve(CodemapOptions.OUTPUT_DIRECTORY)
                    .resolve(CodemapOptions.DEFAULT_REPORT_NAME);
        }
        return output.isAbsolute() ? output.normalize() : root.resolve(output).normalize();
    }

    /**
     * Resolves the config file: an explicit path when given, otherwise
     * {@code codemap.yml} in the project root if it exists.
     *
     * <p>An explicit but missing config is <em>not</em> an error here — the config
     * loader reports that, so this stage stays about path shape alone.
     *
     * @param config requested config path, possibly {@code null}
     * @param root validated project root
     * @return an absolute config path, or {@code null} when there is none to load
     */
    static Path resolveConfig(Path config, Path root) {
        if (config != null) {
            return config.isAbsolute() ? config.normalize() : root.resolve(config).normalize();
        }
        Path conventional = root.resolve(CodemapOptions.DEFAULT_CONFIG_NAME);
        return Files.isRegularFile(conventional) ? conventional : null;
    }

    /**
     * Converts to an absolute, symlink-free path where possible.
     *
     * <p>Falls back to plain normalisation when the real path cannot be read, so a
     * permission problem surfaces as a readable message from the caller rather
     * than an {@link IOException} here.
     */
    private static Path toAbsolute(Path path) {
        try {
            return path.toAbsolutePath().normalize();
        } catch (IllegalArgumentException e) {
            throw new InvalidOptionsException(UNRESOLVABLE_PATH.formatted(path));
        }
    }
}
