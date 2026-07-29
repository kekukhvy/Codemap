package dev.codemap.core.diff;

import dev.codemap.core.model.ChangeStatus;
import dev.codemap.core.model.IndexedMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MethodStatusAssigner} maps diff line ranges onto the method ranges
 * from indexing (spec §5): a method whose lines fall inside a hunk is
 * {@code changed}, one whose lines are entirely new is {@code added}, and
 * removed methods are recovered from the diff's old-file ranges alone, since
 * they have no current declaration to look up.
 */
class MethodStatusAssignerTest {

    private static final String FILE = "src/main/java/com/example/Service.java";
    private static final String CLASS_ID = "com.example.Service";

    private final MethodStatusAssigner assigner = new MethodStatusAssigner();

    @Nested
    @DisplayName("changed")
    class Changed {

        @Test
        @DisplayName("marks exactly the method whose lines overlap a diff hunk")
        void marksOverlappingMethodAsChanged() {
            IndexedMethod touched = method("touched", 10, 15);
            IndexedMethod untouched = method("untouched", 20, 25);
            FileDiff diff = new FileDiff(FILE, FILE, List.of(new LineRange(12, 12)), List.of(), false, false);

            MethodStatusAssignment assignment = assigner.assign(List.of(touched, untouched), List.of(diff));

            assertThat(assignment.statusOf(touched.id())).isEqualTo(ChangeStatus.CHANGED);
            assertThat(assignment.statusOf(untouched.id())).isEqualTo(ChangeStatus.UNCHANGED);
        }

        @Test
        @DisplayName("a hunk spanning two methods marks both changed")
        void hunkSpanningTwoMethodsMarksBothChanged() {
            IndexedMethod first = method("first", 10, 15);
            IndexedMethod second = method("second", 16, 20);
            FileDiff diff = new FileDiff(FILE, FILE, List.of(new LineRange(14, 17)), List.of(), false, false);

            MethodStatusAssignment assignment = assigner.assign(List.of(first, second), List.of(diff));

            assertThat(assignment.statusOf(first.id())).isEqualTo(ChangeStatus.CHANGED);
            assertThat(assignment.statusOf(second.id())).isEqualTo(ChangeStatus.CHANGED);
        }
    }

    @Nested
    @DisplayName("added")
    class Added {

        @Test
        @DisplayName("marks a method added when every one of its lines is inside a changed range")
        void marksFullyNewMethodAsAdded() {
            IndexedMethod brandNew = method("brandNew", 10, 15);
            FileDiff diff = new FileDiff(FILE, FILE, List.of(new LineRange(1, 20)), List.of(), true, false);

            MethodStatusAssignment assignment = assigner.assign(List.of(brandNew), List.of(diff));

            assertThat(assignment.statusOf(brandNew.id())).isEqualTo(ChangeStatus.ADDED);
        }

        @Test
        @DisplayName("every method in a brand new file is added")
        void everyMethodInANewFileIsAdded() {
            IndexedMethod one = method("one", 3, 5);
            IndexedMethod two = method("two", 7, 9);
            FileDiff diff = new FileDiff(FILE, FILE, List.of(new LineRange(1, 9)), List.of(), true, false);

            MethodStatusAssignment assignment = assigner.assign(List.of(one, two), List.of(diff));

            assertThat(assignment.statusOf(one.id())).isEqualTo(ChangeStatus.ADDED);
            assertThat(assignment.statusOf(two.id())).isEqualTo(ChangeStatus.ADDED);
        }

        @Test
        @DisplayName("a method only partially inside a changed range is changed, not added")
        void partiallyOverlappingMethodIsChangedNotAdded() {
            IndexedMethod method = method("method", 10, 20);
            FileDiff diff = new FileDiff(FILE, FILE, List.of(new LineRange(18, 25)), List.of(), false, false);

            MethodStatusAssignment assignment = assigner.assign(List.of(method), List.of(diff));

            assertThat(assignment.statusOf(method.id())).isEqualTo(ChangeStatus.CHANGED);
        }
    }

    @Nested
    @DisplayName("removed")
    class Removed {

