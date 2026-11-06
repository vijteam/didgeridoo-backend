package io.github.vijteam.didgeridoo.tasks.internal.model;

import java.util.UUID;

/** A status within a space; ordering and uniqueness across statuses belong to the service. */
public class TaskStatus {
    private final UUID spaceId;
    private final StatusKind kind;
    private final int position;
    private String name;

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
