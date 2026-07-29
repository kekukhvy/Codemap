package dev.codemap.core.parse;

import dev.codemap.core.ProjectPaths;
import dev.codemap.core.discovery.SourceRootResolver;
import dev.codemap.core.model.IndexedModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Lists the Java files belonging to a module.
 *
 * <p>Filtering happens here rather than during parsing so an excluded file is
 * never opened at all — the cheapest way to honour the rule that test and
 * generated sources stay out of the index.
 */
public final class SourceFileScanner {

    private static final Logger log = LoggerFactory.getLogger(SourceFileScanner.class);

    private static final String JAVA_SUFFIX = ".java";

    /**
     * Files that are syntactically Java but carry no indexable type.
     *
     * <p>{@code package-info} and {@code module-info} declare metadata, not code.
     * Parsing them yields nothing and their absence confuses nothing.
     */
    private static final List<String> NON_TYPE_FILES = List.of("package-info.java", "module-info.java");

    /**
     * Finds every production Java file in a module.
     *
     * @param projectRoot project directory, used to make results relative
     * @param module the module to scan
     * @return file paths relative to {@code projectRoot}, in stable sorted order
     */
    public List<String> scan(Path projectRoot, IndexedModule module) {
        List<String> files = new ArrayList<>();
        for (String sourceRoot : module.sourceRoots()) {
            files.addAll(scanRoot(projectRoot, projectRoot.resolve(sourceRoot)));
        }
        return List.copyOf(files);
    }

    private List<String> scanRoot(Path projectRoot, Path sourceRoot) {
        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(SourceFileScanner::isIndexableJavaFile)
                    .map(path -> ProjectPaths.relative(projectRoot, path))
                    .filter(SourceRootResolver::isProductionPath)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.warn("Could not scan source root {}: {}", sourceRoot, e.getMessage());
            return List.of();
        }
    }

    private static boolean isIndexableJavaFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(JAVA_SUFFIX) && !NON_TYPE_FILES.contains(fileName);
    }
}
