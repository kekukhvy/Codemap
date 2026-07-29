package dev.codemap.ai;

/**
 * Classifies entry-point candidates the deterministic rules could not resolve.
 *
 * <p>Not implemented yet — this arrives with the config-and-AI slice. The type
 * exists now to hold the module's contract, which is unusual enough to be worth
 * stating early:
 *
 * <ul>
 *   <li>It never searches from scratch. Rules run first, and only the leftover
 *       orphan roots are offered for classification.</li>
 *   <li>It shells out to the local {@code claude} CLI rather than embedding an AI
 *       SDK, so there are no credentials to manage and no network code here.</li>
 *   <li>Every failure — missing binary, non-zero exit, malformed output, timeout —
 *       degrades to rule-only results with a warning. None of them fail the run.</li>
 * </ul>
 */
public class EntryPointClassifier {

    /**
     * Reports whether classification can run in this environment.
     *
     * <p>Callers use this to skip the attempt entirely rather than to decide
     * whether to fail: an absent classifier is a normal state, not an error.
     *
     * @return {@code true} once the classifier is implemented and its binary is
     *         available
     */
    public boolean isAvailable() {
        return false;
    }
}
