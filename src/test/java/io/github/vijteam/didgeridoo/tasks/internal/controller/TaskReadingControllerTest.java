package io.github.vijteam.didgeridoo.tasks.internal.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskNotFoundException;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskPriority;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskHistoryService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskReadingService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskView;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

class TaskReadingControllerTest {
    private final TaskReadingService reading = mock(TaskReadingService.class);
    private final TaskHistoryService history = mock(TaskHistoryService.class);
    private final TaskActorResolver resolver = mock(TaskActorResolver.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<TaskActorResolver> provider = mock(ObjectProvider.class);

    private final Principal principal = () -> "username-is-not-a-uuid";
    private final UUID space = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final UUID task = UUID.randomUUID();
    private MockMvc mvc;

    private String base() {
        return "/api/v1/spaces/" + space + "/tasks";
    }

    @BeforeEach
    void setup() {
        when(provider.getIfAvailable()).thenReturn(resolver);
        when(resolver.resolve(principal)).thenReturn(actor);
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new TaskReadingController(reading, history, provider))
                        .setControllerAdvice(new TaskReadingExceptionHandler())
                        .build();
    }

    @Test
    void readsTaskUsingTrustedActorResolver() throws Exception {
        var now = Instant.parse("2026-11-06T12:00:00Z");
        when(reading.get(space, actor, task))
                .thenReturn(
                        new TaskView(
                                task,
                                space,
                                actor,
                                UUID.randomUUID(),
                                "Task",
                                null,
                                null,
                                TaskPriority.MEDIUM,
                                false,
                                null,
                                null,
                                now,
                                now));
        mvc.perform(get(base() + "/" + task).principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.toString()))
                .andExpect(jsonPath("$.title").value("Task"));
        verify(reading).get(space, actor, task);
    }

    @Test
    void returnsExplicitPageShapeAndDefaults() throws Exception {
        when(reading.list(space, actor, 0, 20)).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get(base()).principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    @Test
    void readsHistoryWithRequestedPage() throws Exception {
        when(history.list(space, actor, task, 1, 5)).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get(base() + "/" + task + "/changes?page=1&size=5").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5));
        verify(history).list(space, actor, task, 1, 5);
    }

    @Test
    void anonymousRequestIsUnauthenticated() throws Exception {
        mvc.perform(get(base()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void missingAdapterFailsClosed() throws Exception {
        when(provider.getIfAvailable()).thenReturn(null);
        mvc.perform(get(base()).principal(principal))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void hidesOutsiderAndMissingTaskBehindSameError() throws Exception {
        when(reading.get(space, actor, task))
                .thenThrow(
                        new TaskAccessException(
                                TaskAccessException.Reason.NOT_MEMBER,
                                "internal membership details"));
        mvc.perform(get(base() + "/" + task).principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        doThrow(new TaskNotFoundException("internal task details"))
                .when(reading)
                .get(space, actor, task);
        mvc.perform(get(base() + "/" + task).principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }

    @Test
    void rejectsUnknownQueryFieldsAndMalformedIdentifiers() throws Exception {
        mvc.perform(get(base() + "?actorId=" + actor).principal(principal))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base() + "?page=no").principal(principal))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/spaces/bad/tasks").principal(principal))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base() + "?size=0").principal(principal))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unresolvedPrincipalIsUnauthenticated() throws Exception {
        when(resolver.resolve(principal)).thenReturn(null);
        mvc.perform(get(base()).principal(principal)).andExpect(status().isUnauthorized());
    }

    @Test
    void forbiddenActionAndUnexpectedFailureHaveSafeResponses() throws Exception {
        when(reading.list(space, actor, 0, 20))
                .thenThrow(new TaskAccessException("Private reason"));
        mvc.perform(get(base()).principal(principal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Action is not allowed"));
        doThrow(new IllegalStateException("Internal storage details"))
                .when(reading)
                .list(space, actor, 0, 20);
        mvc.perform(get(base()).principal(principal))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to process request"));
    }

    @Test
    void rejectsRepeatedOrEmptyPagination() throws Exception {
        mvc.perform(get(base() + "?page=0&page=1").principal(principal))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base() + "?size=").principal(principal)).andExpect(status().isBadRequest());
    }
}
