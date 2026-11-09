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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Local task invariants; services check membership and permissions before mutation. */
@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    @Column(name = "creator_user_id", updatable = false)
    private UUID creatorId;

    @Column(name = "status_id", nullable = false)
    private UUID statusId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "deadline_at")
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "assignee_can_edit", nullable = false)
    private boolean assigneeCanEdit;

    @Column(name = "assignee_user_id")
    private UUID assigneeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_source", length = 16)
    private AssignmentSource assignmentSource;

    protected Task() {}

    public Task(UUID spaceId, UUID creatorId, UUID statusId, String title) {
        this.spaceId = TaskFieldValidator.required(spaceId, "spaceId");
        this.creatorId = TaskFieldValidator.required(creatorId, "creatorId");
        this.statusId = TaskFieldValidator.required(statusId, "statusId");
        this.title = TaskFieldValidator.text(title, 255, "title");
    }

    public void updateDetails(
            String title,
            String description,
            Instant deadline,
            TaskPriority priority,
            boolean assigneeCanEdit) {
        String validTitle = TaskFieldValidator.text(title, 255, "title");
        TaskPriority validPriority = TaskFieldValidator.required(priority, "priority");
        this.title = validTitle;
        this.description = description;
        this.deadline = deadline;
        this.priority = validPriority;
        this.assigneeCanEdit = assigneeCanEdit;
    }

    public void changeStatus(UUID statusId) {
        this.statusId = TaskFieldValidator.required(statusId, "statusId");
    }

    public void assign(UUID assigneeId, AssignmentSource source) {
        UUID validAssignee = TaskFieldValidator.required(assigneeId, "assigneeId");
        AssignmentSource validSource = TaskFieldValidator.required(source, "assignmentSource");
        this.assigneeId = validAssignee;
        this.assignmentSource = validSource;
    }

    public void unassign() {
        this.assigneeId = null;
        this.assignmentSource = null;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getSpaceId() {
        return spaceId;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public UUID getStatusId() {
        return statusId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public boolean isAssigneeCanEdit() {
        return assigneeCanEdit;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public AssignmentSource getAssignmentSource() {
        return assignmentSource;
    }
}
