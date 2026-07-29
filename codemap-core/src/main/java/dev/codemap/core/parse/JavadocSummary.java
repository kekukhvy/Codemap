package dev.codemap.core.parse;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;

import java.util.regex.Pattern;

/**
 * Extracts the first sentence of a Javadoc comment.
 *
 * <p>Only the first sentence is kept: the map shows it as a one-line label beside
 * a class or method, where a full comment would not fit and would not help.
 */
final class JavadocSummary {

    /** Inline tags such as {@code {@link Foo}}, reduced to their last word. */
    private static final Pattern INLINE_TAG = Pattern.compile("\\{@\\w+\\s+([^}]+)}");

    /** A sentence ends at the first period followed by whitespace or end of text. */
    private static final Pattern FIRST_SENTENCE = Pattern.compile("^(.*?\\.)(\\s|$)", Pattern.DOTALL);

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private static final int MAX_LENGTH = 200;
    private static final String ELLIPSIS = "…";

    private JavadocSummary() {
    }

    /**
     * Reads the summary sentence from a declaration's Javadoc.
     *
     * @param declaration any declaration that may carry Javadoc
     * @return the first sentence, or {@code null} when there is no usable comment
     */
    static String of(BodyDeclaration<?> declaration, OrphanJavadocIndex orphans) {
        if (!(declaration instanceof NodeWithJavadoc<?> documented)) {
            return null;
        }
        return documented.getJavadoc()
                .map(javadoc -> summarise(javadoc.getDescription().toText()))
                .filter(text -> !text.isBlank())
                .or(() -> detachedJavadoc(declaration, orphans))
                .orElse(null);
    }

    /**
     * Recovers a Javadoc block separated from its declaration by a blank line.
     *
     * <p>JavaParser only attaches a comment that directly precedes the
     * declaration, so a stray blank line between {@code *&#47;} and the type leaves
     * the comment orphaned. That formatting is uncommon but does occur in real
     * code, and dropping the documentation because of a blank line would lose
     * information the author clearly meant to attach.
     */
    private static java.util.Optional<String> detachedJavadoc(
            BodyDeclaration<?> declaration, OrphanJavadocIndex orphans) {
        return orphans.findFor(declaration)
                .map(comment -> comment.parse().getDescription().toText())
                .map(JavadocSummary::summarise)
                .filter(text -> !text.isBlank());
    }

    /**
     * Reduces a Javadoc description to a single clean line.
     *
     * <p>Inline tags keep their target text — {@code {@link Task}} becomes
     * {@code Task} — because dropping them would leave sentences with holes in
     * them, and rendering the raw tag would be noise.
     */
    private static String summarise(String description) {
        String text = INLINE_TAG.matcher(description).replaceAll(matchResult -> {
            String content = matchResult.group(1).trim();
            int lastSpace = content.lastIndexOf(' ');
            return java.util.regex.Matcher.quoteReplacement(
                    lastSpace < 0 ? content : content.substring(lastSpace + 1));
        });

        text = HTML_TAG.matcher(text).replaceAll(" ");
        text = WHITESPACE_RUN.matcher(text).replaceAll(" ").trim();

        var sentence = FIRST_SENTENCE.matcher(text);
        if (sentence.find()) {
            text = sentence.group(1).trim();
        }
        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH).trim() + ELLIPSIS : text;
    }
}
