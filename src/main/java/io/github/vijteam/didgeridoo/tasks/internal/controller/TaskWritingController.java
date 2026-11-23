package io.github.vijteam.didgeridoo.tasks.internal.controller;

import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskDependencyException;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskCreationService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskDeletionService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskEditingService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskStatusChangeService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskView;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/tasks")
@ConditionalOnProperty(name = "didgeridoo.tasks.http.writes-enabled", havingValue = "true")
public class TaskWritingController {
    private final TaskCreationService creation;
    private final TaskEditingService editing;
    private final TaskStatusChangeService statuses;
    private final TaskDeletionService deletion;
    private final TaskRequestParser parser;
    private final ObjectProvider<TaskWriteSecurity> security;

    public TaskWritingController(
            TaskCreationService creation,
            TaskEditingService editing,
            TaskStatusChangeService statuses,
            TaskDeletionService deletion,
            TaskRequestParser parser,
            ObjectProvider<TaskWriteSecurity> security) {
        this.creation = creation;
        this.editing = editing;
        this.statuses = statuses;
        this.deletion = deletion;
        this.parser = parser;
        this.security = security;
    }

    @PostMapping
    public ResponseEntity<TaskView> create(
            @PathVariable UUID spaceId,
            @RequestBody String body,
            @RequestParam MultiValueMap<String, String> query,
            HttpServletRequest request) {
        UUID actor = actor(request);
        validateQuery(query, Set.of());
        TaskView task = creation.createView(spaceId, actor, parser.create(body));
        URI location = URI.create("/api/v1/spaces/" + spaceId + "/tasks/" + task.id());
        return ResponseEntity.created(location).body(task);
    }

    @PatchMapping("/{taskId}")
    public TaskView patch(
            @PathVariable UUID spaceId,
            @PathVariable UUID taskId,
            @RequestBody String body,
            @RequestParam MultiValueMap<String, String> query,
            HttpServletRequest request) {
        UUID actor = actor(request);
        validateQuery(query, Set.of());
        return editing.patch(spaceId, actor, taskId, parser.patch(body));
    }

    @PutMapping("/{taskId}/status")
    public TaskView status(
            @PathVariable UUID spaceId,
            @PathVariable UUID taskId,
            @RequestBody String body,
            @RequestParam MultiValueMap<String, String> query,
            HttpServletRequest request) {
        UUID actor = actor(request);
        validateQuery(query, Set.of());
        return statuses.change(spaceId, actor, taskId, parser.status(body));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID spaceId,
            @PathVariable UUID taskId,
            @RequestParam MultiValueMap<String, String> query,
            HttpServletRequest request) {
        UUID actor = actor(request);
        validateQuery(query, Set.of());
        deletion.delete(spaceId, actor, taskId);
        return ResponseEntity.noContent().build();
    }

    private UUID actor(HttpServletRequest request) {
        if (request.getUserPrincipal() == null) {
            throw new TaskAuthenticationException();
        }
        TaskWriteSecurity guard = security.getIfAvailable();
        if (guard == null) {
            throw new TaskDependencyException(
                    TaskDependencyException.Reason.AUTHENTICATION_UNAVAILABLE,
                    "Write security adapter is not configured");
        }
        UUID actor = guard.authorize(request);
        if (actor == null) {
            throw new TaskAuthenticationException();
        }
        if (!"DELETE".equals(request.getMethod())) {
            try {
                if (request.getContentType() == null
                        || !MediaType.APPLICATION_JSON.includes(
                                MediaType.parseMediaType(request.getContentType()))) {
                    throw new TaskMediaTypeException();
                }
            } catch (org.springframework.http.InvalidMediaTypeException failure) {
                throw new TaskMediaTypeException();
            }
        }
        return actor;
    }

    private void validateQuery(MultiValueMap<String, String> query, Set<String> allowed) {
        query.forEach(
                (field, values) -> {
                    if (!allowed.contains(field) || values.size() != 1 || values.get(0).isBlank()) {
                        throw new FieldValidationException(
                                field,
                                FieldErrorCode.INVALID_FORMAT,
                                "Unknown, empty or repeated query parameter");
                    }
                });
    }
}
