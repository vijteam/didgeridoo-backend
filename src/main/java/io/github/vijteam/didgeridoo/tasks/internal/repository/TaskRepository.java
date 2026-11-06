package io.github.vijteam.didgeridoo.tasks.internal.repository;

import io.github.vijteam.didgeridoo.tasks.internal.model.Task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    Page<Task> findAllBySpaceId(UUID spaceId, Pageable pageable);

    Optional<Task> findByIdAndSpaceId(UUID id, UUID spaceId);

    List<Task> findAllBySpaceIdOrderByCreatedAtAscIdAsc(UUID spaceId);
}
