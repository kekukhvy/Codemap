package dev.codemap.render;

import dev.codemap.core.CodemapOptions;
import dev.codemap.core.model.CodeIndex;
import dev.codemap.render.viewmodel.ReportViewModel;
import dev.codemap.render.viewmodel.ReportViewModelBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders an index into a self-contained {@code report.html}.
 *
 * <p>The output works over {@code file://} with no server and no network: CSS,
 * JavaScript, D3, the index, and every method's source text are all inlined
 * into the single file (spec §6.6). This class only composes collaborators —
 * projection, serialisation, escaping, asset loading, and page assembly each
 * live in their own class so this one stays a straight-line pipeline.
 */
public class ReportRenderer {

    private final ReportViewModelBuilder viewModelBuilder;
    private final ReportJsonSerializer jsonSerializer;
    private final JsonScriptEscaper scriptEscaper;
    private final ClasspathAssetLoader assetLoader;
    private final ReportPageAssembler pageAssembler;

    public ReportRenderer() {
        this(new ReportViewModelBuilder(), new ReportJsonSerializer(), new JsonScriptEscaper(),
                new ClasspathAssetLoader(), new ReportPageAssembler());
    }

    ReportRenderer(
            ReportViewModelBuilder viewModelBuilder,
            ReportJsonSerializer jsonSerializer,
            JsonScriptEscaper scriptEscaper,
            ClasspathAssetLoader assetLoader,
            ReportPageAssembler pageAssembler) {
        this.viewModelBuilder = viewModelBuilder;
        this.jsonSerializer = jsonSerializer;
        this.scriptEscaper = scriptEscaper;
        this.assetLoader = assetLoader;
        this.pageAssembler = pageAssembler;
    }

    /**
     * Writes the report for a completed run.
     *
     * @param options validated inputs, carrying the report destination
     * @param index the complete static picture of the project, with change
     *        status attached when a diff was resolved
     * @throws UncheckedIOException if the report file cannot be written
     */
    public void render(CodemapOptions options, CodeIndex index) {
        String html = renderToString(index);
        writeReport(options.output(), html);
    }

    /** Renders the report to a string, without touching the filesystem. */
    String renderToString(CodeIndex index) {
        ReportViewModel viewModel = viewModelBuilder.build(index);
        String json = jsonSerializer.serialize(viewModel);
        String escapedJson = scriptEscaper.escapeForScriptContext(json);

        String css = assetLoader.load(ReportAssets.CSS_RESOURCE);
        String d3Bundle = assetLoader.loadD3();
        String reportScript = assetLoader.load(ReportAssets.SCRIPT_RESOURCE);

        return pageAssembler.assemble(css, d3Bundle, escapedJson, reportScript);
    }

    private void writeReport(Path output, String html) {
        try {
            Path directory = output.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Files.writeString(output, html);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the report to " + output, e);
        }
    }
}
