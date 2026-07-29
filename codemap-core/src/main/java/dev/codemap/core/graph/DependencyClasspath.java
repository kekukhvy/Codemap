package dev.codemap.core.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Locates the jars an analysed project compiles against.
 *
 * <p>Without them the symbol solver cannot tell a third-party call from a broken
 * one: {@code logger.info(...)} fails to resolve for the same reason a genuine
 * mistake would, and the call graph fills with dead-end edges naming methods that
 * belong to libraries.
 *
 * <p>Jars are discovered from the build tool's local cache rather than by
 * building the project — Codemap never compiles or executes what it analyses. The
 * consequence is that a project whose dependencies have never been downloaded
 * resolves fewer calls; that degrades the graph rather than breaking it.
 */
final class DependencyClasspath {

    private static final Logger log = LoggerFactory.getLogger(DependencyClasspath.class);

    private static final String JAR_SUFFIX = ".jar";
    private static final String SOURCES_SUFFIX = "-sources.jar";
    private static final String JAVADOC_SUFFIX = "-javadoc.jar";

    /** Local caches, relative to the user's home directory. */
    private static final List<String> CACHE_LOCATIONS = List.of(
            ".gradle/caches/modules-2/files-2.1",
            ".m2/repository");

    /**
     * Depth limit for the cache walk.
     *
     * <p>Both layouts nest a jar a handful of levels below the cache root
     * (group/artifact/version/hash). Bounding the walk keeps a large shared cache
     * from dominating start-up.
     */
    private static final int MAX_CACHE_DEPTH = 8;

    /** Upper bound on jars handed to the solver, so a huge cache cannot stall a run. */
    private static final int MAX_JARS = 400;

    private DependencyClasspath() {
    }

    /**
     * Finds candidate dependency jars for a project.
     *
     * <p>Only jars whose file name matches a name referenced by the project's build
     * files are returned, so an unrelated library sitting in a shared cache does
     * not join the classpath.
     *
     * @param projectRoot the analysed project
     * @return jar paths, empty when none can be found
     */
    static List<Path> discover(Path projectRoot) {
        List<String> declaredNames = declaredArtifactNames(projectRoot);
        if (declaredNames.isEmpty()) {
            log.debug("No dependency names found in build files; symbol resolution will be source-only");
            return List.of();
        }

        List<Path> jars = new ArrayList<>();
        for (String cacheLocation : CACHE_LOCATIONS) {
            Path cache = Path.of(System.getProperty("user.home")).resolve(cacheLocation);
            if (Files.isDirectory(cache)) {
                collectMatchingJars(cache, declaredNames, jars);
            }
        }
        log.debug("Resolved {} dependency jar(s) for symbol resolution", jars.size());
        return List.copyOf(jars);
    }

    /**
     * Reads artifact names out of the project's build files.
     *
     * <p>Deliberately crude: any {@code group:artifact} token is enough to know
     * which jar names matter. Parsing the build properly would mean running the
     * build tool, which Codemap does not do.
     */
    private static List<String> declaredArtifactNames(Path projectRoot) {
        List<String> names = new ArrayList<>();
        for (Path buildFile : buildFiles(projectRoot)) {
            try {
                for (String line : Files.readAllLines(buildFile)) {
                    addArtifactNames(line, names);
                }
            } catch (IOException e) {
                log.debug("Could not read {}: {}", buildFile, e.getMessage());
            }
        }
        return names.stream().distinct().toList();
    }

    private static void addArtifactNames(String line, List<String> names) {
        java.util.regex.Matcher matcher = ARTIFACT_COORDINATE.matcher(line);
        while (matcher.find()) {
            names.add(matcher.group(2));
        }
        java.util.regex.Matcher maven = MAVEN_ARTIFACT_ID.matcher(line);
        while (maven.find()) {
            names.add(maven.group(1));
        }
    }

    /**
     * Gradle-style {@code group:artifact} inside quotes, with any version form.
     *
     * <p>The version is deliberately not captured: build files commonly write it
     * as an interpolated property ({@code "io.javalin:javalin:${javalinVersion}"}),
     * and only the artifact name is needed to recognise the jar.
     */
    private static final java.util.regex.Pattern ARTIFACT_COORDINATE =
            java.util.regex.Pattern.compile("[\"']([a-zA-Z0-9._-]+):([a-zA-Z0-9._-]+)(?::[^\"']*)?[\"']");

    /** Maven {@code <artifactId>name</artifactId>}. */
    private static final java.util.regex.Pattern MAVEN_ARTIFACT_ID =
            java.util.regex.Pattern.compile("<artifactId>\\s*([a-zA-Z0-9._-]+)\\s*</artifactId>");

    private static List<Path> buildFiles(Path projectRoot) {
        try (Stream<Path> candidates = Files.walk(projectRoot, 3)) {
            return candidates
                    .filter(Files::isRegularFile)
                    .filter(DependencyClasspath::isBuildFile)
                    .toList();
        } catch (IOException e) {
            log.debug("Could not scan {} for build files: {}", projectRoot, e.getMessage());
            return List.of();
        }
    }

    private static boolean isBuildFile(Path path) {
        String name = path.getFileName().toString();
        return name.equals("build.gradle") || name.equals("build.gradle.kts")
                || name.equals("pom.xml") || name.equals("libs.versions.toml")
                || name.equals("gradle.properties");
    }

    private static void collectMatchingJars(Path cache, List<String> declaredNames, List<Path> jars) {
        try (Stream<Path> candidates = Files.walk(cache, MAX_CACHE_DEPTH)) {
            candidates
                    .filter(Files::isRegularFile)
                    .filter(DependencyClasspath::isBinaryJar)
                    .filter(path -> matchesDeclaredName(path, declaredNames))
                    .limit(MAX_JARS - (long) jars.size())
                    .forEach(jars::add);
        } catch (IOException | RuntimeException e) {
            log.debug("Could not scan dependency cache {}: {}", cache, e.getMessage());
        }
    }

    private static boolean isBinaryJar(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(JAR_SUFFIX)
                && !name.endsWith(SOURCES_SUFFIX)
                && !name.endsWith(JAVADOC_SUFFIX);
    }

    private static boolean matchesDeclaredName(Path jar, List<String> declaredNames) {
        String fileName = jar.getFileName().toString();
        return declaredNames.stream().anyMatch(name -> fileName.startsWith(name + "-"));
    }
}
