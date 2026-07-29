package dev.codemap.core.diff;

import dev.codemap.core.ComparisonMode;
import dev.codemap.core.model.CallEdge;
import dev.codemap.core.model.ChangeStatus;
import dev.codemap.core.model.CodeIndex;
import dev.codemap.core.model.EdgeKind;
import dev.codemap.core.model.IndexedClass;
import dev.codemap.core.model.IndexedMethod;
import dev.codemap.core.model.IndexedModule;
import dev.codemap.core.model.Layer;
import dev.codemap.core.model.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GitChangeAnalyzer} is the diff stage's entry point: given an already
 * built {@link CodeIndex} and a repository, it produces a copy with change
 * status attached to every class and method (spec §5), or reports why it
 * could not — a repository problem never fails the run.
 *
 * <p>Runs against hermetic {@code git init} repositories; the index itself is
 * hand-built so this test is about wiring the diff stages together, not about
 * parsing.
 */
class GitChangeAnalyzerTest {

    private static final String MODULE_ID = "app";
    private static final String FILE = "Service.java";
    private static final String CLASS_ID = "com.example.Service";

    private final GitChangeAnalyzer analyzer = new GitChangeAnalyzer(new GitChangeSource(new GitCommandRunner()));

    @TempDir
    Path repoRoot;

    @Nested
    @DisplayName("successful analysis")
    class SuccessfulAnalysis {

        @Test
        @DisplayName("marks the edited method changed and leaves the rest unchanged")
        void marksEditedMethodChanged() throws IOException, InterruptedException {
            initRepo();
            writeAndCommit(FILE, javaClass("void touched() {\n    }", "void untouched() {\n    }"), "init on main");
            checkoutBranch("feature");
            writeAndCommit(FILE, javaClass("void touched() {\n        int x = 1;\n    }", "void untouched() {\n    }"), "edit");

            CodeIndex index = indexWithMethods(method("touched", 2, 4), method("untouched", 5, 6));
            ChangeAnalysisResult result = analyzer.analyze(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty(), index);

            assertThat(result.isResolved()).isTrue();
            IndexedMethod touched = methodNamed(result.index(), "touched");
            IndexedMethod untouched = methodNamed(result.index(), "untouched");
            assertThat(touched.status()).isEqualTo(ChangeStatus.CHANGED);
            assertThat(untouched.status()).isEqualTo(ChangeStatus.UNCHANGED);
        }

        @Test
        @DisplayName("aggregates the owning class's status from its methods")
        void aggregatesClassStatus() throws IOException, InterruptedException {
            initRepo();
            writeAndCommit(FILE, javaClass("void touched() {\n    }"), "init on main");
            checkoutBranch("feature");
            writeAndCommit(FILE, javaClass("void touched() {\n        int x = 1;\n    }"), "edit");

            CodeIndex index = indexWithMethods(method("touched", 2, 4));
            ChangeAnalysisResult result = analyzer.analyze(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty(), index);

            assertThat(result.index().classes()).singleElement()
                    .extracting(IndexedClass::status).isEqualTo(ChangeStatus.CHANGED);
        }

        @Test
        @DisplayName("marks a changed method's caller affected")
        void marksCallerAffected() throws IOException, InterruptedException {
            initRepo();
            writeAndCommit(FILE, javaClass("void touched() {\n    }", "void caller() {\n    }"), "init on main");
            checkoutBranch("feature");
            writeAndCommit(FILE, javaClass("void touched() {\n        int x = 1;\n    }", "void caller() {\n    }"), "edit");

            IndexedMethod touched = method("touched", 2, 4);
            IndexedMethod caller = method("caller", 5, 7);
            CallEdge edge = new CallEdge(caller.id(), touched.id(), EdgeKind.CALL_INTERNAL, true, 6, null, null);
            CodeIndex index = CodeIndex.builder()
                    .root(repoRoot.toString())
                    .modules(List.of(new IndexedModule(MODULE_ID, MODULE_ID, MODULE_ID, List.of("."))))
                    .classes(List.of(indexedClass()))
                    .methods(List.of(touched, caller))
                    .calls(List.of(edge))
                    .build();

            ChangeAnalysisResult result = analyzer.analyze(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty(), index);

            assertThat(methodNamed(result.index(), "caller").status()).isEqualTo(ChangeStatus.AFFECTED);
        }

