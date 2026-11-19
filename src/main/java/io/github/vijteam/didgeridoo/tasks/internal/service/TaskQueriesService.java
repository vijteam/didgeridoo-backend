package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.api.StatusTaskCount;
import io.github.vijteam.didgeridoo.tasks.api.TaskQueries;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskStatusRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskQueriesService implements TaskQueries {
    private final TaskStatusRepository statuses;

    public TaskQueriesService(TaskStatusRepository statuses) {
        this.statuses = statuses;
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
