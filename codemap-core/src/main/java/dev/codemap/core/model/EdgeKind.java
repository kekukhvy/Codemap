package dev.codemap.core.model;

/**
 * What kind of relationship a {@link CallEdge} records.
 *
 * <p>The internal/external distinction is deliberate (spec §3.3): a call within a
 * class is a local detail, while a call crossing a class boundary is an
 * architectural fact worth a visible arrow. {@link #CROSS_MODULE} additionally
 * crosses a module boundary and carries both module ids.
 */
public enum EdgeKind {

    /** A call to another method of the same class. */
    CALL_INTERNAL,

    /** A call crossing into another class, within the same module. */
    CALL_EXTERNAL,

    /** A call or reference crossing a module boundary. */
    CROSS_MODULE,

    /** A type used in a signature; does not continue a call chain. */
    USES_TYPE,

    /** An interface implemented by a class. */
    IMPLEMENTS
}
