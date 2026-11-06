package io.github.vijteam.didgeridoo.tasks.internal.repository;

import io.github.vijteam.didgeridoo.tasks.internal.model.StatusKind;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskStatusRepository extends JpaRepository<TaskStatus, UUID> {
    Optional<TaskStatus> findFirstBySpaceIdAndKindOrderByPositionAsc(UUID spaceId, StatusKind kind);

    List<TaskStatus> findAllBySpaceIdOrderByPositionAsc(UUID spaceId);
}
