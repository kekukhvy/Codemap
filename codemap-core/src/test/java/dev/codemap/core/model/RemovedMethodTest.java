package dev.codemap.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RemovedMethod} is what a {@code removed} status is recovered from
 * (spec §5): a location in the diff's old revision, with no body to display
 * since the base tree is never parsed.
 */
class RemovedMethodTest {

    private static final String FILE = "com/example/Service.java";

    @Test
    @DisplayName("carries the old file and the deleted line range, with no source")
    void carriesFileAndRange() {
        RemovedMethod removed = new RemovedMethod(FILE, 10, 15);

        assertThat(removed.file()).isEqualTo(FILE);
        assertThat(removed.lineStart()).isEqualTo(10);
        assertThat(removed.lineEnd()).isEqualTo(15);
    }

    @Test
    @DisplayName("rejects a missing file")
    void rejectsMissingFile() {
        assertThatThrownBy(() -> new RemovedMethod(null, 10, 15))
                .isInstanceOf(NullPointerException.class);
    }
}
