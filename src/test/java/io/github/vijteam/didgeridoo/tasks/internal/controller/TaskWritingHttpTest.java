package io.github.vijteam.didgeridoo.tasks.internal.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskSpaceAccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.UUID;

@SpringBootTest(properties = "didgeridoo.tasks.http.writes-enabled=true")
@Import(TestcontainersConfiguration.class)
class TaskWritingHttpTest {
    @Autowired private TaskWritingController controller;
    @Autowired private TaskHttpExceptionHandler errors;
    @Autowired private StatusInitializer initializer;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private TaskWriteSecurity security;
    @MockitoBean private TaskSpaceAccess access;
    private final Principal principal = () -> "trusted-test-principal";
    private UUID space;
    private UUID actor;
    private MockMvc mvc;

    private String base() {
        return "/api/v1/spaces/" + space + "/tasks";
    }

    @BeforeEach
    void setup() {
        space = UUID.randomUUID();
        actor = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id,username,display_name,email,password_hash) VALUES (?,?,'Test',?,'hash')",
                actor,
                actor.toString().substring(0, 20),
                actor + "@test.example");
        initializer.initialize(space);
        when(security.authorize(any())).thenReturn(actor);
        when(access.roleOf(space, actor)).thenReturn(TaskSpaceAccess.Role.OWNER);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(errors).build();
    }

    private UUID create() throws Exception {
        mvc.perform(
                        post(base())
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"HTTP task\",\"description\":\"Keep\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value("HTTP task"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
        return jdbc.queryForObject("SELECT id FROM tasks WHERE space_id=?", UUID.class, space);
    }

    @Test
    void createPatchChangeStatusAndDeleteThroughRealServices() throws Exception {
        UUID task = create();
        mvc.perform(
                        patch(base() + "/" + task)
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Changed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Changed"))
                .andExpect(jsonPath("$.description").value("Keep"));
        UUID finalStatus =
                jdbc.queryForObject(
                        "SELECT id FROM task_statuses WHERE space_id=? AND kind='FINAL'",
                        UUID.class,
                        space);
        mvc.perform(
                        put(base() + "/" + task + "/status")
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"statusId\":\"" + finalStatus + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusId").value(finalStatus.toString()));
        assertEquals(
                3,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_changes WHERE task_id=?", Integer.class, task));
        mvc.perform(delete(base() + "/" + task + "?confirmed=true").principal(principal))
                .andExpect(status().isNoContent());
        assertEquals(
                0,
                jdbc.queryForObject("SELECT count(*) FROM tasks WHERE id=?", Integer.class, task));
    }

    @Test
    void rejectsUnauthenticatedGuestAndCsrfFailureBeforeWriting() throws Exception {
        mvc.perform(
                        post(base())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Task\"}"))
                .andExpect(status().isUnauthorized());
        when(access.roleOf(space, actor)).thenReturn(TaskSpaceAccess.Role.GUEST);
        mvc.perform(
                        post(base())
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Task\"}"))
                .andExpect(status().isForbidden());
        when(security.authorize(any())).thenThrow(new TaskCsrfException());
        mvc.perform(
                        post(base())
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Task\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM tasks WHERE space_id=?", Integer.class, space));
    }

    @Test
    void rejectsMalformedUnknownFieldsAndUnconfirmedDelete() throws Exception {
        mvc.perform(
                        post(base())
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"A\",\"title\":\"B\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
        mvc.perform(
                        post(base())
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"A\",\"actorId\":\"fake\"}"))
                .andExpect(status().isBadRequest());
        UUID task = create();
        mvc.perform(delete(base() + "/" + task).principal(principal))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(base() + "/" + task + "?confirmed=false").principal(principal))
                .andExpect(status().isBadRequest());
        assertEquals(
                1,
                jdbc.queryForObject("SELECT count(*) FROM tasks WHERE id=?", Integer.class, task));
    }
}
