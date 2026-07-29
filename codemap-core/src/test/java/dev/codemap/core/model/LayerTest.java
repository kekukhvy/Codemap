package dev.codemap.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LayerTest {

    @Nested
    @DisplayName("from the package path")
    class FromPackage {

        @ParameterizedTest
        @CsvSource({
                "dev.app.domain.task,        DOMAIN",
                "dev.app.application.task,   APPLICATION",
                "dev.app.infrastructure.jpa, INFRASTRUCTURE",
                "dev.app.api.task,           ENTRY",
                "dev.app.common.util,        SUPPORT"
        })
        @DisplayName("reads the layer from a conventional segment")
        void readsConventionalSegments(String packageName, Layer expected) {
            assertThat(Layer.fromPackage(packageName)).isEqualTo(expected);
        }

        @Test
        @DisplayName("prefers the most specific segment over an earlier one")
        void prefersMostSpecificSegment() {
            assertThat(Layer.fromPackage("dev.app.api.domain")).isEqualTo(Layer.DOMAIN);
        }

        @Test
        @DisplayName("returns UNKNOWN rather than guessing")
        void returnsUnknownWhenNothingMatches() {
            assertThat(Layer.fromPackage("dev.app.feature.dashboard")).isEqualTo(Layer.UNKNOWN);
            assertThat(Layer.fromPackage("")).isEqualTo(Layer.UNKNOWN);
            assertThat(Layer.fromPackage(null)).isEqualTo(Layer.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("falling back to the type name")
    class FromTypeName {

        @ParameterizedTest
        @CsvSource({
                "DashboardView,     ENTRY",
                "TaskController,    ENTRY",
                "TaskRepository,    INFRASTRUCTURE",
                "CreateTaskUseCase, APPLICATION",
                "TaskResponse,      SUPPORT"
        })
        @DisplayName("reads the layer from a conventional suffix when the package is silent")
        void readsConventionalSuffixes(String simpleName, Layer expected) {
            assertThat(Layer.fromPackageOrName("dev.app.feature.dashboard", simpleName)).isEqualTo(expected);
        }

        @Test
        @DisplayName("lets a decisive package win over the type name")
        void packageWinsOverName() {
            assertThat(Layer.fromPackageOrName("dev.app.domain.task", "TaskRepository"))
                    .as("a port declared in the domain belongs to the domain")
                    .isEqualTo(Layer.DOMAIN);
        }

        @Test
        @DisplayName("stays UNKNOWN when neither signal is decisive")
        void staysUnknownWithoutSignal() {
            assertThat(Layer.fromPackageOrName("dev.app.feature.dashboard", "StatCard"))
                    .isEqualTo(Layer.UNKNOWN);
        }
    }
}
