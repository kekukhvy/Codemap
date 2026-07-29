package dev.codemap.core.discovery;

import dev.codemap.core.ProjectPaths;
import dev.codemap.core.model.IndexedModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the build modules of a project.
 *
 * <p>Three strategies are tried in order of how much they can be trusted:
 * a Gradle {@code settings} file, Maven {@code pom.xml} modules, then a filesystem
 * scan for source roots. The build file is preferred because it states the intended
 * structure, including modules that exist but hold no code yet — a filesystem scan
 * cannot see those, and dropping them would hide part of the project.
 */
public final class ModuleDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ModuleDiscovery.class);

    private static final String SETTINGS_GRADLE = "settings.gradle";
    private static final String SETTINGS_GRADLE_KTS = "settings.gradle.kts";
    private static final String POM_XML = "pom.xml";

    private static final String MAIN_SOURCE_ROOT = "src/main/java";
    private static final String ROOT_MODULE_PATH = "";

    /** Matches {@code include 'a:b'} and {@code include("a:b")}, one path per group. */
    private static final Pattern GRADLE_INCLUDE = Pattern.compile(
            "include\\s*\\(?\\s*((?:[\"'][^\"']+[\"']\\s*,?\\s*)+)\\)?");
    private static final Pattern QUOTED_PATH = Pattern.compile("[\"']([^\"']+)[\"']");
    private static final Pattern MAVEN_MODULE = Pattern.compile("<module>\\s*([^<]+?)\\s*</module>");

    private static final String GRADLE_PATH_SEPARATOR = ":";
    private static final String PATH_SEPARATOR = "/";

    /** How deep a filesystem scan looks for source roots when there is no build file. */
    private static final int MAX_SCAN_DEPTH = 4;

    private final SourceRootResolver sourceRootResolver;

    public ModuleDiscovery() {
        this(new SourceRootResolver());
    }

    ModuleDiscovery(SourceRootResolver sourceRootResolver) {
        this.sourceRootResolver = sourceRootResolver;
    }

    /**
     * Discovers the modules of a project.
     *
     * <p>Always returns at least one module: a project with no recognisable build
     * file and no nested source roots is treated as a single module rooted at the
     * project directory, which is the right answer for a plain source tree.
     *
     * @param projectRoot validated project directory
     * @return modules in declaration order, each with its production source roots
     */
    public List<IndexedModule> discover(Path projectRoot) {
        List<String> declaredPaths = readDeclaredModulePaths(projectRoot);

        if (declaredPaths.isEmpty()) {
            log.debug("No build file declared modules; scanning for source roots");
            declaredPaths = scanForModulePaths(projectRoot);
        }

        List<IndexedModule> modules = new ArrayList<>();
        for (String modulePath : declaredPaths) {
            toModule(projectRoot, modulePath).ifPresent(modules::add);
        }

        if (modules.isEmpty()) {
            log.debug("Nothing discovered; treating the project as a single module");
            modules.add(singleModule(projectRoot));
        }
        return List.copyOf(modules);
    }

    private java.util.Optional<IndexedModule> toModule(Path projectRoot, String modulePath) {
        Path moduleDirectory = modulePath.isEmpty() ? projectRoot : projectRoot.resolve(modulePath);
        if (!Files.isDirectory(moduleDirectory)) {
            log.warn("Declared module has no directory, skipping: {}", modulePath);
            return java.util.Optional.empty();
        }
        List<String> sourceRoots = sourceRootResolver.resolve(projectRoot, moduleDirectory);
        return java.util.Optional.of(new IndexedModule(
                moduleId(modulePath),
                moduleName(projectRoot, modulePath),
                modulePath,
                sourceRoots));
    }

    private IndexedModule singleModule(Path projectRoot) {
        return new IndexedModule(
                moduleId(ROOT_MODULE_PATH),
                projectRoot.getFileName() == null ? "root" : projectRoot.getFileName().toString(),
                ROOT_MODULE_PATH,
                sourceRootResolver.resolve(projectRoot, projectRoot));
    }

    /** Reads module paths from a Gradle settings file or a Maven pom, if either exists. */
    private List<String> readDeclaredModulePaths(Path projectRoot) {
        for (String settingsFile : List.of(SETTINGS_GRADLE, SETTINGS_GRADLE_KTS)) {
            Path candidate = projectRoot.resolve(settingsFile);
            if (Files.isRegularFile(candidate)) {
                return withRootIfItHasSources(projectRoot, parseGradleIncludes(candidate));
            }
        }
        Path pom = projectRoot.resolve(POM_XML);
        if (Files.isRegularFile(pom)) {
            return withRootIfItHasSources(projectRoot, parseMavenModules(pom));
        }
        return List.of();
    }

    /**
     * Adds the project directory itself when it holds source.
     *
     * <p>A Gradle root project commonly declares subprojects and also contains code
     * of its own; that code belongs to a module, so the root is included as one.
     */
    private List<String> withRootIfItHasSources(Path projectRoot, List<String> declared) {
        if (declared.isEmpty()) {
            return declared;
        }
        boolean rootHasSources = Files.isDirectory(projectRoot.resolve(MAIN_SOURCE_ROOT));
        if (!rootHasSources) {
            return declared;
        }
        List<String> withRoot = new ArrayList<>();
        withRoot.add(ROOT_MODULE_PATH);
        withRoot.addAll(declared);
        return withRoot;
    }

    private List<String> parseGradleIncludes(Path settingsFile) {
        String content = readOrEmpty(settingsFile);
        Set<String> paths = new LinkedHashSet<>();

        Matcher includes = GRADLE_INCLUDE.matcher(content);
        while (includes.find()) {
            Matcher quoted = QUOTED_PATH.matcher(includes.group(1));
            while (quoted.find()) {
                paths.add(quoted.group(1).replace(GRADLE_PATH_SEPARATOR, PATH_SEPARATOR));
            }
        }
        log.debug("Gradle settings declared {} module(s)", paths.size());
        return List.copyOf(paths);
    }

    private List<String> parseMavenModules(Path pomFile) {
        String content = readOrEmpty(pomFile);
        Set<String> paths = new LinkedHashSet<>();

        Matcher modules = MAVEN_MODULE.matcher(content);
        while (modules.find()) {
            paths.add(modules.group(1));
        }
        log.debug("Maven pom declared {} module(s)", paths.size());
        return List.copyOf(paths);
    }

    /** Locates directories containing a production source root, for build-file-less projects. */
    private List<String> scanForModulePaths(Path projectRoot) {
        try (Stream<Path> candidates = Files.walk(projectRoot, MAX_SCAN_DEPTH)) {
            return candidates
                    .filter(Files::isDirectory)
                    .filter(path -> path.endsWith(Path.of(MAIN_SOURCE_ROOT)))
                    .map(path -> relativeModulePath(projectRoot, path))
                    .distinct()
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.warn("Could not scan {} for source roots: {}", projectRoot, e.getMessage());
            return List.of();
        }
    }

    /** Walks up from {@code src/main/java} to the directory that owns it. */
    private String relativeModulePath(Path projectRoot, Path sourceRoot) {
        Path moduleDirectory = sourceRoot.getParent().getParent().getParent();
        return ProjectPaths.relative(projectRoot, moduleDirectory);
    }

    private String readOrEmpty(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            log.warn("Could not read {}: {}", file, e.getMessage());
            return "";
        }
    }

    private String moduleId(String modulePath) {
        return modulePath.isEmpty() ? "." : modulePath;
    }

    private String moduleName(Path projectRoot, String modulePath) {
        if (modulePath.isEmpty()) {
            Path name = projectRoot.getFileName();
            return name == null ? "root" : name.toString();
        }
        int lastSeparator = modulePath.lastIndexOf(PATH_SEPARATOR);
        return lastSeparator < 0 ? modulePath : modulePath.substring(lastSeparator + 1);
    }
}
