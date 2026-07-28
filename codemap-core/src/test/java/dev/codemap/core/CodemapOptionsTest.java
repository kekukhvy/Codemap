package dev.codemap.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodemapOptionsTest {

    private static final String MISSING_DIRECTORY = "definitely-not-here";
    private static final String FEATURE_BRANCH = "feature/x";
    private static final String CUSTOM_REPORT = "custom/map.html";
    private static final String CONFIG_CONTENT = "entryPoints: []";

    @Nested
    @DisplayName("project root")
    class Root {

        @Test
        @DisplayName("is accepted when it is an existing directory")
        void acceptsExistingDirectory(@TempDir Path projectRoot) {
            CodemapOptions options = CodemapOptions.builder().root(projectRoot).build();

            assertThat(options.root()).isEqualTo(projectRoot.toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("is rejected with a readable message when it does not exist")
        void rejectsMissingDirectory(@TempDir Path projectRoot) {
            Path missing = projectRoot.resolve(MISSING_DIRECTORY);

            assertThatThrownBy(() -> CodemapOptions.builder().root(missing).build())
                    .isInstanceOf(InvalidOptionsException.class)
                    .hasMessageContaining("does not exist")
                    .hasMessageContaining(MISSING_DIRECTORY);
        }

        @Test
        @DisplayName("is rejected when it points at a file")
        void rejectsRegularFile(@TempDir Path projectRoot) throws IOException {
            Path file = Files.createFile(projectRoot.resolve("build.gradle"));

            assertThatThrownBy(() -> CodemapOptions.builder().root(file).build())
                    .isInstanceOf(InvalidOptionsException.class)
                    .hasMessageContaining("not a directory");
        }

        @Test
        @DisplayName("is rejected when absent entirely")
        void rejectsNullRoot() {
            assertThatThrownBy(() -> CodemapOptions.builder().build())
                    .isInstanceOf(InvalidOptionsException.class)
                    .hasMessageContaining("--root");
        }

        @Test
        @DisplayName("is normalised to an absolute path")
        void normalisesToAbsolutePath(@TempDir Path projectRoot) {
            Path unnormalised = projectRoot.resolve("nested").resolve("..");
            createDirectory(projectRoot.resolve("nested"));

            CodemapOptions options = CodemapOptions.builder().root(unnormalised).build();

            assertThat(options.root()).isAbsolute();
            assertThat(options.root().toString()).doesNotContain("..");
        }
    }

    @Nested
    @DisplayName("revisions")
    class Revisions {

        @Test
        @DisplayName("default to HEAD when no base is named")
        void defaultsToHead(@TempDir Path projectRoot) {
            CodemapOptions options = CodemapOptions.builder().root(projectRoot).build();

            assertThat(options.base()).isEqualTo(CodemapOptions.DEFAULT_BASE_REVISION);
        }

        @Test
        @DisplayName("keep an explicit base")
        void keepsExplicitBase(@TempDir Path projectRoot) {
            CodemapOptions options = CodemapOptions.builder()
                    .root(projectRoot)
                    .base(FEATURE_BRANCH)
                    .build();

            assertThat(options.base()).isEqualTo(FEATURE_BRANCH);
        }

        @Test
        @DisplayName("reject a blank base rather than silently using HEAD")
        void rejectsBlankBase(@TempDir Path projectRoot) {
            assertThatThrownBy(() -> CodemapOptions.builder()
                    .root(projectRoot)
                    .base("   ")
                    .build())
                    .isInstanceOf(InvalidOptionsException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("treat a blank since as absent")
        void treatsBlankSinceAsAbsent(@TempDir Path projectRoot) {
            CodemapOptions options = CodemapOptions.builder()
                    .root(projectRoot)
                    .since("  ")
                    .build();

            assertThat(options.since()).isEmpty();
        }

        @Test
        @DisplayName("expose since when a commit range is requested")
        void exposesSince(@TempDir Path projectRoot) {
            CodemapOptions options = CodemapOptions.builder()
                    .root(projectRoot)
                    .since("HEAD~5")
                    .build();

            assertThat(options.since()).contains("HEAD~5");
        }
    }

    @Nested
    @DisplayName("output location")
    class Output {

        @Test
        @DisplayName("defaults to codemap/report.html inside the project")
        void defaultsInsideProject(@TempDir Path projectRoot) {
            CodemapOptions options = CodemapOptions.builder().root(projectRoot).build();

            assertThat(options.output())
                    .isEqualTo(projectRoot.toAbsolutePath().normalize()
                            .resolve(CodemapOptions.OUTPUT_DIRECTORY)
                            .resolve(CodemapOptions.DEFAULT_REPORT_NAME));
        }

        @Test
        @DisplayName("resolves a relative --out against the project, not the launch directory")
        void resolvesRelativeOutputAgainstProject(@TempDir Path projectRoot) {
            CodemapOptions options = CodemapOptions.builder()
                    .root(projectRoot)
                    .output(Path.of(CUSTOM_REPORT))
                    .build();

            assertThat(options.output())
                    .isEqualTo(projectRoot.toAbsolutePath().normalize().resolve(CUSTOM_REPORT));
        }

        @Test
        @DisplayName("keeps an absolute --out as given")
        void keepsAbsoluteOutput(@TempDir Path projectRoot, @TempDir Path elsewhere) {
            Path target = elsewhere.resolve("report.html").toAbsolutePath();

            CodemapOptions options = CodemapOptions.builder()
                    .root(projectRoot)
                    .output(target)
                    .build();

            assertThat(options.output()).isEqualTo(target.normalize());
        }

        @Test
        @DisplayName("places the index cache beside the report")
        void placesIndexBesideReport(@TempDir Path projectRoot) {
            CodemapOptions options = CodemapOptions.builder().root(projectRoot).build();

            assertThat(options.indexPath())
                    .isEqualTo(options.outputDirectory().resolve(CodemapOptions.DEFAULT_INDEX_NAME));
            assertThat(options.indexPath().getParent()).isEqualTo(options.output().getParent());
        }
    }

    @Nested
    @DisplayName("config discovery")
    class Config {

        @Test
        @DisplayName("finds codemap.yml in the project root without being told")
        void discoversConventionalConfig(@TempDir Path projectRoot) throws IOException {
            Path config = projectRoot.resolve(CodemapOptions.DEFAULT_CONFIG_NAME);
            Files.writeString(config, CONFIG_CONTENT);

            CodemapOptions options = CodemapOptions.builder().root(projectRoot).build();

            assertThat(options.config()).contains(config.toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("reports no config when the project has none")
        void reportsNoConfigWhenAbsent(@TempDir Path projectRoot) {
            CodemapOptions options = CodemapOptions.builder().root(projectRoot).build();

            assertThat(options.config()).isEmpty();
        }

        @Test
        @DisplayName("keeps an explicit config even when the file is missing, so the loader can report it")
        void keepsExplicitMissingConfig(@TempDir Path projectRoot) {
            Path requested = projectRoot.resolve("rules.yml");

            CodemapOptions options = CodemapOptions.builder()
                    .root(projectRoot)
                    .config(requested)
                    .build();

            assertThat(options.config()).contains(requested.toAbsolutePath().normalize());
        }
    }

    @Nested
    @DisplayName("flags")
    class Flags {

        @Test
        @DisplayName("default to off")
        void defaultToOff(@TempDir Path projectRoot) {
            CodemapOptions options = CodemapOptions.builder().root(projectRoot).build();

            assertThat(options.aiEnabled()).isFalse();
            assertThat(options.rebuild()).isFalse();
        }

        @Test
        @DisplayName("are carried through when set")
        void carryThroughWhenSet(@TempDir Path projectRoot) {
            CodemapOptions options = CodemapOptions.builder()
                    .root(projectRoot)
                    .aiEnabled(true)
                    .rebuild(true)
                    .build();

            assertThat(options.aiEnabled()).isTrue();
            assertThat(options.rebuild()).isTrue();
        }
    }

    private static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare test directory: " + directory, e);
        }
    }
}
