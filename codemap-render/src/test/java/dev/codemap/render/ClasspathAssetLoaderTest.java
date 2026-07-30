package dev.codemap.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ClasspathAssetLoader} reads report assets (vendored D3, CSS, JS)
 * bundled on the classpath, so the renderer never reaches out to the network
 * or the filesystem outside the jar (spec §6.6: D3 vendored inline, never CDN).
 */
class ClasspathAssetLoaderTest {

    private final ClasspathAssetLoader loader = new ClasspathAssetLoader();

    @Test
    @DisplayName("loads the vendored D3 bundle from the classpath")
    void loadsVendoredD3() {
        String d3 = loader.loadD3();

        assertThat(d3).isNotBlank();
        assertThat(d3).contains("d3");
    }

    @Test
    @DisplayName("loads D3 as a non-empty, parseable script bundle rather than a CDN <script src> stub")
    void d3IsTheActualBundleNotAReference() {
        String d3 = loader.loadD3();

        assertThat(d3).doesNotContain("<script");
        assertThat(d3.length()).isGreaterThan(1000);
    }

    @Test
    @DisplayName("fails fast when a required asset is missing from the classpath")
    void missingAssetFailsFast() {
        assertThatThrownBy(() -> loader.load("dev/codemap/render/does-not-exist.js"))
                .isInstanceOf(IllegalStateException.class);
    }
}
