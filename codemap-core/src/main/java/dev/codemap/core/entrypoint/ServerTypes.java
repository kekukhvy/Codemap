package dev.codemap.core.entrypoint;

import java.util.Set;

/**
 * Type names Codemap recognises as a route-registration receiver (spec §4.1).
 *
 * <p>Matching is by the simple type name as written at the declaration site
 * rather than by a symbol-solver lookup into the library's own classes. A
 * project's dependency jars may not always resolve (spec's "degrade, don't
 * fail"), and the declared type name alone is already unambiguous — nobody
 * names an unrelated variable {@code Javalin}.
 */
final class ServerTypes {

    private static final Set<String> KNOWN_SERVER_TYPES = Set.of("Javalin", "Spark");

    private ServerTypes() {
    }

    static boolean isKnownServerType(String simpleTypeName) {
        return KNOWN_SERVER_TYPES.contains(simpleTypeName);
    }
}
