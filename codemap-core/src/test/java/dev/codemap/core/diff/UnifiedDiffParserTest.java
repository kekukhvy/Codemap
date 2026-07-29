package dev.codemap.core.diff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UnifiedDiffParser} turns {@code git diff --unified=0} output into
 * per-file line ranges. The header math is the whole point of this class: a
 * hunk header gives new-file ranges for additions and old-file ranges for
 * deletions, and getting that backwards mis-attributes a change to whatever
 * method happens to sit at the same line number on the other side.
 */
class UnifiedDiffParserTest {

    private final UnifiedDiffParser parser = new UnifiedDiffParser();

    @Nested
    @DisplayName("hunk header math")
    class HunkHeaderMath {

        @Test
        @DisplayName("a pure addition is reported as a new-file changed range")
        void pureAddition() {
            String diff = """
                    diff --git a/A.java b/A.java
                    --- a/A.java
                    +++ b/A.java
                    @@ -5,0 +6 @@ line5
                    +line6
                    """;

            List<FileDiff> diffs = parser.parse(diff);

            assertThat(diffs).singleElement().satisfies(fileDiff -> {
                assertThat(fileDiff.path()).isEqualTo("A.java");
                assertThat(fileDiff.changedRanges()).containsExactly(new LineRange(6, 6));
                assertThat(fileDiff.removedRanges()).isEmpty();
            });
        }

        @Test
        @DisplayName("a pure deletion is reported as an old-file removed range, not a changed one")
        void pureDeletion() {
            String diff = """
                    diff --git a/A.java b/A.java
                    --- a/A.java
                    +++ b/A.java
                    @@ -2 +1,0 @@ line1
                    -CHANGED
                    """;

            List<FileDiff> diffs = parser.parse(diff);

            assertThat(diffs).singleElement().satisfies(fileDiff -> {
                assertThat(fileDiff.changedRanges()).isEmpty();
                assertThat(fileDiff.removedRanges()).containsExactly(new LineRange(2, 2));
            });
        }

        @Test
        @DisplayName("a replacement hunk reports both the old removed range and the new changed range")
        void replacement() {
            String diff = """
                    diff --git a/A.java b/A.java
                    --- a/A.java
                    +++ b/A.java
                    @@ -2 +2 @@ line1
                    -line2
                    +CHANGED
                    """;

            List<FileDiff> diffs = parser.parse(diff);

            assertThat(diffs).singleElement().satisfies(fileDiff -> {
                assertThat(fileDiff.changedRanges()).containsExactly(new LineRange(2, 2));
                assertThat(fileDiff.removedRanges()).containsExactly(new LineRange(2, 2));
            });
        }

        @Test
        @DisplayName("a multi-line hunk uses the count to compute the end line")
        void multiLineHunk() {
            String diff = """
                    diff --git a/A.java b/A.java
                    --- a/A.java
                    +++ b/A.java
                    @@ -10,3 +10,5 @@ void foo() {
                    -old1
                    -old2
                    -old3
                    +new1
                    +new2
                    +new3
                    +new4
                    +new5
                    """;

            List<FileDiff> diffs = parser.parse(diff);

            assertThat(diffs).singleElement().satisfies(fileDiff -> {
                assertThat(fileDiff.changedRanges()).containsExactly(new LineRange(10, 14));
                assertThat(fileDiff.removedRanges()).containsExactly(new LineRange(10, 12));
            });
        }
    }

    @Nested
    @DisplayName("file identity")
    class FileIdentity {

        @Test
        @DisplayName("a new file has no old path and every line is a changed range")
        void newFile() {
            String diff = """
                    diff --git a/C.java b/C.java
                    new file mode 100644
                    index 0000000..dd3d46e
                    --- /dev/null
                    +++ b/C.java
                    @@ -0,0 +1,2 @@
                    +newfile1
                    +newfile2
                    """;

            List<FileDiff> diffs = parser.parse(diff);

            assertThat(diffs).singleElement().satisfies(fileDiff -> {
                assertThat(fileDiff.path()).isEqualTo("C.java");
                assertThat(fileDiff.isNewFile()).isTrue();
                assertThat(fileDiff.changedRanges()).containsExactly(new LineRange(1, 2));
            });
        }

        @Test
        @DisplayName("a deleted file has every old line reported as removed")
        void deletedFile() {
            String diff = """
                    diff --git a/B.java b/B.java
                    deleted file mode 100644
                    index cf9e30f..0000000
                    --- a/B.java
                    +++ /dev/null
                    @@ -1,2 +0,0 @@
                    -line1
                    -line2
                    """;

            List<FileDiff> diffs = parser.parse(diff);

            assertThat(diffs).singleElement().satisfies(fileDiff -> {
                assertThat(fileDiff.path()).isEqualTo("B.java");
                assertThat(fileDiff.isDeletedFile()).isTrue();
                assertThat(fileDiff.removedRanges()).containsExactly(new LineRange(1, 2));
            });
        }

