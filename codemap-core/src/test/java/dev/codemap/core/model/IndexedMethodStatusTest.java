package dev.codemap.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IndexedMethod#status()} is {@code null} until the diff stage runs —
 * parsing needs no git (spec §6.1) — and {@link IndexedMethod#withStatus} is
 * how that stage attaches one afterwards without the parser knowing status
 * exists at all.
 */
class IndexedMethodStatusTest {

    private static final IndexedMethod METHOD = new IndexedMethod(
            "com.example.Service#run()", "com.example.Service", "run", "run() : void",
            "Service.java", 5, 7, null, "void run() {}", false);

    @Test
    @DisplayName("has no status until one is explicitly attached")
    void hasNoStatusByDefault() {
        assertThat(METHOD.status()).isNull();
    }

    @Test
    @DisplayName("withStatus returns a copy carrying the given status, leaving the original untouched")
    void withStatusAttachesStatusImmutably() {
        IndexedMethod changed = METHOD.withStatus(ChangeStatus.CHANGED);

        assertThat(changed.status()).isEqualTo(ChangeStatus.CHANGED);
        assertThat(changed.id()).isEqualTo(METHOD.id());
        assertThat(METHOD.status()).as("the original method is immutable").isNull();
    }
}
