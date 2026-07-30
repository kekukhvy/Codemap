package dev.codemap.render;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Reads report assets bundled on the classpath: vendored D3, CSS, and JS.
 *
 * <p>Every asset the report needs travels inside the jar rather than being
 * fetched at render time, which is what lets {@code report.html} work over
 * {@code file://} with no network access (spec §6.6).
 */
public final class ClasspathAssetLoader {

    private static final String D3_RESOURCE = "dev/codemap/render/d3.v7.min.js";

    /**
     * Loads the vendored D3 bundle, never a CDN reference.
     *
     * @return the full D3 source, ready to inline into the report
     */
    public String loadD3() {
        return load(D3_RESOURCE);
    }

    /**
     * Reads one classpath resource as UTF-8 text.
     *
     * @param resourcePath path relative to the classpath root
     * @return the resource's full text
     * @throws IllegalStateException if the resource is not present — a missing
     *         bundled asset is a packaging defect, not a degradable runtime
     *         condition, so this fails fast rather than producing a broken report
     */
    public String load(String resourcePath) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled report asset: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read bundled report asset: " + resourcePath, e);
        }
    }
}
