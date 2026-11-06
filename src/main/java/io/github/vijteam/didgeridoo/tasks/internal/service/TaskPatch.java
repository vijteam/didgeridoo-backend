package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.model.Task;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskPriority;

import java.time.Instant;

/** Internal command, not a JSON DTO: null field means omitted; Field.of(null) means clear. */
public record TaskPatch(
        Field<String> title,
        Field<String> description,
        Field<Instant> deadline,
        Field<TaskPriority> priority,
        Field<Boolean> assigneeCanEdit) {
    public TaskPatch {
        if (title == null
                && description == null
                && deadline == null
                && priority == null
                && assigneeCanEdit == null) {
            throw new IllegalArgumentException("At least one field is required");
        }
        requireNonNullValue(title, "title");
        requireNonNullValue(priority, "priority");
        requireNonNullValue(assigneeCanEdit, "assigneeCanEdit");
    }

    private static void requireNonNullValue(Field<?> field, String name) {
        if (field != null && field.value() == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }

    EditTask applyTo(Task task) {
        return new EditTask(
                title == null ? task.getTitle() : title.value(),
                description == null ? task.getDescription() : description.value(),
                deadline == null ? task.getDeadline() : deadline.value(),
                priority == null ? task.getPriority() : priority.value(),
                assigneeCanEdit == null ? null : assigneeCanEdit.value());
    }

    public record Field<T>(T value) {
        public static <T> Field<T> of(T value) {
            return new Field<>(value);
        }
    }
}
