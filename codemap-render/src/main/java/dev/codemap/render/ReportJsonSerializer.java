package dev.codemap.render;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codemap.render.viewmodel.ReportViewModel;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Serialises a {@link ReportViewModel} to JSON for embedding in the report.
 *
 * <p>Serialisation failures here mean the view model itself is malformed —
 * something this module's own projection produced — so they are wrapped rather
 * than degraded: there is no partial report to fall back to once the payload
 * cannot be written at all.
 */
final class ReportJsonSerializer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Serialises a view model to a compact JSON string.
     *
     * @param viewModel the data to embed
     * @return JSON text, valid but not yet safe for a script context
     */
    String serialize(ReportViewModel viewModel) {
        try {
            return objectMapper.writeValueAsString(viewModel);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Could not serialise the report view model", new IOException(e));
        }
    }
}
