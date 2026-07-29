package dev.codemap.core.index;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.codemap.core.model.CodeIndex;
import dev.codemap.core.model.FileFingerprint;
import dev.codemap.core.model.IndexStatistics;
import dev.codemap.core.model.IndexedClass;
import dev.codemap.core.model.IndexedMethod;
import dev.codemap.core.model.IndexedModule;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * On-disk shape of {@code index.json}.
 *
 * <p>Kept separate from {@link CodeIndex} so the model can hold derived lookups
 * and behaviour while the serialised form stays a flat, stable contract. Jackson
 * annotations live here rather than on the model, which keeps the core free of
 * framework concerns.
 *
 * @param schemaVersion format version, validated on read
 * @param generatedAt when the index was produced
 * @param root absolute path of the analysed project
 * @param modules build modules, each a root of the map
 * @param classes indexed types
 * @param methods indexed methods and constructors
 * @param files fingerprint per source file, keyed by relative path
 * @param statistics counts and skipped files from the run
 */
record IndexDocument(
        @JsonProperty("schemaVersion") int schemaVersion,
        @JsonProperty("generatedAt") Instant generatedAt,
        @JsonProperty("root") String root,
        @JsonProperty("modules") List<IndexedModule> modules,
        @JsonProperty("classes") List<IndexedClass> classes,
        @JsonProperty("methods") List<IndexedMethod> methods,
        @JsonProperty("files") Map<String, FileFingerprint> files,
        @JsonProperty("statistics") IndexStatistics statistics) {

    @JsonCreator
    IndexDocument {
        modules = modules == null ? List.of() : modules;
        classes = classes == null ? List.of() : classes;
        methods = methods == null ? List.of() : methods;
        files = files == null ? Map.of() : files;
        statistics = statistics == null ? IndexStatistics.empty() : statistics;
    }

    static IndexDocument from(CodeIndex index) {
        return new IndexDocument(
                index.schemaVersion(),
                index.generatedAt(),
                index.root(),
                index.modules(),
                index.classes(),
                index.methods(),
                index.files(),
                index.statistics());
    }

    CodeIndex toIndex() {
        return CodeIndex.builder()
                .schemaVersion(schemaVersion)
                .generatedAt(generatedAt)
                .root(root)
                .modules(modules)
                .classes(classes)
                .methods(methods)
                .files(files)
                .statistics(statistics)
                .build();
    }
}
