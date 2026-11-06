package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;
import io.github.vijteam.didgeridoo.tasks.internal.model.StatusKind;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskStatus;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskStatusRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class StatusInitializationService implements StatusInitializer {
    private final TaskStatusRepository statuses;

    public StatusInitializationService(TaskStatusRepository statuses) {
        this.statuses = statuses;
    }

    @Override
    public void initialize(UUID spaceId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId is required");
        }
        if (!statuses.findAllBySpaceIdOrderByPositionAsc(spaceId).isEmpty()) {
            throw new IllegalStateException("Statuses already exist for this space");
        }
        statuses.saveAllAndFlush(
                List.of(
                        new TaskStatus(spaceId, "To do", StatusKind.INITIAL, 0),
                        new TaskStatus(spaceId, "Done", StatusKind.FINAL, 1)));
    }
}
