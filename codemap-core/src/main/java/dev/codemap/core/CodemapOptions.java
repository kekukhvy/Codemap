package dev.codemap.core;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Validated, immutable inputs for one Codemap run.
 *
 * <p>This is the contract between the CLI and the analysis pipeline. It carries
 * no picocli types on purpose: the pipeline can be driven from a test, or later
 * from another front end, without an argument parser in the way.
 *
 * <p>Instances are always valid — {@link Builder#build()} rejects anything the
 * pipeline could not act on, so no downstream stage needs to re-check.
 */
public final class CodemapOptions {

    /** Directory holding generated artifacts, relative to the analysed project. */
    public static final String OUTPUT_DIRECTORY = "codemap";

    /** Default report file name inside {@link #OUTPUT_DIRECTORY}. */
    public static final String DEFAULT_REPORT_NAME = "report.html";

    /** Default index file name inside {@link #OUTPUT_DIRECTORY}. */
    public static final String DEFAULT_INDEX_NAME = "index.json";

    /**
     * Fallback base branch when the repository's default branch cannot be read.
     *
     * <p>Only reached when {@code origin/HEAD} is unset and no common branch name
     * exists — in that case the diff stage reports that it could not resolve a
     * base rather than guessing wrongly.
     */
    public static final String FALLBACK_BASE_BRANCH = "main";

    /** Config file looked up in the project root when {@code --config} is absent. */
    public static final String DEFAULT_CONFIG_NAME = "codemap.yml";

    private final Path root;
    private final String base;
    private final String since;
    private final Path output;
    private final Path config;
    private final boolean aiEnabled;
    private final boolean rebuild;

    private CodemapOptions(Builder builder) {
        this.root = builder.root;
        this.base = builder.base;
        this.since = builder.since;
        this.output = builder.output;
        this.config = builder.config;
        this.aiEnabled = builder.aiEnabled;
        this.rebuild = builder.rebuild;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Project to analyse. Always an existing, readable directory. */
    public Path root() {
        return root;
    }

    /**
     * Branch this one is compared against, when the caller named one.
     *
     * <p>Empty means "work it out": the diff stage resolves the repository's
     * default branch, so the common case needs no flag.
     *
     * @return the base branch, or empty to auto-detect
     */
    public Optional<String> base() {
        return Optional.ofNullable(base);
    }

    /**
     * Commit to diff from, when the caller wants an explicit revision rather than
     * the branch point.
     *
     * @return the commit, or empty when the branch point should be used
     */
    public Optional<String> since() {
        return Optional.ofNullable(since);
    }

    /**
     * Whether changes are measured from the branch point or from one revision.
     *
     * <p>Derived rather than set: naming a {@code --since} commit is what selects
     * {@link ComparisonMode#REVISION}.
     */
    public ComparisonMode comparisonMode() {
        return since == null ? ComparisonMode.BRANCH : ComparisonMode.REVISION;
    }

    /** Absolute path the report is written to. */
    public Path output() {
        return output;
    }

    /**
     * Explicit config file, when one was requested.
     *
     * @return the config path, or empty to fall back to {@code codemap.yml} in
     *         the project root if it happens to exist
     */
    public Optional<Path> config() {
        return Optional.ofNullable(config);
    }

    /** Whether unresolved entry-point candidates may be sent for AI classification. */
    public boolean aiEnabled() {
        return aiEnabled;
    }

    /** Whether a cached index must be discarded and the project reparsed in full. */
    public boolean rebuild() {
        return rebuild;
    }

    /** Directory generated artifacts belong in, derived from the report location. */
    public Path outputDirectory() {
        return output.getParent();
    }

    /** Path of the index file, which doubles as the incremental cache. */
    public Path indexPath() {
        return outputDirectory().resolve(DEFAULT_INDEX_NAME);
    }

    /** Builds validated {@link CodemapOptions}; see {@link #build()} for the rules. */
    public static final class Builder {

        private Path root;
        private String base;
        private String since;
        private Path output;
        private Path config;
        private boolean aiEnabled;
        private boolean rebuild;

        private Builder() {
        }

        public Builder root(Path value) {
            this.root = value;
            return this;
        }

        public Builder base(String value) {
            this.base = value;
            return this;
        }

        public Builder since(String value) {
            this.since = value;
            return this;
        }

        public Builder output(Path value) {
            this.output = value;
            return this;
        }

        public Builder config(Path value) {
            this.config = value;
            return this;
        }

        public Builder aiEnabled(boolean value) {
            this.aiEnabled = value;
            return this;
        }

        public Builder rebuild(boolean value) {
            this.rebuild = value;
            return this;
        }

        /**
         * Validates and assembles the options.
         *
         * <p>Paths are normalised to absolute form so later stages never depend on
         * the working directory the tool happened to be launched from.
         *
         * @return validated options
         * @throws InvalidOptionsException if the root is missing or is not a
         *         readable directory
         */
        public CodemapOptions build() {
            this.root = OptionValidator.requireReadableDirectory(root);
            this.base = OptionValidator.normaliseOptionalRevision(base);
            this.since = OptionValidator.normaliseOptionalRevision(since);
            this.output = OptionValidator.resolveOutput(output, root);
            this.config = OptionValidator.resolveConfig(config, root);
            return new CodemapOptions(this);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CodemapOptions that)) {
            return false;
        }
        return aiEnabled == that.aiEnabled
                && rebuild == that.rebuild
                && root.equals(that.root)
                && Objects.equals(base, that.base)
                && Objects.equals(since, that.since)
                && output.equals(that.output)
                && Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(root, base, since, output, config, aiEnabled, rebuild);
    }

    @Override
    public String toString() {
        return "CodemapOptions[root=%s, base=%s, since=%s, output=%s, config=%s, ai=%s, rebuild=%s]"
                .formatted(root, base, since, output, config, aiEnabled, rebuild);
    }
}
