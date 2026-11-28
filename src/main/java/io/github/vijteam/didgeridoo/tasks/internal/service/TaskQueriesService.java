package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.api.StatusTaskCount;
import io.github.vijteam.didgeridoo.tasks.api.TaskMetrics;
import io.github.vijteam.didgeridoo.tasks.api.TaskQueries;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskMetricsRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskStatusRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskQueriesService implements TaskQueries {
    private final TaskStatusRepository statuses;

    private final TaskMetricsRepository metrics;

    public TaskQueriesService(TaskStatusRepository statuses, TaskMetricsRepository metrics) {
        this.statuses = statuses;
        this.metrics = metrics;
    }

    @Override
    public TaskMetrics metrics(
            UUID spaceId, UUID assigneeId, Instant from, Instant to, Instant asOf) {
        Objects.requireNonNull(spaceId, "spaceId");
        return metrics.count(spaceId, assigneeId, from, to, asOf);
    }

    @Override
    public List<StatusTaskCount> countByStatus(UUID spaceId) {
        Objects.requireNonNull(spaceId, "spaceId");
        return List.copyOf(statuses.countTasksByStatus(spaceId, false, null));
    }

    @Override
    public List<StatusTaskCount> countByStatusForAssignee(UUID spaceId, UUID assigneeId) {
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(assigneeId, "assigneeId");
        return List.copyOf(statuses.countTasksByStatus(spaceId, true, assigneeId));
    }
}
