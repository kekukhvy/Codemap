package dev.codemap.render;

/**
 * Composes the final {@code report.html} text from its parts: markup, inlined
 * CSS, inlined D3, the escaped embedded view model, and the interactive script.
 *
 * <p>Kept separate from {@link ReportRenderer} so the string template lives in
 * one place and is easy to read as a whole page, rather than built up across
 * several methods that each know a fragment of the final HTML.
 *
 * <p>The page declares a Content-Security-Policy of {@code default-src 'none'}.
 * Inline script and style must stay allowed — the whole point of the report is
 * that it carries its own — but everything else is denied, which enforces the
 * self-containment invariant (spec §6.6) in the browser rather than only by
 * convention, and means that even if some future escaping bug let markup out of
 * {@link JsonScriptEscaper}, it could not reach the network to exfiltrate the
 * source text the report embeds.
 */
final class ReportPageAssembler {

    private static final String DATA_VARIABLE = "window.__CODEMAP_DATA__";

    private static final String PAGE_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'">
            <title>Codemap</title>
            <style>
            %s
            </style>
            </head>
            <body>
            <div id="app">
              <header>
                <h1>Codemap</h1>
                <div class="controls">
                  <input type="search" id="search-input" placeholder="Search classes and methods...">
                  <select id="layer-filter"><option value="">All layers</option></select>
                  <select id="module-filter"><option value="">All modules</option></select>
                </div>
              </header>
              <div id="entry-point-panel">
                <h2>Entry points</h2>
                <ul id="entry-point-list"></ul>
              </div>
              <div id="canvas-wrapper">
                <svg id="graph">
                  <defs>
                    <marker id="arrowhead" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
                      <path d="M 0 0 L 10 5 L 0 10 z"></path>
                    </marker>
                  </defs>
                </svg>
              </div>
              <div id="side-panel"><div class="placeholder">Select a node to see details.</div></div>
            </div>
            <script>
            %s
            </script>
            <script>
            %s = %s;
            </script>
            <script>
            %s
            </script>
            </body>
            </html>
            """;

    /**
     * Assembles the full page.
     *
     * @param css inlined report stylesheet
     * @param d3Bundle inlined vendored D3 script
     * @param escapedJson the view model, serialised and escaped for a script context
     * @param reportScript the interactive report script
     * @return the complete, self-contained HTML document
     */
    String assemble(String css, String d3Bundle, String escapedJson, String reportScript) {
        return PAGE_TEMPLATE.formatted(css, d3Bundle, DATA_VARIABLE, escapedJson, reportScript);
    }
}
