package dev.codemap.core.diff;

import dev.codemap.core.model.ChangeStatus;

import java.util.List;

/**
 * Derives a class's status from its methods' statuses (spec §5): "class status
 * is derived; a class is changed if any of its methods changed."
 *
 * <p>The priority order below matters when a class has a mix — {@code changed}
 * always wins because it is the strongest fact a reviewer can be told about a
 * type, ahead of {@code added} or the merely {@code affected}.
 */
public final class ClassStatusAggregator {

    private static final List<ChangeStatus> PRIORITY_ORDER = List.of(
            ChangeStatus.CHANGED,
            ChangeStatus.ADDED,
            ChangeStatus.AFFECTED);

    /**
     * Aggregates one class's status from its methods.
     *
     * @param methodStatuses status of every method declared by the class
     * @return the highest-priority status present, or {@link ChangeStatus#UNCHANGED}
     *         when every method is unchanged or the class declares none
     */
    public ChangeStatus aggregate(List<ChangeStatus> methodStatuses) {
        for (ChangeStatus candidate : PRIORITY_ORDER) {
            if (methodStatuses.contains(candidate)) {
                return candidate;
            }
        }
        return ChangeStatus.UNCHANGED;
    }
}
