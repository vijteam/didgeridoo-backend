package io.github.vijteam.didgeridoo.tasks.internal.repository;

import io.github.vijteam.didgeridoo.tasks.api.TaskMetrics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class TaskMetricsRepository {
    private final JdbcTemplate jdbc;

    public TaskMetricsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TaskMetrics count(
            UUID spaceId, UUID assigneeId, Instant from, Instant to, Instant asOf) {
        return jdbc.queryForObject(
                """
                WITH bounds AS (SELECT CAST(? AS timestamptz) AS from_at,
                                       CAST(? AS timestamptz) AS to_at)
                SELECT count(*) FILTER (WHERE b.from_at IS NULL
                           OR (t.created_at >= b.from_at AND t.created_at < b.to_at)) AS total,
                       count(*) FILTER (WHERE s.kind <> 'FINAL' AND t.deadline_at < ?
                           AND (b.from_at IS NULL OR (t.deadline_at >= b.from_at
                                                     AND t.deadline_at < b.to_at))) AS overdue
                FROM tasks t
                JOIN task_statuses s ON s.space_id = t.space_id AND s.id = t.status_id
                CROSS JOIN bounds b
                WHERE t.space_id = ? AND (CAST(? AS uuid) IS NULL OR t.assignee_user_id = ?)
                """,
                (row, index) -> new TaskMetrics(row.getLong("total"), row.getLong("overdue")),
                timestamp(from),
                timestamp(to),
                Timestamp.from(asOf),
                spaceId,
                assigneeId,
                assigneeId);
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
