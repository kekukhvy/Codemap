package dev.codemap.core.diff;

import dev.codemap.core.model.ChangeStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The result of one {@link MethodStatusAssigner#assign} pass: a status per
 * still-existing method, plus the line ranges the diff deleted outright.
 *
 * <p>Deliberately excludes {@link ChangeStatus#AFFECTED} — that status needs
 * the call graph, which this stage does not have, and is added in a later pass
 * over this result.
 *
 * @param statusesByMethodId status for every method that was assigned one
 * @param removedRanges old-file ranges the diff deleted, with no surviving method to attach to
 */
public record MethodStatusAssignment(Map<String, ChangeStatus> statusesByMethodId, List<RemovedMethodRange> removedRanges) {

    public MethodStatusAssignment {
        statusesByMethodId = Map.copyOf(Objects.requireNonNull(statusesByMethodId, "statusesByMethodId"));
        removedRanges = List.copyOf(Objects.requireNonNull(removedRanges, "removedRanges"));
    }

    /** The status assigned to one method, defaulting to {@link ChangeStatus#UNCHANGED}. */
    public ChangeStatus statusOf(String methodId) {
        return statusesByMethodId.getOrDefault(methodId, ChangeStatus.UNCHANGED);
    }
}
