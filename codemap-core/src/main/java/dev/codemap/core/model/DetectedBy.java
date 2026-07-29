package dev.codemap.core.model;

/**
 * How an {@link EntryPoint} was found.
 *
 * <p>Recorded on every entry point (spec §4.1, §4.3) so a reader can always tell a
 * syntax-derived fact from a project-declared rule or a model's guess.
 */
public enum DetectedBy {

    /** Found by a built-in deterministic rule: an annotation or a recognised call shape. */
    RULE,

    /** Found by a project-declared rule in {@code codemap.yml}. */
    CONFIG,

    /** Found by the {@code --ai} classifier, never overriding a rule- or config-detected entry point. */
    AI
}
