package dev.codemap.core.entrypoint;

import com.github.javaparser.ast.CompilationUnit;
import dev.codemap.core.model.EntryPoint;

import java.util.List;

/**
 * One deterministic entry-point detection rule (spec §4.1).
 *
 * <p>Each rule owns exactly one recognition shape — an annotation combination, a
 * bootstrap method, a programmatic route registration — so a new rule can be added
 * without touching the others, and {@link EntryPointDetector} stays a simple
 * aggregator.
 */
interface EntryPointRule {

    /**
     * Finds every entry point this rule recognises in a compilation unit.
     *
     * @param unit a compilation unit, parsed with a symbol solver attached
     * @param moduleId the module the file belongs to
     * @param relativePath the file's path relative to the project root
     * @return matched entry points, or an empty list when the rule finds none
     */
    List<EntryPoint> detect(CompilationUnit unit, String moduleId, String relativePath);
}
