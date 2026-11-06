package io.github.vijteam.didgeridoo.tasks.internal.model;

import java.time.Instant;
import java.util.UUID;

/** Local task invariants; services check membership and permissions before mutation. */
public class Task {
    private final UUID spaceId;
    private final UUID creatorId;
    private UUID statusId;
    private String title;
    private String description;
    private Instant deadline;
    private TaskPriority priority = TaskPriority.MEDIUM;
    private boolean assigneeCanEdit;
    private UUID assigneeId;
    private AssignmentSource assignmentSource;

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
