package io.github.vijteam.didgeridoo.tasks.internal.repository;

import io.github.vijteam.didgeridoo.tasks.internal.model.TaskChange;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskChangeRepository extends JpaRepository<TaskChange, UUID> {}
