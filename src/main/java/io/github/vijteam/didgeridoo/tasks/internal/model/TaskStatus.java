package io.github.vijteam.didgeridoo.tasks.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A status within a space; ordering and uniqueness across statuses belong to the service. */
@Entity
@Table(name = "task_statuses")
public class TaskStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private StatusKind kind;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 100)
    private String name;

    protected TaskStatus() {}

    public TaskStatus(UUID spaceId, String name, StatusKind kind, int position) {
        this.spaceId = TaskFieldValidator.required(spaceId, "spaceId");
        this.kind = TaskFieldValidator.required(kind, "kind");
        if (position < 0) {
            throw new IllegalArgumentException("Position must not be negative");
        }
        this.position = position;
        this.name = TaskFieldValidator.text(name, 100, "status name");
    }

    public void rename(String name) {
        this.name = TaskFieldValidator.text(name, 100, "status name");
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getSpaceId() {
        return spaceId;
    }

    public StatusKind getKind() {
        return kind;
    }

    public int getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }
}
