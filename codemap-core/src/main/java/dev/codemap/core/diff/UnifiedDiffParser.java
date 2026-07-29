package dev.codemap.core.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code git diff --unified=0} output into per-file line ranges.
 *
 * <p>With zero context lines, every hunk is exactly the lines that changed —
 * there is no surrounding unchanged text to strip out. The one thing this class
 * exists to get right is which side of the hunk header each range comes from:
 * additions land on the new-file numbers, deletions on the old-file numbers.
 * Using the wrong side attributes a change to whichever method happens to sit
 * at that line number in the other file state.
 *
 * <p>A file git treats as binary (by content sniffing, not just extension —
 * this catches real {@code .java} and {@code .md} files with the right bytes)
 * has no {@code ---}/{@code +++} or hunk lines at all, only
 * {@code Binary files ... differ}. Its path always comes from the
 * {@code diff --git} header line, which every entry has regardless.
 */
public final class UnifiedDiffParser {

    private static final String DIFF_HEADER_PREFIX = "diff --git a/";
    private static final Pattern DIFF_HEADER =
            Pattern.compile("^diff --git a/(.+) b/(.+)$");
    private static final String NEW_FILE_MARKER = "new file mode";
    private static final String DELETED_FILE_MARKER = "deleted file mode";
    private static final String RENAME_FROM_PREFIX = "rename from ";
    private static final String RENAME_TO_PREFIX = "rename to ";
    private static final String OLD_PATH_PREFIX = "--- ";
    private static final String NEW_PATH_PREFIX = "+++ ";
    private static final String DEV_NULL = "/dev/null";
    private static final String OLD_PATH_MARKER = "a/";
    private static final String NEW_PATH_MARKER = "b/";
    private static final String HUNK_PREFIX = "@@ ";

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

    /**
     * Parses full diff text into one {@link FileDiff} per file it touches.
     *
     * @param diffText raw output of {@code git diff --unified=0}
     * @return file diffs in the order git reported them
     */
    public List<FileDiff> parse(String diffText) {
        List<FileDiff> results = new ArrayList<>();
        FileDiffBuilder current = null;

        for (String line : diffText.split("\n", -1)) {
            if (line.startsWith(DIFF_HEADER_PREFIX)) {
                flush(results, current);
                current = new FileDiffBuilder();
                applyDiffHeaderPaths(current, line);
                continue;
            }
            if (current == null) {
                continue;
            }
            if (!applyMetadataLine(current, line)) {
                applyHunkLine(current, line);
            }
        }
        flush(results, current);
        return results;
    }

    /**
     * Seeds the builder's path from the {@code diff --git} header, the one line
     * every entry has. {@code ---}/{@code +++} or a rename overwrite this later
     * when present; a binary file has neither, so this is the only path it gets.
     */
    private void applyDiffHeaderPaths(FileDiffBuilder builder, String line) {
        Matcher matcher = DIFF_HEADER.matcher(line);
        if (matcher.matches()) {
            builder.headerOldPath = matcher.group(1);
            builder.headerPath = matcher.group(2);
        }
    }

    private void flush(List<FileDiff> results, FileDiffBuilder builder) {
        if (builder != null) {
            results.add(builder.build());
        }
    }

    /** @return {@code true} when the line was header metadata, not hunk content */
    private boolean applyMetadataLine(FileDiffBuilder builder, String line) {
        if (line.startsWith(NEW_FILE_MARKER)) {
            builder.newFile = true;
        } else if (line.startsWith(DELETED_FILE_MARKER)) {
            builder.deletedFile = true;
        } else if (line.startsWith(RENAME_FROM_PREFIX)) {
            builder.oldPath = line.substring(RENAME_FROM_PREFIX.length());
        } else if (line.startsWith(RENAME_TO_PREFIX)) {
            builder.path = line.substring(RENAME_TO_PREFIX.length());
        } else if (line.startsWith(OLD_PATH_PREFIX)) {
            applyOldPath(builder, line);
        } else if (line.startsWith(NEW_PATH_PREFIX)) {
            applyNewPath(builder, line);
        } else {
            return false;
        }
        return true;
    }

    private void applyOldPath(FileDiffBuilder builder, String line) {
        String path = line.substring(OLD_PATH_PREFIX.length());
        if (!path.equals(DEV_NULL) && builder.oldPath == null) {
            builder.oldPath = stripMarker(path, OLD_PATH_MARKER);
        }
    }

    private void applyNewPath(FileDiffBuilder builder, String line) {
        String path = line.substring(NEW_PATH_PREFIX.length());
        if (!path.equals(DEV_NULL)) {
            builder.path = stripMarker(path, NEW_PATH_MARKER);
        }
    }

    private String stripMarker(String path, String marker) {
        return path.startsWith(marker) ? path.substring(marker.length()) : path;
    }

    private void applyHunkLine(FileDiffBuilder builder, String line) {
        if (!line.startsWith(HUNK_PREFIX)) {
            return;
        }
        Matcher matcher = HUNK_HEADER.matcher(line);
        if (!matcher.find()) {
            return;
        }
        addRemovedRange(builder, matcher);
        addChangedRange(builder, matcher);
    }

    private void addRemovedRange(FileDiffBuilder builder, Matcher matcher) {
        int oldStart = Integer.parseInt(matcher.group(1));
        int oldCount = parseCount(matcher.group(2));
        if (oldCount > 0) {
            builder.removedRanges.add(new LineRange(oldStart, oldStart + oldCount - 1));
        }
    }

    private void addChangedRange(FileDiffBuilder builder, Matcher matcher) {
        int newStart = Integer.parseInt(matcher.group(3));
        int newCount = parseCount(matcher.group(4));
        if (newCount > 0) {
            builder.changedRanges.add(new LineRange(newStart, newStart + newCount - 1));
        }
    }

    /** A missing count means exactly one line, per the unified-diff header format. */
    private int parseCount(String group) {
        return group == null ? 1 : Integer.parseInt(group);
    }

    private static final class FileDiffBuilder {
        private String headerPath;
        private String headerOldPath;
        private String path;
        private String oldPath;
        private boolean newFile;
        private boolean deletedFile;
        private final List<LineRange> changedRanges = new ArrayList<>();
        private final List<LineRange> removedRanges = new ArrayList<>();

        private FileDiff build() {
            String resolvedPath = firstNonNull(path, oldPath, headerPath);
            String resolvedOldPath = firstNonNull(oldPath, resolvedPath, headerOldPath);
            return new FileDiff(resolvedPath, resolvedOldPath, changedRanges, removedRanges, newFile, deletedFile);
        }

        private static String firstNonNull(String first, String second, String third) {
            return first != null ? first : Objects.requireNonNullElse(second, third);
        }
    }
}
