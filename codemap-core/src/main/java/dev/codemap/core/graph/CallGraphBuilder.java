package dev.codemap.core.graph;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.CallGraph;
import dev.codemap.core.model.IndexedClass;
import dev.codemap.core.model.IndexedModule;
import dev.codemap.core.parse.ParsedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolves call, type-use, and implementation edges across an already-parsed
 * project and assembles them into a {@link CallGraph}.
 *
 * <p>Parsing (one file at a time, tolerant of bad input) and resolution (needs
 * every source root loaded together) are different concerns, which is why this
 * runs as a second pass over {@link ParsedFile}s rather than living inside
 * {@code JavaSourceParser}. The symbol solver is configured with every module's
 * production source root plus the JDK, so cross-module and JDK types resolve.
 * Symbol resolution failures are expected in real projects and degrade to a
 * name-based edge marked {@code resolved: false} instead of failing the run.
 */
public final class CallGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(CallGraphBuilder.class);

    private final Path projectRoot;
    private final JavaParser resolvingParser;
    private final CallExpressionResolver callExpressionResolver;
    private final SignatureTypeResolver signatureTypeResolver;
    private final ImplementsResolver implementsResolver;
    private final ModuleBoundaryReclassifier moduleBoundaryReclassifier;

    public CallGraphBuilder(Path projectRoot, List<IndexedModule> modules) {
        this.projectRoot = projectRoot;
        this.resolvingParser = newResolvingParser(buildTypeSolver(projectRoot, modules));
        this.callExpressionResolver = new CallExpressionResolver();
        this.signatureTypeResolver = new SignatureTypeResolver();
        this.implementsResolver = new ImplementsResolver();
        this.moduleBoundaryReclassifier = new ModuleBoundaryReclassifier();
    }

    /**
     * Assembles the solver from the JDK, the project's own sources, and its
     * dependency jars.
     *
     * <p>The jars matter for what is <em>excluded</em>: without them a call into a
     * library cannot be resolved, so it is indistinguishable from a broken
     * in-project call and leaks into the graph as a dead-end edge. With them the
     * call resolves, is recognised as external, and is dropped by the same filter
     * that already drops JDK calls.
     */
    private static CombinedTypeSolver buildTypeSolver(Path projectRoot, List<IndexedModule> modules) {
        CombinedTypeSolver solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());
        for (IndexedModule module : modules) {
            for (String sourceRoot : module.sourceRoots()) {
                solver.add(new JavaParserTypeSolver(projectRoot.resolve(sourceRoot)));
            }
        }
        addDependencyJars(solver, projectRoot);
        return solver;
    }

    /** Adds each dependency jar, skipping any the solver cannot open. */
    private static void addDependencyJars(CombinedTypeSolver solver, Path projectRoot) {
        int added = 0;
        for (Path jar : DependencyClasspath.discover(projectRoot)) {
            try {
                solver.add(new JarTypeSolver(jar));
                added++;
            } catch (IOException | RuntimeException e) {
                log.debug("Skipping unreadable dependency jar {}: {}", jar, e.getMessage());
            }
        }
        log.debug("Symbol solver loaded {} dependency jar(s)", added);
    }

    private static JavaParser newResolvingParser(CombinedTypeSolver solver) {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
                .setSymbolResolver(new JavaSymbolSolver(solver));
        return new JavaParser(configuration);
    }

    /**
     * Builds the call graph for a set of already-parsed files.
     *
     * @param parsedFiles every successfully parsed production file, across all
     *        modules — resolution needs the whole project visible at once
     * @return the assembled graph; never fails — edges that could not be resolved
     *         are still present, marked {@code resolved: false}
     */
    public CallGraph build(List<ParsedFile> parsedFiles) {
        Map<String, String> moduleIdByClassId = moduleIdByClassId(parsedFiles);
        List<CallEdge> edges = new ArrayList<>();
        for (ParsedFile parsedFile : parsedFiles) {
            if (parsedFile.wasSkipped()) {
                continue;
            }
            reparseWithSymbols(parsedFile.file()).ifPresent(unit -> {
                edges.addAll(callExpressionResolver.resolve(unit, moduleIdByClassId.keySet()));
                edges.addAll(signatureTypeResolver.resolve(unit, moduleIdByClassId.keySet()));
                edges.addAll(implementsResolver.resolve(unit, moduleIdByClassId.keySet()));
            });
        }
        List<CallEdge> reclassified = moduleBoundaryReclassifier.reclassify(edges, moduleIdByClassId);
        logSummary(reclassified);
        return new CallGraph(reclassified);
    }

    /** Every indexed class's owning module, by class id — the boundary for "inside the project". */
    private static Map<String, String> moduleIdByClassId(List<ParsedFile> parsedFiles) {
        return parsedFiles.stream()
                .flatMap(parsedFile -> parsedFile.classes().stream())
                .collect(Collectors.toUnmodifiableMap(IndexedClass::id, IndexedClass::moduleId, (a, b) -> a));
    }

    /**
     * Re-parses one file with the symbol solver injected.
     *
     * <p>{@code JavaSourceParser} parses without a solver, since indexing a single
     * file must not require the whole project to be loaded. Resolution needs the
     * opposite, so the file is read again here rather than threading a resolvable
     * AST back through the parsing stage.
     */
    private Optional<CompilationUnit> reparseWithSymbols(String relativePath) {
        Path absolute = projectRoot.resolve(relativePath);
        try {
            String content = Files.readString(absolute);
            return resolvingParser.parse(content).getResult();
        } catch (IOException | RuntimeException e) {
            log.debug("Could not re-parse {} for call resolution: {}", relativePath, e.getMessage());
            return Optional.empty();
        }
    }

    private void logSummary(List<CallEdge> edges) {
        long resolvedCount = edges.stream().filter(CallEdge::resolved).count();
        log.info("Built call graph: {} edge(s) ({} resolved, {} unresolved)",
                edges.size(), resolvedCount, edges.size() - resolvedCount);
    }
}
