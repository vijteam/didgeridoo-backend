package io.github.vijteam.didgeridoo.tasks.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.util.UUID;

/** Link used in task queries. Changes are written in batches by the tag repository. */
@Entity
@Immutable
@Table(name = "task_tags")
@IdClass(TaskTag.Key.class)
public class TaskTag {
    public record Key(UUID taskId, UUID tagId) implements Serializable {}

    @Id
    @Column(name = "task_id")
    private UUID taskId;

    @Id
    @Column(name = "tag_id")
    private UUID tagId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    protected TaskTag() {}
}
