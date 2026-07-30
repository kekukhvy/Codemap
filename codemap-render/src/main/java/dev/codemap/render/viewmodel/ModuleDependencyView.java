package dev.codemap.render.viewmodel;

import java.util.Objects;

/**
 * One module depending on another, aggregated from {@code CROSS_MODULE} edges —
 * the "which module depends on which" overview (spec §3.2.2, §3.5).
 *
 * @param fromModuleId the depending module
 * @param toModuleId the module depended on
 */
public record ModuleDependencyView(String fromModuleId, String toModuleId) {

    public ModuleDependencyView {
        Objects.requireNonNull(fromModuleId, "fromModuleId");
        Objects.requireNonNull(toModuleId, "toModuleId");
    }
}
