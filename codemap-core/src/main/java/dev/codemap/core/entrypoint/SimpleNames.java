package dev.codemap.core.entrypoint;

/**
 * Reduces a qualified name to the part a reader recognises.
 *
 * <p>Detection rules see names in whichever form the author wrote them: an
 * annotation may appear as {@code @Route} or {@code @com.vaadin.flow.router.Route},
 * and a parameter type as {@code Javalin} or {@code io.javalin.Javalin}. Matching
 * has to work either way, so every rule reduces to the simple name first.
 *
 * <p>Shared rather than repeated because the rule for what counts as a qualifier
 * belongs in one place — nested types (written with {@code $} in binary form) would
 * otherwise need fixing in three.
 */
final class SimpleNames {

    private static final char QUALIFIER_SEPARATOR = '.';

    private SimpleNames() {
    }

    /**
     * Strips any package or enclosing-type qualifier.
     *
     * @param qualifiedName a name, qualified or not
     * @return the final segment, or the input unchanged when it carries no qualifier
     */
    static String of(String qualifiedName) {
        int lastSeparator = qualifiedName.lastIndexOf(QUALIFIER_SEPARATOR);
        return lastSeparator < 0 ? qualifiedName : qualifiedName.substring(lastSeparator + 1);
    }
}
