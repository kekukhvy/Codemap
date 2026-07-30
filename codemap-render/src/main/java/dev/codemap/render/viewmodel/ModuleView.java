package dev.codemap.render.viewmodel;

import java.util.Objects;

/**
 * One build module, a top-level root of the map (spec §3.2.1).
 *
 * @param id stable identifier
 * @param name display name, e.g. {@code kairos-api}
 * @param path module directory, relative to the project root
 */
public record ModuleView(String id, String name, String path) {

    public ModuleView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
    }
}
