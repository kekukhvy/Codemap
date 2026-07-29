package dev.codemap.core.discovery;

import dev.codemap.core.ProjectPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Locates the production source roots of a module.
 *
 * <p>Test sources are never returned. That is a project-level decision, not a
 * filter applied later: the map answers what an application does in production,
 * and tests are unreachable from any entry point, invert the call direction, and
 * typically outnumber the code they cover. Excluding them here means no downstream
 * stage has to know they exist.
 */
public final class SourceRootResolver {

    /** Conventional production source roots, in preference order. */
    private static final List<String> PRODUCTION_ROOTS = List.of(
            "src/main/java",
            "src/java",
            "java");

    /**
     * Path segments that mark a directory as test or generated code.
     *
     * <p>Generated sources are excluded because they inflate the map without
     * telling a reader anything about the system that was written.
     */
    private static final List<String> EXCLUDED_SEGMENTS = List.of(
            "src/test",
            "src/it",
            "src/integrationtest",
            "src/integration-test",
            "src/testfixtures",
            "src/test-fixtures",
            "build/generated",
            "target/generated",
            "generated-sources");

    /**
     * Resolves the production source roots of one module.
     *
     * @param projectRoot project directory, used to make results relative
     * @param moduleDirectory the module to inspect
     * @return source roots relative to {@code projectRoot}, empty when the module
     *         holds no production code
     */
    public List<String> resolve(Path projectRoot, Path moduleDirectory) {
        return PRODUCTION_ROOTS.stream()
                .map(moduleDirectory::resolve)
                .filter(Files::isDirectory)
                .map(root -> ProjectPaths.relative(projectRoot, root))
                .filter(SourceRootResolver::isProductionPath)
                .toList();
    }

    /**
     * Whether a path belongs to production code.
     *
     * <p>Applied to every candidate directory and file, so a nested test or
     * generated directory is rejected even when it sits below a production root.
     *
     * @param relativePath path relative to the project root, using {@code /}
     * @return {@code true} when the path is production source
     */
    public static boolean isProductionPath(String relativePath) {
        String normalised = relativePath.toLowerCase(java.util.Locale.ROOT);
        return EXCLUDED_SEGMENTS.stream().noneMatch(normalised::contains);
    }
}
