package io.github.vijteam.didgeridoo.tasks.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.vijteam.didgeridoo.tasks.internal.service.TaskCreationService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskDeletionService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskEditingService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskStatusChangeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

class TaskWritingSecurityTest {
    private final TaskCreationService creation = mock(TaskCreationService.class);
    private final TaskEditingService editing = mock(TaskEditingService.class);
    private final TaskStatusChangeService statuses = mock(TaskStatusChangeService.class);
    private final TaskDeletionService deletion = mock(TaskDeletionService.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<TaskWriteSecurity> provider = mock(ObjectProvider.class);

    private final Principal principal = () -> "trusted-principal";
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new TaskWritingController(
                                        creation,
                                        editing,
                                        statuses,
                                        deletion,
                                        new TaskRequestParser(),
                                        provider))
                        .setControllerAdvice(new TaskHttpExceptionHandler())
                        .build();
    }

    private List<MockHttpServletRequestBuilder> requests() {
        String base = "/api/v1/spaces/" + UUID.randomUUID() + "/tasks";
        String task = base + "/" + UUID.randomUUID();
        return List.of(
                post(base).content("{}"),
                patch(task).content("{}"),
                put(task + "/status").content("{}"),
                delete(task + "?confirmed=true"));
    }

    @Test
    void allWritesFailClosedWithoutSecurityAdapter() throws Exception {
        for (var request : requests()) {
            mvc.perform(request.principal(principal).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isServiceUnavailable());
        }
        verifyNoInteractions(creation, editing, statuses, deletion);
    }

    @Test
    void allWritesStopOnCsrfFailure() throws Exception {
        var guard = mock(TaskWriteSecurity.class);
        when(provider.getIfAvailable()).thenReturn(guard);
        when(guard.authorize(any())).thenThrow(new TaskCsrfException());
        for (var request : requests()) {
            mvc.perform(request.principal(principal).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(creation, editing, statuses, deletion);
    }

    @Test
    void unresolvedActorStopsBeforeBusinessServices() throws Exception {
        when(provider.getIfAvailable()).thenReturn(mock(TaskWriteSecurity.class));
        mvc.perform(requests().get(0).principal(principal).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(creation, editing, statuses, deletion);
    }
}
