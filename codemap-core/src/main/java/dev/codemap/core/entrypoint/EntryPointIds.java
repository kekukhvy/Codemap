package dev.codemap.core.entrypoint;

import dev.codemap.core.model.EntryPointKind;

/**
 * Builds stable ids for detected entry points.
 *
 * <p>An id is derived from the module, kind, and target rather than a counter, so
 * two runs over unchanged source agree on the same id — that stability is what
 * lets {@code --ai} classifications and future incremental caching key off it.
 */
final class EntryPointIds {

    private static final String SEPARATOR = ":";

    private EntryPointIds() {
    }

    static String of(String moduleId, EntryPointKind kind, String discriminator) {
        return moduleId + SEPARATOR + kind + SEPARATOR + discriminator;
    }
}
