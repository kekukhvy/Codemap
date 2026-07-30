package dev.codemap.core.model;

/**
 * Java access level of a declared method, read from its modifiers.
 *
 * <p>Drives which rows a class box lists as public API versus which are
 * reveal-only in the UML class diagram (spec 007 §5.1): {@link #PUBLIC} and
 * {@link #PROTECTED} rows sit in the public compartment, {@link #PACKAGE} and
 * {@link #PRIVATE} rows appear only once a visible caller reveals them.
 */
public enum Visibility {

    PUBLIC,
    PROTECTED,
    PACKAGE,
    PRIVATE
}