        @Test
        @DisplayName("a rename carries the old path forward so removed methods still resolve")
        void renamedFile() {
            String diff = """
                    diff --git a/Old.java b/New.java
                    similarity index 92%
                    rename from Old.java
                    rename to New.java
                    --- a/Old.java
                    +++ b/New.java
                    @@ -5,0 +6 @@ line5
                    +line6
                    """;

            List<FileDiff> diffs = parser.parse(diff);

            assertThat(diffs).singleElement().satisfies(fileDiff -> {
                assertThat(fileDiff.path()).isEqualTo("New.java");
                assertThat(fileDiff.oldPath()).isEqualTo("Old.java");
                assertThat(fileDiff.isRenamed()).isTrue();
                assertThat(fileDiff.changedRanges()).containsExactly(new LineRange(6, 6));
            });
        }
    }

    @Nested
    @DisplayName("multiple files and hunks")
    class MultipleFilesAndHunks {

        @Test
        @DisplayName("accumulates every hunk of a file across the whole diff")
        void accumulatesHunksPerFile() {
            String diff = """
                    diff --git a/A.java b/A.java
                    --- a/A.java
                    +++ b/A.java
                    @@ -2 +2 @@ line1
                    -old
                    +new
                    @@ -20 +20 @@ line20
                    -old2
                    +new2
                    """;

            List<FileDiff> diffs = parser.parse(diff);

            assertThat(diffs).singleElement().satisfies(fileDiff ->
                    assertThat(fileDiff.changedRanges())
                            .containsExactly(new LineRange(2, 2), new LineRange(20, 20)));
        }

        @Test
        @DisplayName("keeps files in separate entries")
        void separatesFiles() {
            String diff = """
                    diff --git a/A.java b/A.java
                    --- a/A.java
                    +++ b/A.java
                    @@ -2 +2 @@ line1
                    -old
                    +new
                    diff --git a/B.java b/B.java
                    --- a/B.java
                    +++ b/B.java
                    @@ -3 +3 @@ line3
                    -old3
                    +new3
                    """;

            List<FileDiff> diffs = parser.parse(diff);

            assertThat(diffs).extracting(FileDiff::path).containsExactly("A.java", "B.java");
        }
    }

    @Nested
    @DisplayName("degradation")
    class Degradation {

        @Test
        @DisplayName("empty diff text yields no file diffs")
        void emptyDiff() {
            assertThat(parser.parse("")).isEmpty();
        }

        @Test
        @DisplayName("non-Java files are still parsed here; filtering is the caller's job")
        void doesNotFilterByExtension() {
            String diff = """
                    diff --git a/notes.txt b/notes.txt
                    --- a/notes.txt
                    +++ b/notes.txt
                    @@ -1 +1 @@
                    -old
                    +new
                    """;

            assertThat(parser.parse(diff)).extracting(FileDiff::path).containsExactly("notes.txt");
        }

        @Test
        @DisplayName("a file git treats as binary has no hunks, and no crash, since there are no line ranges to report")
        void skipsBinaryFileWithNoCrash() {
            String diff = """
                    diff --git a/image.png b/image.png
                    index e82b4f9..7ebdcf5 100644
                    Binary files a/image.png and b/image.png differ
                    """;

            List<FileDiff> diffs = parser.parse(diff);

            assertThat(diffs).singleElement().satisfies(fileDiff -> {
                assertThat(fileDiff.path()).isEqualTo("image.png");
                assertThat(fileDiff.changedRanges()).isEmpty();
                assertThat(fileDiff.removedRanges()).isEmpty();
            });
        }

        @Test
        @DisplayName("a newly added file git treats as binary is still identified from the diff header, not /dev/null")
        void newBinaryFileUsesGitHeaderPath() {
            String diff = """
                    diff --git a/notes.md b/notes.md
                    new file mode 100644
                    index 0000000..ac1bcdd
                    Binary files /dev/null and b/notes.md differ
                    """;

            List<FileDiff> diffs = parser.parse(diff);

            assertThat(diffs).singleElement().satisfies(fileDiff -> {
                assertThat(fileDiff.path()).isEqualTo("notes.md");
                assertThat(fileDiff.isNewFile()).isTrue();
            });
        }

        @Test
        @DisplayName("a real file after a binary one in the same diff is still parsed correctly")
        void realFileAfterBinaryFileStillParses() {
            String diff = """
                    diff --git a/image.png b/image.png
                    index e82b4f9..7ebdcf5 100644
                    Binary files a/image.png and b/image.png differ
                    diff --git a/A.java b/A.java
                    --- a/A.java
                    +++ b/A.java
                    @@ -2 +2 @@ line1
                    -old
                    +new
                    """;

            List<FileDiff> diffs = parser.parse(diff);

            assertThat(diffs).extracting(FileDiff::path).containsExactly("image.png", "A.java");
        }
    }
}
