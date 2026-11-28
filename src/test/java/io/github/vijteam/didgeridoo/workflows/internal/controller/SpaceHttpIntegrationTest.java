package io.github.vijteam.didgeridoo.workflows.internal.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.identity.internal.model.User;
import io.github.vijteam.didgeridoo.identity.internal.repository.UserRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SpaceHttpIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    User owner;
    User stranger;
    MockHttpSession session;
    String name;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        owner =
                users.saveAndFlush(
                        new User("o" + suffix, "Owner", "o" + suffix + "@example.com", "hash"));
        stranger =
                users.saveAndFlush(
                        new User("s" + suffix, "Stranger", "s" + suffix + "@example.com", "hash"));
        session = login(owner.getUsername());
        name = "http-" + suffix;
    }

    MockHttpSession login(String username) {
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        username, null, java.util.List.of()));
        var session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
        return session;
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_http_status ON task_statuses");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_http_status()");
        jdbc.update(
                "DELETE FROM task_statuses WHERE space_id IN (SELECT id FROM spaces WHERE name=?)",
                name);
        jdbc.update("DELETE FROM spaces WHERE name=?", name);
        users.deleteById(owner.getId());
        users.deleteById(stranger.getId());
    }

    String create() throws Exception {
        String response =
                mvc.perform(
                                post("/api/v1/spaces")
                                        .session(session)
                                        .with(csrf().asHeader())
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content("{\"name\":\"\u00a0" + name + "\u00a0\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(
                                header().string(
                                                "Location",
                                                org.hamcrest.Matchers.startsWith(
                                                        "/api/v1/spaces/")))
                        .andExpect(jsonPath("$.name").value(name))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonMapper.builder().build().readTree(response).get("id").asString();
    }

    @Test
    void createsOwnerAndStatusesAndOnlyListsVisibleSpaces() throws Exception {
        String id = create();
        UUID space = UUID.fromString(id);
        assertEquals(
                "OWNER",
                jdbc.queryForObject(
                        "SELECT role FROM space_memberships WHERE space_id=? AND user_id=?",
                        String.class,
                        space,
                        owner.getId()));
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_statuses WHERE space_id=?",
                        Integer.class,
                        space));
        assertEquals(
                java.util.List.of("INITIAL", "FINAL"),
                jdbc.queryForList(
                        "SELECT kind FROM task_statuses WHERE space_id=? ORDER BY position",
                        String.class,
                        space));
        mvc.perform(get("/api/v1/spaces/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/v1/spaces").session(session).param("name", name).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(
                        get("/api/v1/spaces")
                                .session(session)
                                .param("name", name)
                                .param("page", "1")
                                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(
                        get("/api/v1/spaces")
                                .session(login(stranger.getUsername()))
                                .param("name", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/spaces/" + id).session(login(stranger.getUsername())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/spaces/" + UUID.randomUUID()).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        for (String role : java.util.List.of("MEMBER", "GUEST")) {
            jdbc.update(
                    "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,?::space_role) ON CONFLICT (space_id,user_id) DO UPDATE SET role=EXCLUDED.role",
                    space,
                    stranger.getId(),
                    role);
            mvc.perform(get("/api/v1/spaces/" + id).session(login(stranger.getUsername())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void rejectsMissingSessionCsrfAndDeletedUser() throws Exception {
        mvc.perform(get("/api/v1/spaces"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(
                        post("/api/v1/spaces")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/spaces")
                                .session(session)
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        mvc.perform(
                        post("/api/v1/spaces")
                                .session(session)
                                .with(csrf().useInvalidToken().asHeader())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/spaces").session(login("deleted-user")))
                .andExpect(status().isUnauthorized());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM spaces WHERE name=?", Integer.class, name));
    }

    @Test
    void strictlyValidatesBodyAndPagination() throws Exception {
        for (String body :
                java.util.List.of(
                        "{}",
                        "{\"name\":null}",
                        "{\"name\":42}",
                        "{\"name\":\" \"}",
                        "{\"name\":\"x\",\"actorId\":\"fake\"}",
                        "{\"name\":\"a\",\"name\":\"b\"}",
                        "{",
                        "[]",
                        "{\"name\":\"x\"} {}",
                        "{\"name\":\"a\\u202Eb\"}")) {
            mvc.perform(
                            post("/api/v1/spaces")
                                    .session(session)
                                    .with(csrf().asHeader())
                                    .header("Idempotency-Key", UUID.randomUUID().toString())
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String[] pair :
                java.util.List.of(
                        new String[] {"page", "-1"},
                        new String[] {"page", "2147483648"},
                        new String[] {"page", "2147483647"},
                        new String[] {"size", "0"},
                        new String[] {"size", "101"},
                        new String[] {"actorId", "fake"})) {
            mvc.perform(get("/api/v1/spaces").session(session).param(pair[0], pair[1]))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/spaces").session(session).param("page", "0", "1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/spaces/not-a-uuid").session(session))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/spaces")
                                .session(session)
                                .with(csrf().asHeader())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType("text/plain")
                                .content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void rollsBackSpaceOwnerAndFirstStatusWhenSecondStatusFails() throws Exception {
        int originalStatusCount =
                jdbc.queryForObject("SELECT count(*) FROM task_statuses", Integer.class);
        jdbc.execute(
                "CREATE FUNCTION fail_http_status() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.kind='FINAL' THEN RAISE EXCEPTION 'Injected status failure'; END IF; RETURN NEW; END $$");
        jdbc.execute(
                "CREATE TRIGGER fail_http_status BEFORE INSERT ON task_statuses FOR EACH ROW EXECUTE FUNCTION fail_http_status()");
        mvc.perform(
                        post("/api/v1/spaces")
                                .session(session)
                                .with(csrf().asHeader())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType("application/json")
                                .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM spaces WHERE name=?", Integer.class, name));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_memberships WHERE user_id=?",
                        Integer.class,
                        owner.getId()));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_creation_requests WHERE actor_id=?",
                        Integer.class,
                        owner.getId()));
        assertEquals(
                originalStatusCount,
                jdbc.queryForObject("SELECT count(*) FROM task_statuses", Integer.class));
    }

    @Test
    void replaysCreationAndRejectsChangedPayload() throws Exception {
        String key = UUID.randomUUID().toString();
        var first = submit(session, key, name).andExpect(status().isCreated()).andReturn();
        var replay =
                submit(session, key, "\u00a0" + name + "\u00a0")
                        .andExpect(status().isCreated())
                        .andReturn();
        assertEquals(
                first.getResponse().getContentAsString(),
                replay.getResponse().getContentAsString());
        assertEquals(
                first.getResponse().getHeader("Location"),
                replay.getResponse().getHeader("Location"));
        submit(session, key, name + "changed")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM spaces WHERE name=?", Integer.class, name));
        submit(login(stranger.getUsername()), key, name).andExpect(status().isCreated());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT count(*) FROM spaces WHERE name=?", Integer.class, name));
    }

    @Test
    void concurrentSameKeyCreatesOnlyOneSpaceAndTwoStatuses() throws Exception {
        String key = UUID.randomUUID().toString();
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<String> request =
                    () -> {
                        start.await();
                        return submit(login(owner.getUsername()), key, name)
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getHeader("Location");
                    };
            var first = executor.submit(request);
            var second = executor.submit(request);
            start.countDown();
            assertEquals(
                    first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM spaces WHERE name=?", Integer.class, name));
            assertEquals(
                    2,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM task_statuses WHERE space_id IN (SELECT id FROM spaces WHERE name=?)",
                            Integer.class,
                            name));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsMissingOrInvalidIdempotencyKey() throws Exception {
        mvc.perform(
                        post("/api/v1/spaces")
                                .session(session)
                                .with(csrf().asHeader())
                                .contentType("application/json")
                                .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));
        submit(session, "bad", name).andExpect(status().isBadRequest());
    }

    org.springframework.test.web.servlet.ResultActions submit(
            MockHttpSession session, String key, String name) throws Exception {
        return mvc.perform(
                post("/api/v1/spaces")
                        .session(session)
                        .with(csrf().asHeader())
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\"}"));
    }

    @Test
    void acceptsRealFrameworkCsrfHeaderBoundToSession() throws Exception {
        var listing =
                mvc.perform(get("/api/v1/spaces").session(session))
                        .andExpect(status().isOk())
                        .andReturn();
        var token =
                (org.springframework.security.web.csrf.CsrfToken)
                        listing.getRequest()
                                .getAttribute(
                                        org.springframework.security.web.csrf.CsrfToken.class
                                                .getName());
        assertNotNull(token);
        mvc.perform(
                        post("/api/v1/spaces")
                                .session(session)
                                .header(token.getHeaderName(), token.getToken())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType("application/json")
                                .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsMalformedUnicodeBeforeReservingKey() throws Exception {
        for (String body : java.util.List.of("{\"name\":\"\\uD800\"}", "{\"name\":\"\\uDC00\"}")) {
            mvc.perform(
                            post("/api/v1/spaces")
                                    .session(session)
                                    .with(csrf().asHeader())
                                    .header("Idempotency-Key", UUID.randomUUID().toString())
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        }
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_creation_requests WHERE actor_id=?",
                        Integer.class,
                        owner.getId()));
    }
}
