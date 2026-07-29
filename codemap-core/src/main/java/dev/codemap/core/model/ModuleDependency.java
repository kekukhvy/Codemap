package dev.codemap.core.model;

import java.util.Objects;

/**
 * One module depending on another, aggregated from {@link CallEdge}s of kind
 * {@link EdgeKind#CROSS_MODULE}.
 *
 * <p>This is the module-level overview the spec (§3.2.2) calls "which module
 * depends on which" — several call edges between the same pair of modules
 * collapse to a single dependency, since the overview answers a yes/no question
 * about the relationship, not how many calls realise it.
 *
 * @param fromModuleId the depending module
 * @param toModuleId the module depended on
 */
public record ModuleDependency(String fromModuleId, String toModuleId) {

    public ModuleDependency {
        Objects.requireNonNull(fromModuleId, "fromModuleId");
        Objects.requireNonNull(toModuleId, "toModuleId");
    }
}
