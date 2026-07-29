package dev.codemap.core.diff;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LineRange} is the shared unit both hunk parsing and method-status
 * assignment work in: an inclusive 1-based line span with an overlap test.
 */
class LineRangeTest {

    @Test
    void overlapsWhenOneRangeStartsInsideTheOther() {
        LineRange method = new LineRange(10, 20);
        LineRange hunk = new LineRange(15, 17);

        assertThat(method.overlaps(hunk)).isTrue();
        assertThat(hunk.overlaps(method)).isTrue();
    }

    @Test
    void overlapsAtASharedBoundaryLine() {
        LineRange method = new LineRange(10, 20);
        LineRange hunk = new LineRange(20, 25);

        assertThat(method.overlaps(hunk)).isTrue();
    }

    @Test
    void doesNotOverlapWhenRangesAreDisjoint() {
        LineRange method = new LineRange(10, 20);
        LineRange hunk = new LineRange(21, 25);

        assertThat(method.overlaps(hunk)).isFalse();
    }

    @Test
    void aRangeFullyInsideAnotherIsContained() {
        LineRange method = new LineRange(10, 20);
        LineRange hunk = new LineRange(12, 14);

        assertThat(hunk.isContainedIn(method)).isTrue();
        assertThat(method.isContainedIn(hunk)).isFalse();
    }
}
