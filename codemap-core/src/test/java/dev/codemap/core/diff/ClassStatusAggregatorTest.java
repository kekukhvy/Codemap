package dev.codemap.core.diff;

import dev.codemap.core.model.ChangeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ClassStatusAggregator} derives a class's status from its methods
 * (spec §5: "class status is derived; a class is changed if any of its
 * methods changed"), in the priority order a reviewer would care about most.
 */
class ClassStatusAggregatorTest {

    private final ClassStatusAggregator aggregator = new ClassStatusAggregator();

    @Test
    @DisplayName("a class with a changed method is itself changed")
    void changedMethodMakesClassChanged() {
        ChangeStatus status = aggregator.aggregate(List.of(ChangeStatus.UNCHANGED, ChangeStatus.CHANGED));

        assertThat(status).isEqualTo(ChangeStatus.CHANGED);
    }

    @Test
    @DisplayName("a class with only added methods is itself added")
    void onlyAddedMethodsMakeClassAdded() {
        ChangeStatus status = aggregator.aggregate(List.of(ChangeStatus.ADDED, ChangeStatus.ADDED));

        assertThat(status).isEqualTo(ChangeStatus.ADDED);
    }

    @Test
    @DisplayName("a class with only affected methods is itself affected")
    void onlyAffectedMethodsMakeClassAffected() {
        ChangeStatus status = aggregator.aggregate(List.of(ChangeStatus.UNCHANGED, ChangeStatus.AFFECTED));

        assertThat(status).isEqualTo(ChangeStatus.AFFECTED);
    }

    @Test
    @DisplayName("a class where every method is unchanged is itself unchanged")
    void allUnchangedMakesClassUnchanged() {
        ChangeStatus status = aggregator.aggregate(List.of(ChangeStatus.UNCHANGED, ChangeStatus.UNCHANGED));

        assertThat(status).isEqualTo(ChangeStatus.UNCHANGED);
    }

    @Test
    @DisplayName("changed outranks added and affected when a class has a mix")
    void changedOutranksAddedAndAffected() {
        ChangeStatus status = aggregator.aggregate(List.of(ChangeStatus.ADDED, ChangeStatus.AFFECTED, ChangeStatus.CHANGED));

        assertThat(status).isEqualTo(ChangeStatus.CHANGED);
    }

    @Test
    @DisplayName("a class with no methods at all is unchanged, not absent")
    void noMethodsIsUnchanged() {
        assertThat(aggregator.aggregate(List.of())).isEqualTo(ChangeStatus.UNCHANGED);
    }
}
