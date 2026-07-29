package dev.codemap.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IndexedClass#status()} mirrors {@link IndexedMethod#status()}: absent
 * until the diff stage aggregates it from the class's methods (spec §5).
 */
class IndexedClassStatusTest {

    private static final IndexedClass CLASS = new IndexedClass(
            "com.example.Service", "app", "com.example.Service", "Service", "com.example",
            TypeKind.CLASS, Layer.APPLICATION, "Service.java", 3, 9, null);

    @Test
    @DisplayName("has no status until one is explicitly attached")
    void hasNoStatusByDefault() {
        assertThat(CLASS.status()).isNull();
    }

    @Test
    @DisplayName("withStatus returns a copy carrying the given status, leaving the original untouched")
    void withStatusAttachesStatusImmutably() {
        IndexedClass changed = CLASS.withStatus(ChangeStatus.AFFECTED);

        assertThat(changed.status()).isEqualTo(ChangeStatus.AFFECTED);
        assertThat(changed.id()).isEqualTo(CLASS.id());
        assertThat(CLASS.status()).as("the original class is immutable").isNull();
    }
}