        @Test
        @DisplayName("a deleted file yields one removed placeholder per old-file range that is not adjacent to a survivor")
        void deletedFileYieldsRemovedPlaceholder() {
            FileDiff diff = new FileDiff(FILE, FILE, List.of(), List.of(new LineRange(1, 10)), false, true);

            MethodStatusAssignment assignment = assigner.assign(List.of(), List.of(diff));

            assertThat(assignment.removedRanges()).singleElement().satisfies(removed -> {
                assertThat(removed.file()).isEqualTo(FILE);
                assertThat(removed.range()).isEqualTo(new LineRange(1, 10));
            });
        }
    }

    @Nested
    @DisplayName("unchanged")
    class Unchanged {

        @Test
        @DisplayName("a method in a file with no diff at all is unchanged")
        void methodWithNoDiffIsUnchanged() {
            IndexedMethod method = method("method", 1, 5);

            MethodStatusAssignment assignment = assigner.assign(List.of(method), List.of());

            assertThat(assignment.statusOf(method.id())).isEqualTo(ChangeStatus.UNCHANGED);
        }

        @Test
        @DisplayName("a method in a changed file whose own lines are untouched is unchanged")
        void methodOutsideEveryHunkIsUnchanged() {
            IndexedMethod method = method("method", 30, 40);
            FileDiff diff = new FileDiff(FILE, FILE, List.of(new LineRange(1, 5)), List.of(), false, false);

            MethodStatusAssignment assignment = assigner.assign(List.of(method), List.of(diff));

            assertThat(assignment.statusOf(method.id())).isEqualTo(ChangeStatus.UNCHANGED);
        }
    }

    @Nested
    @DisplayName("renames")
    class Renames {

        @Test
        @DisplayName("a renamed file's changed ranges are keyed by the new path, where the method now lives")
        void rangesAreKeyedByTheNewPath() {
            String newFile = "src/main/java/com/example/Renamed.java";
            IndexedMethod method = method("method", 10, 15);
            IndexedMethod renamedMethod = new IndexedMethod(
                    "com.example.Renamed#method()", CLASS_ID, "method", "method()", newFile,
                    10, 15, null, "void method() {}", false);
            FileDiff diff = new FileDiff(newFile, FILE, List.of(new LineRange(12, 12)), List.of(), false, false);

            MethodStatusAssignment assignment = assigner.assign(List.of(renamedMethod), List.of(diff));

            assertThat(assignment.statusOf(renamedMethod.id())).isEqualTo(ChangeStatus.CHANGED);
        }
    }

    private IndexedMethod method(String name, int lineStart, int lineEnd) {
        String id = CLASS_ID + "#" + name + "()";
        return new IndexedMethod(id, CLASS_ID, name, name + "()", FILE, lineStart, lineEnd, null,
                "void " + name + "() {}", false);
    }
    @Nested
    @DisplayName("modification versus deletion")
    class ModificationVersusDeletion {

        /**
         * `--unified=0` reports a modified line as a deletion plus an addition at
         * the same place. Treating the deleted side as a removed method would
         * invent a phantom removal for every ordinary edit — and the method is
         * already reported as changed.
         */
        @Test
        @DisplayName("a rewritten line is not also reported as a removed method")
        void modifiedLineIsNotAlsoRemoved() {
            FileDiff modification = new FileDiff(
                    FILE, FILE, List.of(new LineRange(4, 4)), List.of(new LineRange(4, 4)), false, false);

            MethodStatusAssignment assignment =
                    assigner.assign(List.of(method("rewritten", 4, 4)), List.of(modification));

            assertThat(assignment.removedRanges())
                    .as("the line was rewritten, not deleted")
                    .isEmpty();
        }

        @Test
        @DisplayName("a genuinely deleted range is still reported as removed")
        void deletedRangeIsStillRemoved() {
            FileDiff deletion = new FileDiff(
                    FILE, FILE, List.of(), List.of(new LineRange(4, 6)), false, false);

            MethodStatusAssignment assignment =
                    assigner.assign(List.of(method("survivor", 20, 25)), List.of(deletion));

            assertThat(assignment.removedRanges()).hasSize(1);
        }
    }

}
