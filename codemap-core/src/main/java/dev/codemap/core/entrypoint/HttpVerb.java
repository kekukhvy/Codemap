package dev.codemap.core.entrypoint;

import java.util.Locale;

/**
 * An HTTP method recognised by the REST detection rules (spec §4.1).
 *
 * <p>Both the Spring mapping-annotation family and the Javalin/Spark
 * route-registration methods key on one of these, so the two rules share the
 * same closed set rather than each hand-rolling its own strings.
 */
enum HttpVerb {

    GET,
    POST,
    PUT,
    DELETE,
    PATCH;

    private static final String LABEL_SEPARATOR = " ";

    /** The entry-point label for a route on this verb, e.g. {@code "GET /api/v1/tasks"}. */
    String labelFor(String path) {
        return name() + LABEL_SEPARATOR + path;
    }

    /**
     * Matches a Javalin/Spark route-registration method name, e.g. {@code app.get(...)}.
     *
     * @param methodName the called method's name, as written at the call site
     * @return the matching verb, or {@code null} when the name is not a recognised verb
     */
    static HttpVerb fromRegistrationMethodName(String methodName) {
        for (HttpVerb verb : values()) {
            if (verb.name().toLowerCase(Locale.ROOT).equals(methodName)) {
                return verb;
            }
        }
        return null;
    }
}
