package dev.codemap.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Objects;

/**
 * One build module of the analysed project, and a top-level root of the map.
 *
 * <p>Modules are roots because that is how such systems are deployed — separate
 * processes, separate containers. Flattening them into one root would draw a
 * monolith that does not exist.
 *
 * @param id stable identifier, derived from the module path
 * @param name display name, e.g. {@code kairos-api}
 * @param path module directory, relative to the project root
 * @param sourceRoots production source roots, relative to the project root
 */
public record IndexedModule(String id, String name, String path, List<String> sourceRoots) {

    public IndexedModule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "sourceRoots"));
    }

    /**
     * Whether this module contributes any production source.
     *
     * <p>A declared but empty module is normal — a build file may list a module
     * that has not been written yet — so it is indexed as a root with nothing
     * under it rather than being dropped.
     */
    @JsonIgnore
    public boolean hasSources() {
        return !sourceRoots.isEmpty();
    }
}
