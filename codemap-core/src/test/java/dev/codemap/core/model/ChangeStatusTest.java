package dev.codemap.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChangeStatus} is a closed set (spec §5); the only behaviour worth
 * pinning is that every value the table names actually exists.
 */
class ChangeStatusTest {

    @Test
    void definesExactlyTheStatusesFromTheSpecTable() {
        assertThat(ChangeStatus.values()).containsExactly(
                ChangeStatus.ADDED,
                ChangeStatus.CHANGED,
                ChangeStatus.REMOVED,
                ChangeStatus.AFFECTED,
                ChangeStatus.UNCHANGED);
    }
}
