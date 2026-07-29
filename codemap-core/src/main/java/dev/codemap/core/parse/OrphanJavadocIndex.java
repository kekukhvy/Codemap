package dev.codemap.core.parse;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Finds Javadoc comments that a declaration should own but the parser left loose.
 *
 * <p>JavaParser attaches a comment only when it directly precedes the declaration.
 * A blank line between {@code *&#47;} and the declaration orphans it — uncommon
 * formatting, but present in real code, and the author plainly meant the comment
 * to belong to what follows.
 *
 * <p>Comments are indexed by the line they end on, so a declaration can claim the
 * one that ends just above it. Attribution stays conservative: a gap of more than
 * {@link #MAX_BLANK_LINES} blank lines is treated as a comment that belongs to
 * nothing, rather than guessing across arbitrary distance.
 */
final class OrphanJavadocIndex {

    /** How many blank lines may sit between a comment and the declaration it describes. */
    private static final int MAX_BLANK_LINES = 3;

    private final Map<Integer, JavadocComment> byEndLine;

    private OrphanJavadocIndex(Map<Integer, JavadocComment> byEndLine) {
        this.byEndLine = byEndLine;
    }

    /**
     * Indexes the unattached Javadoc comments of a compilation unit.
     *
     * @param unit the parsed file
     * @return an index, empty when every comment is already attached
     */
    static OrphanJavadocIndex of(CompilationUnit unit) {
        Map<Integer, JavadocComment> byEndLine = new HashMap<>();
        for (Comment comment : unit.getAllComments()) {
            if (!comment.isJavadocComment() || comment.getCommentedNode().isPresent()) {
                continue;
            }
            comment.getEnd().ifPresent(position ->
                    byEndLine.put(position.line, comment.asJavadocComment()));
        }
        return new OrphanJavadocIndex(byEndLine);
    }

    /**
     * Finds the comment that describes a declaration starting at a given line.
     *
     * @param declarationLine first line of the declaration
     * @return the comment, or empty when nothing plausible precedes it
     */
    Optional<JavadocComment> findFor(int declarationLine) {
        for (int gap = 1; gap <= MAX_BLANK_LINES + 1; gap++) {
            JavadocComment candidate = byEndLine.get(declarationLine - gap);
            if (candidate != null) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the comment describing a node, using its own position.
     *
     * @param node the declaration
     * @return the comment, or empty when the node has no position or no comment
     */
    Optional<JavadocComment> findFor(Node node) {
        return node.getBegin().flatMap(position -> findFor(position.line));
    }
}
