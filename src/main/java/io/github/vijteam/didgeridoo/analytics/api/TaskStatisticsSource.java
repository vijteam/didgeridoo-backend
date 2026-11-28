package io.github.vijteam.didgeridoo.analytics.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Proposed consumer port for the Tasks aggregate adapter (S1 contract, not a working adapter). The
 * analytics caller must check SpaceAccess before invoking the source. Null assigneeId selects all
 * space tasks; a UUID selects tasks currently assigned to that user. All counts and status metadata
 * must come from one consistent database snapshot. Historical/period counts are intentionally
 * absent until Q-07 is decided.
 */
public interface TaskStatisticsSource {
    Snapshot current(UUID spaceId, UUID assigneeId, Instant asOf);

    record StatusCount(UUID statusId, String name, int position, boolean terminal, long count) {
        public StatusCount {
            if (invalidStatus(statusId, name, position, count)) {
                throw new IllegalArgumentException("Invalid status count");
            }
        }
    }

    record Snapshot(
            UUID spaceId,
            UUID assigneeId,
            Instant asOf,
            long total,
            long overdue,
            List<StatusCount> statuses) {
        public Snapshot {
            if (invalidSnapshot(spaceId, asOf, total, overdue, statuses)) {
                throw new IllegalArgumentException("Invalid snapshot");
            }
            statuses = List.copyOf(statuses);
            long sum = 0;
            int previousPosition = -1;
            java.util.Set<UUID> ids = new java.util.HashSet<>();
            for (StatusCount status : statuses) {
                if (status.position() <= previousPosition || !ids.add(status.statusId())) {
                    throw new IllegalArgumentException(
                            "Statuses must have unique IDs and increasing positions");
                }
                previousPosition = status.position();
                sum = Math.addExact(sum, status.count());
            }
            if (sum != total) {
                throw new IllegalArgumentException("Status counts must add up to total");
            }
        }
    }

    private static boolean invalidStatus(UUID id, String name, int position, long count) {
        boolean invalidMetadata = id == null || name == null || name.isBlank();
        return invalidMetadata || position < 0 || count < 0;
    }

    private static boolean invalidSnapshot(
            UUID spaceId, Instant asOf, long total, long overdue, List<StatusCount> statuses) {
        boolean missingContext = spaceId == null || asOf == null || statuses == null;
        boolean invalidCounts = total < 0 || overdue < 0 || overdue > total;
        return missingContext || invalidCounts;
    }
}
