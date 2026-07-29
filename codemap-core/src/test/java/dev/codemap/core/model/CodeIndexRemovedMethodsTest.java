package dev.codemap.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CodeIndex#removedMethods()} carries {@code removed} nodes (spec §5),
 * which are never part of {@link CodeIndex#methods()} since they have no
 * current declaration to be one.
 */
class CodeIndexRemovedMethodsTest {

    @Test
    @DisplayName("defaults to empty when the diff stage never ran")
    void defaultsToEmpty() {
        CodeIndex index = CodeIndex.builder().build();

        assertThat(index.removedMethods()).isEmpty();
    }

    @Test
    @DisplayName("carries every removed method the builder was given")
    void carriesGivenRemovedMethods() {
        RemovedMethod removed = new RemovedMethod("Service.java", 10, 15);

        CodeIndex index = CodeIndex.builder().removedMethods(List.of(removed)).build();

        assertThat(index.removedMethods()).containsExactly(removed);
    }
}
