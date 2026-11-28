package io.github.vijteam.didgeridoo.analytics.internal.controller;

import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.api.TaskQueries;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AnalyticsHttpTest {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private TaskQueries tasks;

    private final UUID space = UUID.randomUUID();
    private final UUID open = UUID.randomUUID();
    private final UUID done = UUID.randomUUID();
    private final UUID otherSpace = UUID.randomUUID();
    private final List<UUID> users = new ArrayList<>();
    private MockMvc mvc;
    private UUID owner;
    private UUID member;
    private UUID guest;
    private UUID outsider;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("INSERT INTO spaces(id,name) VALUES (?,'Analytics')", space);
        jdbc.update(
                """
                INSERT INTO task_statuses(id,space_id,name,kind,position)
                VALUES (?,?,'Done','FINAL',10), (?,?,'Open','INITIAL',0)
                """,
                done,
                space,
                open,
                space);
        owner = user();
        member = user();
        guest = user();
        outsider = user();
        membership(owner, "OWNER");
        membership(member, "MEMBER");
        membership(guest, "GUEST");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM tasks WHERE space_id IN (?, ?)", space, otherSpace);
        jdbc.update("DELETE FROM task_statuses WHERE space_id IN (?, ?)", space, otherSpace);
        jdbc.update("DELETE FROM spaces WHERE id IN (?, ?)", space, otherSpace);
        for (UUID user : users) {
            jdbc.update("DELETE FROM users WHERE id = ?", user);
        }
    }

    @Test
    void spaceAnalyticsIncludesAssignedAndUnassignedTasksForEveryRole() throws Exception {
        task(open, member);
        task(open, null);
        for (UUID actor : List.of(owner, member, guest)) {
            request(actor, spacePath())
                    .andExpect(status().isOk())
                    .andExpect(content().json(snapshot(2, 0), JsonCompareMode.STRICT));
        }
        request(outsider, spacePath())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void memberAnalyticsCountsOnlyCurrentAssignmentsForSelfAndOwner() throws Exception {
        task(open, member);
        task(done, owner);
        task(open, null);
        UUID otherStatus = UUID.randomUUID();
        jdbc.update("INSERT INTO spaces(id,name) VALUES (?,'Other')", otherSpace);
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,'OWNER'), (?,?,'MEMBER')",
                otherSpace,
                outsider,
                otherSpace,
                member);
        jdbc.update(
                "INSERT INTO task_statuses(id,space_id,name,kind,position) VALUES (?,?,'Open','INITIAL',0)",
                otherStatus,
                otherSpace);
        jdbc.update(
                """
                INSERT INTO tasks(space_id,creator_user_id,assignee_user_id,status_id,title,assignment_source)
                VALUES (?,?,?,?,'Task in another space','DIRECT')
                """,
                otherSpace,
                outsider,
                member,
                otherStatus);

        request(owner, spacePath())
                .andExpect(status().isOk())
                .andExpect(content().json(snapshot(2, 1), JsonCompareMode.STRICT));
        request(member, memberPath(member))
                .andExpect(status().isOk())
                .andExpect(content().json(snapshot(1, 0), JsonCompareMode.STRICT));
        request(owner, memberPath(member))
                .andExpect(status().isOk())
                .andExpect(content().json(snapshot(1, 0), JsonCompareMode.STRICT));
        request(owner, memberPath(owner))
                .andExpect(status().isOk())
                .andExpect(content().json(snapshot(0, 1), JsonCompareMode.STRICT));
        UUID otherOwner = user();
        membership(otherOwner, "OWNER");
        request(owner, memberPath(otherOwner))
                .andExpect(status().isOk())
                .andExpect(content().json(snapshot(0, 0), JsonCompareMode.STRICT));
        String otherPath = "/api/v1/spaces/" + otherSpace;
        request(owner, otherPath + "/analytics").andExpect(status().isNotFound());
        request(owner, otherPath + "/memberships/" + member + "/analytics")
                .andExpect(status().isNotFound());
    }

    @Test
    void memberAnalyticsHidesUnavailableTargetsAndDeniesUnauthorizedMembers() throws Exception {
        for (UUID target : List.of(guest, outsider, UUID.randomUUID())) {
            request(owner, memberPath(target))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            request(guest, memberPath(target))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        request(member, memberPath(owner))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        request(outsider, memberPath(member))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void analyticsRequiresASessionAndRejectsInvalidIdentifiersAndExtraParameters()
            throws Exception {
        for (String path : List.of(spacePath(), memberPath(member))) {
            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(get(path).session(session(owner)).queryParam("period", "day"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        request(owner, "/api/v1/spaces/1-1-1-1-1/analytics")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        request(owner, "/api/v1/spaces/" + space + "/memberships/invalid/analytics")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void periodUsesCreatedAndDeadlineBoundariesButKeepsCurrentStatusCounts() throws Exception {
        String from = "2025-01-01T00:00:00Z";
        String to = "2025-01-08T00:00:00Z";
        datedTask(open, member, from, from);
        datedTask(open, member, to, to);
        datedTask(done, member, "2025-01-07T00:00:00Z", from);
        datedTask(open, owner, "2024-12-31T00:00:00Z", from);

        mvc.perform(
                        get(spacePath())
                                .session(session(guest))
                                .queryParam("from", from)
                                .queryParam("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.overdue").value(2))
                .andExpect(jsonPath("$.statuses[0].count").value(3))
                .andExpect(jsonPath("$.statuses[1].count").value(1));
        mvc.perform(
                        get(memberPath(member))
                                .session(session(member))
                                .queryParam("from", from)
                                .queryParam("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.overdue").value(1))
                .andExpect(jsonPath("$.statuses[0].count").value(2));
        mvc.perform(
                        get(spacePath())
                                .session(session(owner))
                                .queryParam("from", to)
                                .queryParam("to", from))
                .andExpect(status().isBadRequest());
        mvc.perform(get(spacePath()).session(session(owner)).queryParam("from", from))
                .andExpect(status().isBadRequest());
    }

    private void datedTask(UUID status, UUID assignee, String created, String deadline) {
        jdbc.update(
                """
                INSERT INTO tasks(space_id, creator_user_id, assignee_user_id, status_id, title,
                                  assignment_source, created_at, deadline_at)
                VALUES (?, ?, ?, ?, 'Analytics dated task', 'DIRECT', CAST(? AS timestamptz), CAST(? AS timestamptz))
                """,
                space,
                owner,
                assignee,
                status,
                created,
                deadline);
    }

    @Test
    void roleCheckAndCountsShareASnapshotAcrossAConcurrentCommit() throws Exception {
        task(open, member);
        doAnswer(
                        invocation -> {
                            TransactionTemplate writer =
                                    new TransactionTemplate(transactionManager);
                            writer.setPropagationBehavior(
                                    TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                            writer.executeWithoutResult(
                                    transaction -> {
                                        jdbc.update(
                                                "DELETE FROM space_memberships WHERE space_id = ? AND user_id = ?",
                                                space,
                                                member);
                                        jdbc.update("DELETE FROM tasks WHERE space_id = ?", space);
                                    });
                            return invocation.callRealMethod();
                        })
                .when(tasks)
                .countByStatusForAssignee(space, member);

        request(owner, memberPath(member))
                .andExpect(status().isOk())
                .andExpect(content().json(snapshot(1, 0), JsonCompareMode.STRICT));
        request(owner, memberPath(member)).andExpect(status().isNotFound());
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO users(id,username,display_name,email,password_hash)
                VALUES (?,?,'Analytics tester',?,'hash')
                """,
                id,
                username(id),
                id + "@example.com");
        users.add(id);
        return id;
    }

    private void membership(UUID user, String role) {
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,?::space_role)",
                space,
                user,
                role);
    }

    private void task(UUID taskStatus, UUID assignee) {
        jdbc.update(
                """
                INSERT INTO tasks(space_id,creator_user_id,assignee_user_id,status_id,title,assignment_source)
                VALUES (?,?,?,?,'Analytics task',?)
                """,
                space,
                owner,
                assignee,
                taskStatus,
                assignee == null ? null : "DIRECT");
    }

    private ResultActions request(UUID actor, String path) throws Exception {
        return mvc.perform(get(path).session(session(actor)));
    }

    private static MockHttpSession session(UUID actor) {
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        username(actor), null, List.of()));
        var session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
        return session;
    }

    private static String username(UUID user) {
        return "a" + user.toString().substring(0, 18);
    }

    private String spacePath() {
        return "/api/v1/spaces/" + space + "/analytics";
    }

    private String memberPath(UUID user) {
        return "/api/v1/spaces/" + space + "/memberships/" + user + "/analytics";
    }

    private String snapshot(int openCount, int doneCount) {
        return """
                {"total":%d,"overdue":0,"statuses":[
                  {"statusId":"%s","name":"Open","position":0,"count":%d},
                  {"statusId":"%s","name":"Done","position":10,"count":%d}
                ]}
                """
                .formatted(openCount + doneCount, open, openCount, done, doneCount);
    }
}
