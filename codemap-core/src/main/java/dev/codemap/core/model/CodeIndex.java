package dev.codemap.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The complete static picture of a project: modules, types, and methods.
 *
 * <p>This is a serialisation contract — {@code index.json} is read back by later
 * runs and by the report, so changing a field is a breaking change and must bump
 * {@link #SCHEMA_VERSION}.
 *
 * <p>The index doubles as the incremental cache: {@link #files()} carries a
 * fingerprint per source file so a rerun can reparse only what changed.
 */
public final class CodeIndex {

    /**
     * Version of the on-disk format.
     *
     * <p>A cache written by a different version is discarded and rebuilt rather
     * than migrated: the index is derived data, so recomputing it is always
     * cheaper and safer than writing migration code for every field change.
     */
    public static final int SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final Instant generatedAt;
    private final String root;
    private final List<IndexedModule> modules;
    private final List<IndexedClass> classes;
    private final List<IndexedMethod> methods;
    private final List<EntryPoint> entryPoints;
    private final Map<String, FileFingerprint> files;
    private final IndexStatistics statistics;
    private final CallGraph callGraph;

    private final Map<String, IndexedClass> classesById;
    private final Map<String, List<IndexedMethod>> methodsByClassId;

    private CodeIndex(Builder builder) {
        this.schemaVersion = builder.schemaVersion;
        this.generatedAt = builder.generatedAt;
        this.root = builder.root;
        this.modules = List.copyOf(builder.modules);
        this.classes = List.copyOf(builder.classes);
        this.methods = List.copyOf(builder.methods);
        this.entryPoints = List.copyOf(builder.entryPoints);
        this.files = Collections.unmodifiableMap(new LinkedHashMap<>(builder.files));
        this.statistics = builder.statistics;
        this.callGraph = new CallGraph(builder.calls);

        this.classesById = classes.stream()
                .collect(Collectors.toUnmodifiableMap(IndexedClass::id, Function.identity()));
        this.methodsByClassId = methods.stream()
                .collect(Collectors.groupingBy(IndexedMethod::classId));
    }

    public static Builder builder() {
        return new Builder();
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public Instant generatedAt() {
        return generatedAt;
    }

    /** Absolute path of the analysed project. */
    public String root() {
        return root;
    }

    /** Build modules, in discovery order. Each is a root of the map. */
    public List<IndexedModule> modules() {
        return modules;
    }

    public List<IndexedClass> classes() {
        return classes;
    }

    public List<IndexedMethod> methods() {
        return methods;
    }

    /** Every detected entry point: a root of the map (spec §4). */
    public List<EntryPoint> entryPoints() {
        return entryPoints;
    }

    /** Fingerprint per indexed file, keyed by path relative to the project root. */
    public Map<String, FileFingerprint> files() {
        return files;
    }

    /** Counts and degradation notes from the run that produced this index. */
    public IndexStatistics statistics() {
        return statistics;
    }

    /** Every call, type-use, and implementation edge, with reverse lookups. */
    public CallGraph callGraph() {
        return callGraph;
    }

    /** Raw edge list, for serialisation. Prefer {@link #callGraph()} for lookups. */
    public List<CallEdge> calls() {
        return callGraph.edges();
    }

    /**
     * Looks up a type by id.
     *
     * @param classId identifier from {@link IndexedClass#id()}
     * @return the type, or empty when the id is unknown
     */
    public Optional<IndexedClass> findClass(String classId) {
        return Optional.ofNullable(classesById.get(classId));
    }

    /**
     * Methods declared by one type, in source order.
     *
     * @param classId identifier from {@link IndexedClass#id()}
     * @return the methods, or an empty list when the type declares none
     */
    public List<IndexedMethod> methodsOf(String classId) {
        return methodsByClassId.getOrDefault(classId, List.of());
    }

    /** Types belonging to one module, in source order. */
    public List<IndexedClass> classesOf(String moduleId) {
        return classes.stream()
                .filter(indexed -> indexed.moduleId().equals(moduleId))
                .toList();
    }

    /**
     * Entry points belonging to one module, in detection order.
     *
     * <p>Modules are the roots of the map and their entry points hang beneath
     * them, so the renderer asks per module rather than filtering a flat list.
     *
     * @param moduleId identifier from {@link IndexedModule#id()}
     * @return that module's entry points, empty when it has none — normal for a
     *         library module that nothing external reaches
     */
    public List<EntryPoint> entryPointsOf(String moduleId) {
        return entryPoints.stream()
                .filter(entryPoint -> entryPoint.moduleId().equals(moduleId))
                .toList();
    }

    /** Builds a {@link CodeIndex}. */
    public static final class Builder {

        private int schemaVersion = SCHEMA_VERSION;
        private Instant generatedAt = Instant.now();
        private String root = "";
        private List<IndexedModule> modules = List.of();
        private List<IndexedClass> classes = List.of();
        private List<IndexedMethod> methods = List.of();
        private List<EntryPoint> entryPoints = List.of();
        private Map<String, FileFingerprint> files = Map.of();
        private IndexStatistics statistics = IndexStatistics.empty();
        private List<CallEdge> calls = List.of();

        private Builder() {
        }

        public Builder schemaVersion(int value) {
            this.schemaVersion = value;
            return this;
        }

        public Builder generatedAt(Instant value) {
            this.generatedAt = value;
            return this;
        }

        public Builder root(String value) {
            this.root = value;
            return this;
        }

        public Builder modules(List<IndexedModule> value) {
            this.modules = Objects.requireNonNull(value, "modules");
            return this;
        }

        public Builder classes(List<IndexedClass> value) {
            this.classes = Objects.requireNonNull(value, "classes");
            return this;
        }

        public Builder methods(List<IndexedMethod> value) {
            this.methods = Objects.requireNonNull(value, "methods");
            return this;
        }

        public Builder entryPoints(List<EntryPoint> value) {
            this.entryPoints = Objects.requireNonNull(value, "entryPoints");
            return this;
        }

        public Builder files(Map<String, FileFingerprint> value) {
            this.files = Objects.requireNonNull(value, "files");
            return this;
        }

        public Builder statistics(IndexStatistics value) {
            this.statistics = Objects.requireNonNull(value, "statistics");
            return this;
        }

        public Builder calls(List<CallEdge> value) {
            this.calls = Objects.requireNonNull(value, "calls");
            return this;
        }

        public CodeIndex build() {
            return new CodeIndex(this);
        }
    }
}
