package dev.codemap.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The index embeds real Java source text, which routinely contains
 * {@code </script>}, backslashes, and line/paragraph separators. Whatever is
 * embedded inside a {@code <script>} tag must survive intact and must never
 * let the browser think the script block ended early.
 */
class JsonScriptEscaperTest {

    private static final char LINE_SEPARATOR_CHAR = 0x2028;
    private static final char PARAGRAPH_SEPARATOR_CHAR = 0x2029;

    private static final String SOURCE_FIELD = "source";

    private final JsonScriptEscaper escaper = new JsonScriptEscaper();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Reads the escaped payload back the way the browser does — as JSON — and
     * returns the {@code source} value.
     *
     * <p>Asserting only that the dangerous substring is gone is not enough: an
     * escape that removes it but is not itself legal JSON breaks the entire
     * report, which is a worse failure than the one being prevented. Every case
     * therefore has to survive a real parse and come back byte-for-byte.
     */
    private String sourceAfterRoundTrip(String json) {
        String escaped = escaper.escapeForScriptContext(json);
        assertThatCode(() -> mapper.readTree(escaped))
                .describedAs("escaped payload must still be valid JSON: %s", escaped)
                .doesNotThrowAnyException();
        JsonNode parsed = readTree(escaped);
        return parsed.get(SOURCE_FIELD).asText();
    }

    private JsonNode readTree(String escaped) {
        try {
            return mapper.readTree(escaped);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse the escaped payload", e);
        }
    }

    @Test
    @DisplayName("escapes a closing script tag so it cannot terminate the embedding script block")
    void escapesClosingScriptTag() {
        String json = "{\"source\":\"a string with </script> inside\"}";

        String escaped = escaper.escapeForScriptContext(json);

        assertThat(escaped).doesNotContain("</script>");
        assertThat(sourceAfterRoundTrip(json)).isEqualTo("a string with </script> inside");
    }

    @Test
    @DisplayName("escapes an opening script tag the same way, for symmetry and safety")
    void escapesOpeningScriptTag() {
        String json = "{\"source\":\"<script>alert(1)</script>\"}";

        String escaped = escaper.escapeForScriptContext(json);

        assertThat(escaped).doesNotContain("<script>");
        assertThat(escaped).doesNotContain("</script>");
    }

    /**
     * The HTML tokenizer ends a script element on {@code </script} followed by
     * whitespace, {@code /} or {@code >}, case-insensitively — so an escaper
     * that matches only the literal {@code </script>} lets every one of these
     * through, and a comment in an analysed file becomes script execution in
     * the report. None of them may survive in a form the tokenizer can act on.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "</script foo>",
            "</SCRIPT>",
            "</ScRiPt>",
            "</script/>",
            "</script\tfoo>",
            "</script>",
            "<SCRIPT src=x>",
            "<!-- not a comment -->"
    })
    @DisplayName("no script-end-tag variant survives into the embedded payload")
    void escapesEveryScriptEndTagVariant(String payload) {
        String json = "{\"source\":\"" + payload.replace("\t", "\\t") + "\"}";

        String escaped = escaper.escapeForScriptContext(json);

        assertThat(escaped)
                .describedAs("payload %s must not leave a tokenizer-visible tag", payload)
                .doesNotContainPattern("(?i)</script[\\s/>]")
                .doesNotContainPattern("(?i)<script");
        assertThat(sourceAfterRoundTrip(json))
                .describedAs("escaping must be lossless for %s", payload)
                .isEqualTo(payload);
    }

    @Test
    @DisplayName("escapes the ampersand so entity tricks cannot reconstruct a tag")
    void escapesAmpersand() {
        String json = "{\"source\":\"a &lt;b&gt; c\"}";

        String escaped = escaper.escapeForScriptContext(json);

        assertThat(escaped).doesNotContain("&");
        assertThat(sourceAfterRoundTrip(json)).isEqualTo("a &lt;b&gt; c");
    }

    @Test
    @DisplayName("an escaped opening script tag stays valid JSON and reads back unchanged")
    void escapedOpeningScriptTagSurvivesAJsonRoundTrip() {
        String json = "{\"source\":\"<script>alert(1)</script>\"}";

        assertThat(sourceAfterRoundTrip(json)).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    @DisplayName("escapes a leading HTML comment opener so it cannot swallow the rest of the document")
    void escapesHtmlCommentOpener() {
        String json = "{\"source\":\"<!-- not a comment -->\"}";

        String escaped = escaper.escapeForScriptContext(json);

        assertThat(escaped).doesNotContain("<!--");
    }

    @Test
    @DisplayName("an escaped HTML comment opener stays valid JSON and reads back unchanged")
    void escapedHtmlCommentOpenerSurvivesAJsonRoundTrip() {
        String json = "{\"source\":\"<!-- not a comment -->\"}";

        assertThat(sourceAfterRoundTrip(json)).isEqualTo("<!-- not a comment -->");
    }

    @Test
    @DisplayName("an escaped closing script tag stays valid JSON and reads back unchanged")
    void escapedClosingScriptTagSurvivesAJsonRoundTrip() {
        String json = "{\"source\":\"a string with </script> inside\"}";

        assertThat(sourceAfterRoundTrip(json)).isEqualTo("a string with </script> inside");
    }

    @Test
    @DisplayName("preserves backslashes in embedded source text")
    void preservesBackslashes() {
        String json = "{\"source\":\"path\\\\to\\\\file\"}";

        String escaped = escaper.escapeForScriptContext(json);

        assertThat(escaped).contains("path\\\\to\\\\file");
    }

    @Test
    @DisplayName("escapes U+2028 and U+2029, which terminate a JS statement inside a string literal")
    void escapesLineAndParagraphSeparators() {
        String json = "{\"source\":\"line one" + LINE_SEPARATOR_CHAR + "line two" + PARAGRAPH_SEPARATOR_CHAR + "line three\"}";

        String escaped = escaper.escapeForScriptContext(json);

        assertThat(escaped).doesNotContain(String.valueOf(LINE_SEPARATOR_CHAR));
        assertThat(escaped).doesNotContain(String.valueOf(PARAGRAPH_SEPARATOR_CHAR));
        assertThat(escaped).contains("\\u2028");
        assertThat(escaped).contains("\\u2029");
    }
}
