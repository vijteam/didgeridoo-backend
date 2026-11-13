package io.github.vijteam.didgeridoo.tasks.internal.controller;

import io.github.vijteam.didgeridoo.tasks.internal.dto.TaskPageResponse;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskDependencyException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskChangeView;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskHistoryService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskReadingService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskView;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/tasks")
public class TaskReadingController {
    private final TaskReadingService reading;
    private final TaskHistoryService history;
    private final ObjectProvider<TaskActorResolver> actors;

    public TaskReadingController(
            TaskReadingService reading,
            TaskHistoryService history,
            ObjectProvider<TaskActorResolver> actors) {
        this.reading = reading;
        this.history = history;
        this.actors = actors;
    }

    @GetMapping("/{taskId}")
    public TaskView get(
            @PathVariable UUID spaceId,
            @PathVariable UUID taskId,
            @RequestParam MultiValueMap<String, String> query,
            Principal principal) {
        validateQuery(query, Set.of());
        return reading.get(spaceId, actor(principal), taskId);
    }

    @GetMapping
    public TaskPageResponse<TaskView> list(
            @PathVariable UUID spaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> query,
            Principal principal) {
        validatePage(query, page, size);
        var result = reading.list(spaceId, actor(principal), page, size);
        return new TaskPageResponse<>(result.getContent(), page, size, result.getTotalElements());
    }

    @GetMapping("/{taskId}/changes")
    public TaskPageResponse<TaskChangeView> changes(
            @PathVariable UUID spaceId,
            @PathVariable UUID taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> query,
            Principal principal) {
        validatePage(query, page, size);
        var result = history.list(spaceId, actor(principal), taskId, page, size);
        return new TaskPageResponse<>(result.getContent(), page, size, result.getTotalElements());
    }

    private UUID actor(Principal principal) {
        if (principal == null) {
            throw new TaskAuthenticationException();
        }
        TaskActorResolver resolver = actors.getIfAvailable();
        if (resolver == null) {
            throw new TaskDependencyException(
                    TaskDependencyException.Reason.AUTHENTICATION_UNAVAILABLE,
                    "Authentication adapter is not configured");
        }
        UUID id = resolver.resolve(principal);
        if (id == null) {
            throw new TaskAuthenticationException();
        }
        return id;
    }

    private void validatePage(MultiValueMap<String, String> query, int page, int size) {
        validateQuery(query, Set.of("page", "size"));
        if (page < 0 || size < 1 || size > 100) {
            throw new TaskValidationException("Invalid pagination");
        }
    }

    private void validateQuery(MultiValueMap<String, String> query, Set<String> allowed) {
        query.forEach(
                (field, values) -> {
                    if (!allowed.contains(field) || values.size() != 1 || values.get(0).isBlank()) {
                        throw new TaskValidationException(
                                field, "Unknown, empty or repeated query parameter");
                    }
                });
    }
}
