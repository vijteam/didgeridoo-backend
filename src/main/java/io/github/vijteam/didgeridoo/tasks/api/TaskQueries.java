package io.github.vijteam.didgeridoo.tasks.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Trusted inter-module data API for analytics, not an HTTP authorization boundary. The caller must
 * validate space visibility and access to the requested assignee through spaces. Results are
 * current snapshots, not historical metrics; missing spaces/statuses return no rows.
 */
public interface TaskQueries {
    /** Counts by creation time and counts open overdue tasks by deadline in [from, to). */
    TaskMetrics metrics(UUID spaceId, UUID assigneeId, Instant from, Instant to, Instant asOf);

    /** Includes assigned and unassigned tasks and zero-count statuses, ordered by position. */
    List<StatusTaskCount> countByStatus(UUID spaceId);

    /** Counts current assignments to the supplied user; zero-count statuses are retained. */
    List<StatusTaskCount> countByStatusForAssignee(UUID spaceId, UUID assigneeId);
}
