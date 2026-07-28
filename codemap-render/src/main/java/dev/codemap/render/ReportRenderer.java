package dev.codemap.render;

import dev.codemap.core.CodemapOptions;

/**
 * Renders an index into a self-contained {@code report.html}.
 *
 * <p>Not implemented yet — the renderer arrives with the interactive report
 * slice. The type exists now to fix the module's shape and its dependency
 * direction: rendering reads the core model, never the other way round.
 *
 * <p>The output it will produce must work over {@code file://} with no server and
 * no network, which means CSS, JavaScript, D3, the index, and every method's
 * source text are all inlined into the single file.
 */
public class ReportRenderer {

    /**
     * Writes the report for a completed run.
     *
     * @param options validated inputs, carrying the report destination
     * @throws UnsupportedOperationException always, until the renderer is built
     */
    public void render(CodemapOptions options) {
        throw new UnsupportedOperationException(
                "Report rendering is not implemented yet (target: %s)".formatted(options.output()));
    }
}
