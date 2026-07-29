package dev.codemap.core.index;

import dev.codemap.core.discovery.ModuleDiscovery;
import dev.codemap.core.graph.CallGraphBuild;
import dev.codemap.core.graph.CallGraphBuilder;
import dev.codemap.core.model.CodeIndex;
import dev.codemap.core.model.FileFingerprint;
import dev.codemap.core.model.IndexStatistics;
import dev.codemap.core.model.IndexedClass;
import dev.codemap.core.model.IndexedMethod;
import dev.codemap.core.model.IndexedModule;
import dev.codemap.core.parse.FileFingerprints;
import dev.codemap.core.parse.JavaSourceParser;
import dev.codemap.core.parse.ParsedFile;
import dev.codemap.core.parse.SourceFileScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link CodeIndex} for a project.
 *
 * <p>Runs the discover-scan-parse sequence and assembles the result. It owns no
 * parsing logic of its own — each step is a separate collaborator, so a parser
 * test needs no module discovery and a discovery test needs no parser.
 */
public final class ProjectIndexer {

    private static final Logger log = LoggerFactory.getLogger(ProjectIndexer.class);

    private final ModuleDiscovery moduleDiscovery;
    private final SourceFileScanner sourceFileScanner;
    private final JavaSourceParser parser;

    public ProjectIndexer() {
        this(new ModuleDiscovery(), new SourceFileScanner(), JavaSourceParser.withLatestLanguageLevel());
    }

    public ProjectIndexer(ModuleDiscovery moduleDiscovery, SourceFileScanner sourceFileScanner, JavaSourceParser parser) {
        this.moduleDiscovery = moduleDiscovery;
        this.sourceFileScanner = sourceFileScanner;
        this.parser = parser;
    }

    /**
     * Indexes a project in full.
     *
     * @param projectRoot validated project directory
     * @return the index, including a record of anything that could not be parsed
     */
    public CodeIndex index(Path projectRoot) {
        Instant startedAt = Instant.now();
        List<IndexedModule> modules = moduleDiscovery.discover(projectRoot);
        log.info("Discovered {} module(s)", modules.size());

        List<IndexedClass> classes = new ArrayList<>();
        List<IndexedMethod> methods = new ArrayList<>();
        List<ParsedFile> parsedFiles = new ArrayList<>();
        Map<String, FileFingerprint> fingerprints = new LinkedHashMap<>();
        List<IndexStatistics.SkippedFile> skipped = new ArrayList<>();
        int filesScanned = 0;

        for (IndexedModule module : modules) {
            List<String> files = sourceFileScanner.scan(projectRoot, module);
            filesScanned += files.size();
            log.debug("Module {} contributes {} file(s)", module.name(), files.size());

            for (String file : files) {
                fingerprints.put(file, FileFingerprints.of(projectRoot.resolve(file)));
                ParsedFile parsed = parser.parse(projectRoot, file, module.id());

                if (parsed.wasSkipped()) {
                    log.warn("Skipped {}: {}", file, parsed.skipReason());
                    skipped.add(new IndexStatistics.SkippedFile(file, parsed.skipReason()));
                    continue;
                }
                classes.addAll(parsed.classes());
                methods.addAll(parsed.methods());
                parsedFiles.add(parsed);
            }
        }

        IndexStatistics statistics = new IndexStatistics(
                filesScanned,
                filesScanned - skipped.size(),
                classes.size(),
                methods.size(),
                List.copyOf(skipped));

        CallGraphBuild callGraphBuild = new CallGraphBuilder(projectRoot, modules).build(parsedFiles);

        logSummary(statistics, startedAt);

        return CodeIndex.builder()
                .generatedAt(Instant.now())
                .root(projectRoot.toString())
                .modules(modules)
                .classes(classes)
                .methods(methods)
                .files(fingerprints)
                .statistics(statistics)
                .calls(callGraphBuild.callGraph().edges())
                .entryPoints(callGraphBuild.entryPoints())
                .build();
    }

    private void logSummary(IndexStatistics statistics, Instant startedAt) {
        log.info("Indexed {} file(s): {} class(es), {} method(s) in {} ms",
                statistics.filesParsed(),
                statistics.classesIndexed(),
                statistics.methodsIndexed(),
                java.time.Duration.between(startedAt, Instant.now()).toMillis());

        if (statistics.hasSkippedFiles()) {
            log.warn("{} file(s) could not be indexed — the map is incomplete", statistics.skipped().size());
        }
    }
}
