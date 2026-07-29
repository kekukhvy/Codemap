package dev.codemap.core;

/**
 * How the working tree is compared against history.
 *
 * <p>The distinction matters because "what changed" has two useful readings, and
 * picking the wrong one quietly misattributes other people's work to yours.
 */
public enum ComparisonMode {

    /**
     * Everything this branch changed since it diverged from the base — the
     * equivalent of {@code git diff <base>...HEAD}, plus uncommitted work.
     *
     * <p>This is the default because it answers "what is in my pull request".
     * Commits that landed on the base branch after this one forked are excluded,
     * so the map does not colour someone else's changes as yours.
     */
    BRANCH,

    /**
     * A direct comparison against one revision — {@code git diff <rev>}.
     *
     * <p>Used when the caller names an explicit {@code --since} commit and wants
     * the range from exactly that point, with no merge-base reasoning.
     */
    REVISION
}
