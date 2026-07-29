package dev.codemap.core.index;

import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.ChangeStatus;
import dev.codemap.core.model.CodeIndex;
import dev.codemap.core.model.EdgeKind;
import dev.codemap.core.model.FileFingerprint;
import dev.codemap.core.model.IndexStatistics;
import dev.codemap.core.model.IndexedClass;
import dev.codemap.core.model.IndexedMethod;
import dev.codemap.core.model.IndexedModule;
import dev.codemap.core.model.Layer;
import dev.codemap.core.model.RemovedMethod;
import dev.codemap.core.model.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IndexStoreTest {

    private static final String CLASS_ID = "com.example.Service";
    private static final String MODULE_ID = "app";
    private static final String SOURCE_FILE = "src/main/java/com/example/Service.java";

    private final IndexStore store = new IndexStore();

    @TempDir
    Path workingDirectory;

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("preserves every field through write and read")
        void preservesFields() {
            Path target = workingDirectory.resolve("codemap/index.json");

            store.write(sampleIndex(), target);
            CodeIndex restored = store.read(target).orElseThrow();

            assertThat(restored.schemaVersion()).isEqualTo(CodeIndex.SCHEMA_VERSION);
            assertThat(restored.modules()).extracting(IndexedModule::name).containsExactly(MODULE_ID);
            assertThat(restored.classes()).singleElement()
                    .satisfies(indexed -> {
                        assertThat(indexed.fqn()).isEqualTo(CLASS_ID);
                        assertThat(indexed.kind()).isEqualTo(TypeKind.CLASS);
                        assertThat(indexed.layer()).isEqualTo(Layer.APPLICATION);
                    });
            assertThat(restored.methods()).singleElement()
                    .satisfies(method -> {
                        assertThat(method.signature()).isEqualTo("run() : void");
                        assertThat(method.source()).contains("void run()");
                    });
            assertThat(restored.files()).containsKey(SOURCE_FILE);
            assertThat(restored.callGraph().edges()).singleElement()
                    .satisfies(edge -> {
                        assertThat(edge.from()).isEqualTo(CLASS_ID + "#run()");
                        assertThat(edge.to()).isEqualTo(CLASS_ID + "#helper()");
                        assertThat(edge.kind()).isEqualTo(EdgeKind.CALL_INTERNAL);
                        assertThat(edge.resolved()).isTrue();
                    });
        }

        @Test
        @DisplayName("preserves change status on classes and methods")
        void preservesChangeStatus() {
            Path target = workingDirectory.resolve("index.json");
            CodeIndex index = sampleIndex();
            IndexedClass statusedClass = index.classes().get(0).withStatus(ChangeStatus.CHANGED);
            IndexedMethod statusedMethod = index.methods().get(0).withStatus(ChangeStatus.CHANGED);
            CodeIndex withStatus = CodeIndex.builder()
                    .root(index.root())
                    .modules(index.modules())
                    .classes(List.of(statusedClass))
                    .methods(List.of(statusedMethod))
                    .files(index.files())
                    .statistics(index.statistics())
                    .calls(index.calls())
                    .build();

            store.write(withStatus, target);
            CodeIndex restored = store.read(target).orElseThrow();

            assertThat(restored.classes()).singleElement()
                    .extracting(IndexedClass::status).isEqualTo(ChangeStatus.CHANGED);
            assertThat(restored.methods()).singleElement()
                    .extracting(IndexedMethod::status).isEqualTo(ChangeStatus.CHANGED);
        }

        @Test
        @DisplayName("preserves removed methods, which have no current declaration")
        void preservesRemovedMethods() {
            Path target = workingDirectory.resolve("index.json");
            RemovedMethod removed = new RemovedMethod(SOURCE_FILE, 20, 24);
            CodeIndex withRemoved = CodeIndex.builder()
                    .root(workingDirectory.toString())
                    .modules(List.of(new IndexedModule(MODULE_ID, MODULE_ID, MODULE_ID, List.of("src/main/java"))))
                    .removedMethods(List.of(removed))
                    .build();

            store.write(withRemoved, target);
            CodeIndex restored = store.read(target).orElseThrow();

            assertThat(restored.removedMethods()).containsExactly(removed);
        }

        @Test
        @DisplayName("creates the output directory when it does not exist")
        void createsOutputDirectory() {
            Path target = workingDirectory.resolve("nested/deeper/index.json");

            store.write(sampleIndex(), target);

            assertThat(target).exists();
        }

        @Test
        @DisplayName("keeps the lookups usable after a read")
        void keepsLookupsUsable() {
            Path target = workingDirectory.resolve("index.json");
            store.write(sampleIndex(), target);

            CodeIndex restored = store.read(target).orElseThrow();

            assertThat(restored.findClass(CLASS_ID)).isPresent();
            assertThat(restored.methodsOf(CLASS_ID)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("tolerant reading")
    class TolerantReading {

        @Test
        @DisplayName("returns empty for a file that is not there")
        void returnsEmptyForMissingFile() {
            assertThat(store.read(workingDirectory.resolve("absent.json"))).isEmpty();
        }

        @Test
        @DisplayName("rebuilds rather than failing when the cache is corrupt")
        void returnsEmptyForCorruptFile() throws IOException {
            Path target = workingDirectory.resolve("index.json");
            Files.writeString(target, "{ this is not json");

            assertThat(store.read(target)).isEmpty();
        }

        @Test
        @DisplayName("rejects an index written by a different schema version")
        void rejectsSchemaMismatch() throws IOException {
            Path target = workingDirectory.resolve("index.json");
            store.write(sampleIndex(), target);
            Files.writeString(target, Files.readString(target)
                    .replace("\"schemaVersion\" : " + CodeIndex.SCHEMA_VERSION, "\"schemaVersion\" : 999"));

            Optional<CodeIndex> read = store.read(target);

            assertThat(read).as("a future schema must trigger a rebuild, not a crash").isEmpty();
        }
    }

    private CodeIndex sampleIndex() {
        IndexedClass service = new IndexedClass(
                CLASS_ID, MODULE_ID, CLASS_ID, "Service", "com.example",
                TypeKind.CLASS, Layer.APPLICATION, SOURCE_FILE, 3, 9, "Does the thing.");

        IndexedMethod run = new IndexedMethod(
                CLASS_ID + "#run()", CLASS_ID, "run", "run() : void", SOURCE_FILE,
                5, 7, null, "    void run() {\n    }", false);

        CallEdge internalCall = new CallEdge(
                CLASS_ID + "#run()", CLASS_ID + "#helper()", EdgeKind.CALL_INTERNAL, true, 6, null, null);

        return CodeIndex.builder()
                .root(workingDirectory.toString())
                .modules(List.of(new IndexedModule(MODULE_ID, MODULE_ID, MODULE_ID, List.of("src/main/java"))))
                .classes(List.of(service))
                .methods(List.of(run))
                .files(Map.of(SOURCE_FILE, new FileFingerprint("abc123", 120L, 1_700_000_000_000L)))
                .statistics(new IndexStatistics(1, 1, 1, 1, List.of()))
                .calls(List.of(internalCall))
                .build();
    }
}