        @Test
        @DisplayName("records a deleted method as a removed node with the old line range")
        void recordsRemovedMethod() throws IOException, InterruptedException {
            initRepo();
            writeAndCommit(FILE, javaClass("void touched() {\n    }", "void deleted() {\n    }"), "init on main");
            checkoutBranch("feature");
            writeAndCommit(FILE, javaClass("void touched() {\n    }"), "delete a method");

            CodeIndex index = indexWithMethods(method("touched", 2, 3));
            ChangeAnalysisResult result = analyzer.analyze(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty(), index);

            assertThat(result.index().removedMethods()).singleElement()
                    .satisfies(removed -> assertThat(removed.file()).isEqualTo(FILE));
        }
    }

    @Nested
    @DisplayName("degradation")
    class Degradation {

        @Test
        @DisplayName("returns the index untouched, with a readable reason, for a repository with no commits")
        void degradesForARepositoryWithNoCommits() throws IOException, InterruptedException {
            initRepo();
            CodeIndex index = indexWithMethods(method("touched", 2, 4));

            ChangeAnalysisResult result = analyzer.analyze(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty(), index);

            assertThat(result.isResolved()).isFalse();
            assertThat(result.failureReason()).isNotBlank();
            assertThat(methodNamed(result.index(), "touched").status()).isNull();
        }

        @Test
        @DisplayName("returns the index untouched when the directory is not a git repository")
        void degradesWhenGitIsUnavailable() {
            CodeIndex index = indexWithMethods(method("touched", 2, 4));

            ChangeAnalysisResult result = analyzer.analyze(repoRoot, ComparisonMode.BRANCH, Optional.of("main"), Optional.empty(), index);

            assertThat(result.isResolved()).isFalse();
            assertThat(result.index().methods()).hasSize(1);
        }
    }

    private CodeIndex indexWithMethods(IndexedMethod... methods) {
        return CodeIndex.builder()
                .root(repoRoot.toString())
                .modules(List.of(new IndexedModule(MODULE_ID, MODULE_ID, MODULE_ID, List.of("."))))
                .classes(List.of(indexedClass()))
                .methods(List.of(methods))
                .build();
    }

    private IndexedClass indexedClass() {
        return new IndexedClass(CLASS_ID, MODULE_ID, CLASS_ID, "Service", "com.example",
                TypeKind.CLASS, Layer.APPLICATION, FILE, 1, 20, null);
    }

    private IndexedMethod method(String name, int lineStart, int lineEnd) {
        return new IndexedMethod(CLASS_ID + "#" + name + "()", CLASS_ID, name, name + "()", FILE,
                lineStart, lineEnd, null, "void " + name + "() {}", false);
    }

    private IndexedMethod methodNamed(CodeIndex index, String name) {
        return index.methods().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
    }

    private String javaClass(String... methodBodies) {
        StringBuilder body = new StringBuilder("class Service {\n");
        for (String methodBody : methodBodies) {
            body.append("    ").append(methodBody).append("\n");
        }
        body.append("}\n");
        return body.toString();
    }

    private void initRepo() throws IOException, InterruptedException {
        GitCommandRunner runner = new GitCommandRunner();
        runner.run(repoRoot, "init", "-q", "-b", "main");
        runner.run(repoRoot, "config", "user.email", "test@example.com");
        runner.run(repoRoot, "config", "user.name", "Test");
    }

    private void writeAndCommit(String fileName, String content, String message) throws IOException, InterruptedException {
        GitCommandRunner runner = new GitCommandRunner();
        Files.writeString(repoRoot.resolve(fileName), content);
        runner.run(repoRoot, "add", fileName);
        runner.run(repoRoot, "commit", "-q", "-m", message);
    }

    private void checkoutBranch(String branch) throws IOException, InterruptedException {
        GitCommandRunner runner = new GitCommandRunner();
        GitCommandResult result = runner.run(repoRoot, "checkout", branch);
        if (!result.succeeded()) {
            runner.run(repoRoot, "checkout", "-b", branch);
        }
    }
}
