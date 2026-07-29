package dev.codemap.core.index;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.codemap.core.model.CodeIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads and writes {@code index.json}.
 *
 * <p>The file is both the analysis output and the incremental cache, so reading is
 * tolerant by design: a missing, unreadable, corrupt, or version-mismatched file
 * yields {@link Optional#empty()} and the caller rebuilds. Failing instead would
 * turn a stale cache — something the tool can always recover from — into a broken
 * run.
 */
public final class IndexStore {

    private static final Logger log = LoggerFactory.getLogger(IndexStore.class);

    private final ObjectMapper objectMapper;

    public IndexStore() {
        this.objectMapper = JsonMapper.builder()
                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    /**
     * Writes an index, creating the output directory when needed.
     *
     * @param index the index to persist
     * @param target destination file
     * @throws IndexWriteException if the file cannot be written — unlike a failed
     *         read, this is unrecoverable: the run produced results with nowhere
     *         to put them
     */
    public void write(CodeIndex index, Path target) {
        try {
            Path directory = target.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            objectMapper.writeValue(target.toFile(), IndexDocument.from(index));
            log.debug("Wrote index to {}", target);
        } catch (IOException e) {
            throw new IndexWriteException("Could not write the index to " + target, e);
        }
    }

    /**
     * Reads a previously written index.
     *
     * @param source file to read
     * @return the index, or empty when it is absent, unreadable, or written by a
     *         different schema version
     */
    public Optional<CodeIndex> read(Path source) {
        if (!Files.isRegularFile(source)) {
            return Optional.empty();
        }
        try {
            IndexDocument document = objectMapper.readValue(source.toFile(), IndexDocument.class);
            if (document.schemaVersion() != CodeIndex.SCHEMA_VERSION) {
                log.info("Cached index uses schema {} but this build expects {}; rebuilding",
                        document.schemaVersion(), CodeIndex.SCHEMA_VERSION);
                return Optional.empty();
            }
            return Optional.of(document.toIndex());
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read the cached index at {} ({}); rebuilding", source, e.getMessage());
            return Optional.empty();
        }
    }

    /** Thrown when an index cannot be written. */
    public static final class IndexWriteException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        IndexWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
