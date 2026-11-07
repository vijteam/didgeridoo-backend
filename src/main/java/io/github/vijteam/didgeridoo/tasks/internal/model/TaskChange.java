package io.github.vijteam.didgeridoo.tasks.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_changes")
public class TaskChange {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorId;

    @Column(nullable = false, updatable = false, length = 16)
    private String kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "changed_fields",
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb")
    private Map<String, Map<String, Object>> changedFields;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskChange() {}

    public static TaskChange created(Task task, UUID actorId) {
        TaskChange change = new TaskChange();
        change.taskId = task.getId();
        change.actorId = actorId;
        change.kind = "CREATED";
        change.changedFields = new LinkedHashMap<>();
        snapshot(task).forEach(change::add);
        return change;
    }

    public static Map<String, Object> snapshot(Task task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", task.getTitle());
        result.put("description", task.getDescription());
        result.put("deadline", task.getDeadline() == null ? null : task.getDeadline().toString());
        result.put("priority", task.getPriority().name());
        result.put("statusId", task.getStatusId().toString());
        result.put(
                "assigneeId",
                task.getAssigneeId() == null ? null : task.getAssigneeId().toString());
        result.put("assigneeCanEdit", task.isAssigneeCanEdit());
        result.put(
                "assignmentSource",
                task.getAssignmentSource() == null ? null : task.getAssignmentSource().name());
        return result;
    }

    public static TaskChange updated(Task task, UUID actorId, Map<String, Object> before) {
        TaskChange change = new TaskChange();
        change.taskId = task.getId();
        change.actorId = actorId;
        change.kind = "UPDATED";
        change.changedFields = new LinkedHashMap<>();
        snapshot(task)
                .forEach(
                        (field, value) -> {
                            if (!Objects.equals(before.get(field), value)) {
                                Map<String, Object> values = new LinkedHashMap<>();
                                values.put("old", before.get(field));
                                values.put("new", value);
                                change.changedFields.put(field, values);
                            }
                        });
        return change;
    }

    public boolean hasChanges() {
        return !changedFields.isEmpty();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getKind() {
        return kind;
    }

    public Map<String, Map<String, Object>> getChangedFields() {
        return changedFields;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private void add(String field, Object value) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("old", null);
        values.put("new", value);
        changedFields.put(field, values);
    }
}
