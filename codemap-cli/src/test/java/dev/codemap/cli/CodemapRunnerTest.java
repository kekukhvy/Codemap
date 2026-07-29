package dev.codemap.cli;

import dev.codemap.core.CodemapOptions;
import dev.codemap.core.diff.GitChangeAnalyzer;
import dev.codemap.core.diff.GitChangeSource;
import dev.codemap.core.diff.GitCommandRunner;
import dev.codemap.core.index.IndexStore;
import dev.codemap.core.index.ProjectIndexer;
import dev.codemap.core.model.ChangeStatus;
import dev.codemap.core.model.CodeIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CodemapRunner} wires the indexing and diff stages together. Runs
 * against a hermetic {@code git init} project so the resulting {@code index.json}
 * can be asserted end to end, the same shape the CLI produces.
 */
class CodemapRunnerTest {

    private static final String MAIN_SOURCES = "src/main/java/com/example";

    private final CodemapRunner runner = new CodemapRunner(
            new ProjectIndexer(), new IndexStore(), new GitChangeAnalyzer(new GitChangeSource(new GitCommandRunner())));

    @TempDir
    Path projectRoot;

    @Nested
    @DisplayName("with a resolvable diff")
    class ResolvableDiff {

        @Test
        @DisplayName("writes an index carrying change status for the branch's changes")
        void writesIndexWithStatus() throws IOException, InterruptedException {
            initRepo();
            writeClass("Service", "class Service { void touched() { } }");
            commitAll("init on main");
            checkoutBranch("feature");
            writeClass("Service", "class Service { void touched() { int x = 1; } }");
            commitAll("edit on feature");

            CodemapOptions options = CodemapOptions.builder().root(projectRoot).base("main").build();
            int exitCode = runner.run(options);

            assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
            CodeIndex index = new IndexStore().read(options.indexPath()).orElseThrow();
            assertThat(index.methods()).extracting(m -> m.status())
                    .as("status was computed for every indexed method, not left null")
                    .doesNotContainNull();
            assertThat(index.methods()).anySatisfy(m -> assertThat(m.status()).isEqualTo(ChangeStatus.CHANGED));
        }
    }

    @Nested
    @DisplayName("with no resolvable diff")
    class UnresolvableDiff {

        @Test
        @DisplayName("still writes the static index, with no status, rather than failing the run")
        void writesIndexWithoutStatus() throws IOException {
            writeClass("Service", "class Service { void touched() { } }");

            CodemapOptions options = CodemapOptions.builder().root(projectRoot).base("main").build();
            int exitCode = runner.run(options);

            assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
            CodeIndex index = new IndexStore().read(options.indexPath()).orElseThrow();
            assertThat(index.methods()).isNotEmpty();
            assertThat(index.methods()).allSatisfy(m -> assertThat(m.status()).isNull());
        }
    }

    private void initRepo() throws IOException, InterruptedException {
        GitCommandRunner git = new GitCommandRunner();
        git.run(projectRoot, "init", "-q", "-b", "main");
        git.run(projectRoot, "config", "user.email", "test@example.com");
        git.run(projectRoot, "config", "user.name", "Test");
    }

    private void writeClass(String name, String body) throws IOException {
        Path directory = projectRoot.resolve(MAIN_SOURCES);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".java"), "package com.example;\n" + body);
    }

    private void commitAll(String message) throws IOException, InterruptedException {
        GitCommandRunner git = new GitCommandRunner();
        git.run(projectRoot, "add", "-A");
        git.run(projectRoot, "commit", "-q", "-m", message);
    }

    private void checkoutBranch(String branch) throws IOException, InterruptedException {
        GitCommandRunner git = new GitCommandRunner();
        git.run(projectRoot, "checkout", "-b", branch);
    }
}
