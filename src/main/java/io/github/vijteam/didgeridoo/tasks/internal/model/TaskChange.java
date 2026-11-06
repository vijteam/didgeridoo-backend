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
        change.add("title", task.getTitle());
        change.add("description", task.getDescription());
        change.add("deadline", task.getDeadline() == null ? null : task.getDeadline().toString());
        change.add("priority", task.getPriority().name());
        change.add("statusId", task.getStatusId().toString());
        change.add(
                "assigneeId",
                task.getAssigneeId() == null ? null : task.getAssigneeId().toString());
        change.add("assigneeCanEdit", task.isAssigneeCanEdit());
        change.add(
                "assignmentSource",
                task.getAssignmentSource() == null ? null : task.getAssignmentSource().name());
        return change;
    }

    private void add(String field, Object value) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("old", null);
        values.put("new", value);
        changedFields.put(field, values);
    }
}
