package io.github.vijteam.didgeridoo.tasks.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;
import io.github.vijteam.didgeridoo.tasks.internal.service.CreateTask;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskCreationService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskSpaceAccess;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskReadingHttpPersistenceTest {
    @Autowired private TaskReadingController controller;
    @Autowired private TaskHttpExceptionHandler errors;
    @Autowired private TaskCreationService creation;
    @Autowired private StatusInitializer initializer;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private TaskActorResolver actors;
    @MockitoBean private TaskSpaceAccess access;

    @Test
    void guestReadsPersistedTaskAndHistoryButLosesAccessAfterLeaving() throws Exception {
        UUID space = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Principal principal = () -> "trusted-test-principal";
        when(actors.resolve(principal)).thenReturn(actor);
        when(access.roleOf(space, actor)).thenReturn(TaskSpaceAccess.Role.OWNER);
        jdbc.update(
                "INSERT INTO users(id,username,display_name,email,password_hash) VALUES (?,?,'Test',?,'hash')",
                actor,
                actor.toString().substring(0, 20),
                actor + "@test.example");
        initializer.initialize(space);
        UUID task =
                creation.create(
                        space,
                        actor,
                        new CreateTask("Persisted", null, null, null, null, null, false));
        when(access.roleOf(space, actor)).thenReturn(TaskSpaceAccess.Role.GUEST);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(errors).build();
        String base = "/api/v1/spaces/" + space + "/tasks";
        mvc.perform(get(base + "/" + task).principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Persisted"));
        mvc.perform(get(base).principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(task.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(base + "/" + task + "/changes").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].kind").value("CREATED"))
                .andExpect(jsonPath("$.items[0].changedFields.title.new").value("Persisted"));
        when(access.roleOf(space, actor)).thenReturn(null);
        mvc.perform(get(base + "/" + task + "/changes").principal(principal))
                .andExpect(status().isNotFound());
        mvc.perform(get(base).principal(principal)).andExpect(status().isNotFound());
    }
}
