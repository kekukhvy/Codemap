package dev.codemap.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IndexedMethod#visibility()} defaults to {@link Visibility#PACKAGE}
 * for callers that do not supply one — the convenience constructor used by
 * parsing sites that predate visibility, and every existing test written
 * before it existed. This is the degrade-never-fail default (spec §5.1):
 * an unknown visibility must never be assumed {@code PUBLIC}.
 */
class IndexedMethodVisibilityTest {

    private static final IndexedMethod METHOD = new IndexedMethod(
            "com.example.Service#run()", "com.example.Service", "run", "run() : void",
            "Service.java", 5, 7, null, "void run() {}", false);

    @Test
    @DisplayName("defaults to PACKAGE when the convenience constructor is used")
    void defaultsToPackageVisibility() {
        assertThat(METHOD.visibility()).isEqualTo(Visibility.PACKAGE);
    }

    @Test
    @DisplayName("carries an explicitly supplied visibility")
    void carriesExplicitVisibility() {
        IndexedMethod publicMethod = new IndexedMethod(
                "com.example.Service#run()", "com.example.Service", "run", "run() : void",
                "Service.java", 5, 7, null, "void run() {}", false, Visibility.PUBLIC, ChangeStatus.UNCHANGED);

        assertThat(publicMethod.visibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    @DisplayName("withStatus preserves the visibility of the original")
    void withStatusPreservesVisibility() {
        IndexedMethod withStatus = METHOD.withStatus(ChangeStatus.CHANGED);

        assertThat(withStatus.visibility()).isEqualTo(Visibility.PACKAGE);
    }
}
