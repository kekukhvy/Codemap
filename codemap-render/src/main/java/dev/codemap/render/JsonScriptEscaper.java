package dev.codemap.render;

/**
 * Makes a JSON payload safe to embed literally inside an HTML {@code <script>}
 * element.
 *
 * <p>The index carries real Java source text from the analysed repository, which
 * is untrusted input: a method body or comment can contain anything at all. If
 * that text can close the surrounding script element, whoever opens the report
 * executes markup chosen by whoever wrote the analysed code.
 *
 * <p><strong>Characters are escaped, not sequences.</strong> Matching literals
 * like {@code </script>} is not enough, because the HTML tokenizer ends a script
 * element on {@code </script} followed by whitespace, {@code /}, or {@code >},
 * and the tag name is case-insensitive — so {@code </script foo>},
 * {@code </SCRIPT>} and {@code </script/>} all terminate the element while
 * matching no such literal. Escaping {@code <}, {@code >} and {@code &}
 * themselves means no tag can ever form, whatever shape it is written in.
 *
 * <p>Every replacement is itself <em>valid JSON</em>, because the payload is
 * still parsed as JSON once the browser has read it out of the script element.
 * {@code <}-style escapes round-trip losslessly, so the source text a
 * reader sees in the report is byte-for-byte what is in the repository. (The
 * tempting {@code <\/script>} trick works only for that one literal: {@code \/}
 * is legal JSON but {@code \s} and {@code \!} are not, and would make the whole
 * document fail to parse.)
 *
 * <p>The Unicode line and paragraph separators are escaped for a different
 * reason: ECMA-262 historically treated them as line terminators even inside a
 * string literal. None of these characters are illegal JSON, so
 * {@code ObjectMapper} will not escape them — this is a second,
 * HTML-context-specific pass applied after serialisation.
 */
public final class JsonScriptEscaper {

    private static final String LESS_THAN = "<";
    private static final String LESS_THAN_ESCAPED = "\\u003c";
    private static final String GREATER_THAN = ">";
    private static final String GREATER_THAN_ESCAPED = "\\u003e";
    private static final String AMPERSAND = "&";
    private static final String AMPERSAND_ESCAPED = "\\u0026";

    private static final int LINE_SEPARATOR_CODE_POINT = 0x2028;
    private static final int PARAGRAPH_SEPARATOR_CODE_POINT = 0x2029;
    private static final String LINE_SEPARATOR = Character.toString(LINE_SEPARATOR_CODE_POINT);
    private static final String PARAGRAPH_SEPARATOR = Character.toString(PARAGRAPH_SEPARATOR_CODE_POINT);
    private static final String LINE_SEPARATOR_ESCAPED = "\\u2028";
    private static final String PARAGRAPH_SEPARATOR_ESCAPED = "\\u2029";

    /**
     * Escapes a JSON string so it can be embedded verbatim between
     * {@code <script>} tags without prematurely closing the element, opening a
     * spurious HTML comment, or terminating a JS string literal early.
     *
     * @param json valid JSON text, as produced by an {@code ObjectMapper}
     * @return the same JSON, safe to embed inside a script element
     */
    public String escapeForScriptContext(String json) {
        return json
                .replace(LESS_THAN, LESS_THAN_ESCAPED)
                .replace(GREATER_THAN, GREATER_THAN_ESCAPED)
                .replace(AMPERSAND, AMPERSAND_ESCAPED)
                .replace(LINE_SEPARATOR, LINE_SEPARATOR_ESCAPED)
                .replace(PARAGRAPH_SEPARATOR, PARAGRAPH_SEPARATOR_ESCAPED);
    }
}
