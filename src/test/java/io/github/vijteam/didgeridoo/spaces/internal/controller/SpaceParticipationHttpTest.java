package io.github.vijteam.didgeridoo.spaces.internal.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.identity.internal.model.User;
import io.github.vijteam.didgeridoo.identity.internal.repository.UserRepository;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SpaceParticipationHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    User owner;
    User recipient;
    User guest;
    User outsider;
    UUID space;
    UUID otherSpace;
    final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner = user("o");
        recipient = user("r");
        guest = user("g");
        outsider = user("x");
        space = createSpace("Participation");
        otherSpace = createSpace("Other");
    }

    User user(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return users.saveAndFlush(
                new User(prefix + suffix, "Display", prefix + suffix + "@example.com", "hash"));
    }

    MockHttpSession login(User user) {
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        user.getUsername(), null, List.of()));
        var session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
        return session;
    }

    UUID createSpace(String name) throws Exception {
        String body =
                mvc.perform(
                                post("/api/v1/spaces")
                                        .session(login(owner))
                                        .with(csrf().asHeader())
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content("{\"name\":\"" + name + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(mapper.readTree(body).get("id").asString());
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_vlad_accept ON space_invitations");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_vlad_accept()");
        jdbc.update("DELETE FROM task_statuses WHERE space_id IN (?,?)", space, otherSpace);
        jdbc.update("DELETE FROM spaces WHERE id IN (?,?)", space, otherSpace);
        users.deleteAllById(
                List.of(owner.getId(), recipient.getId(), guest.getId(), outsider.getId()));
    }

    void member(User user, UUID space, String role) {
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,?::space_role)",
                space,
                user.getId(),
                role);
    }

    ResultActions invite(User actor, UUID targetSpace, String key, String body) throws Exception {
        return mvc.perform(
                post("/api/v1/spaces/" + targetSpace + "/invitations")
                        .session(login(actor))
                        .with(csrf().asHeader())
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body));
    }

    JsonNode invitation(User target) throws Exception {
        return mapper.readTree(
                invite(
                                owner,
                                space,
                                UUID.randomUUID().toString(),
                                "{\"username\":\"" + target.getUsername() + "\"}")
                        .andExpect(status().isCreated())
                        .andExpect(
                                header().string(
                                                "Location",
                                                org.hamcrest.Matchers.startsWith(
                                                        "/api/v1/invitations/")))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    ResultActions decide(User actor, String id, String state) throws Exception {
        return mvc.perform(
                patch("/api/v1/invitations/" + id)
                        .session(login(actor))
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content("{\"decision\":\"" + state + "\"}"));
    }

    int membership(User user) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM space_memberships WHERE space_id=? AND user_id=?",
                Integer.class,
                space,
                user.getId());
    }

    @Test
    void listsMembersWithPublicFieldsPaginationAndImmediateAccessRevocation() throws Exception {
        member(recipient, space, "MEMBER");
        member(guest, space, "GUEST");
        member(outsider, otherSpace, "MEMBER");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int page = 0; page < 3; page++) {
            String body =
                    mvc.perform(
                                    get("/api/v1/spaces/" + space + "/memberships")
                                            .session(login(guest))
                                            .param("size", "1")
                                            .param("page", String.valueOf(page)))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.totalElements").value(3))
                            .andExpect(jsonPath("$.items[0].email").doesNotExist())
                            .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertTrue(ids.add(mapper.readTree(body).get("items").get(0).get("userId").asString()));
        }
        assertEquals(
                java.util.Set.of(
                        owner.getId().toString(),
                        recipient.getId().toString(),
                        guest.getId().toString()),
                ids);
        mvc.perform(
                        get("/api/v1/spaces/" + space + "/memberships")
                                .session(login(owner))
                                .param("page", "3")
                                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/spaces/" + otherSpace + "/memberships").session(login(guest)))
                .andExpect(status().isNotFound());
        jdbc.update(
                "DELETE FROM space_memberships WHERE space_id=? AND user_id=?",
                space,
                guest.getId());
        mvc.perform(get("/api/v1/spaces/" + space + "/memberships").session(login(guest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptsDefaultMemberOnlyForRecipientAndReplaysSameDecision() throws Exception {
        JsonNode invited = invitation(recipient);
        String id = invited.get("id").asString();
        assertEquals("MEMBER", invited.get("role").asString());
        assertEquals(0, membership(recipient));
        mvc.perform(get("/api/v1/spaces/" + space).session(login(recipient)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/invitations/" + id).session(login(outsider)))
                .andExpect(status().isNotFound());
        decide(owner, id, "ACCEPTED").andExpect(status().isForbidden());
        decide(recipient, id, "ACCEPTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACCEPTED"));
        decide(recipient, id, "ACCEPTED").andExpect(status().isOk());
        assertEquals(1, membership(recipient));
        mvc.perform(get("/api/v1/spaces/" + space).session(login(recipient)))
                .andExpect(status().isOk());
        decide(recipient, id, "DECLINED").andExpect(status().isConflict());
        invite(
                        owner,
                        space,
                        UUID.randomUUID().toString(),
                        "{\"username\":\"" + recipient.getUsername() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_CONFLICT"));
    }

    @ParameterizedTest
    @EnumSource(SpaceRole.class)
    void grantsExactlyInvitedRole(SpaceRole role) throws Exception {
        String response =
                invite(
                                owner,
                                space,
                                UUID.randomUUID().toString(),
                                "{\"username\":\""
                                        + recipient.getUsername()
                                        + "\",\"role\":\""
                                        + role
                                        + "\"}")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        decide(recipient, mapper.readTree(response).get("id").asString(), "ACCEPTED")
                .andExpect(status().isOk());
        assertEquals(
                role.name(),
                jdbc.queryForObject(
                        "SELECT role FROM space_memberships WHERE space_id=? AND user_id=?",
                        String.class,
                        space,
                        recipient.getId()));
    }

    @Test
    void declinedAndCancelledInvitationsPermitNewInvitationButNeverGrantAccess() throws Exception {
        String first = invitation(recipient).get("id").asString();
        decide(recipient, first, "DECLINED").andExpect(status().isOk());
        decide(recipient, first, "DECLINED").andExpect(status().isOk());
        assertEquals(0, membership(recipient));
        String next = invitation(recipient).get("id").asString();
        decide(recipient, next, "CANCELLED").andExpect(status().isForbidden());
        decide(owner, next, "CANCELLED").andExpect(status().isOk());
        decide(owner, next, "CANCELLED").andExpect(status().isOk());
        decide(recipient, next, "ACCEPTED").andExpect(status().isConflict());
        assertEquals(0, membership(recipient));
        assertNotEquals(next, invitation(recipient).get("id").asString());
    }

    @Test
    void listsOnlyOwnOrOwnerVisibleInvitationsAndRejectsNonOwnerCreation() throws Exception {
        member(guest, space, "GUEST");
        member(outsider, space, "MEMBER");
        invitation(recipient);
        mvc.perform(get("/api/v1/users/me/invitations").session(login(recipient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].spaceName").value("Participation"));
        mvc.perform(get("/api/v1/users/me/invitations").session(login(guest)))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/spaces/" + space + "/invitations").session(login(owner)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/spaces/" + otherSpace + "/invitations").session(login(owner)))
                .andExpect(jsonPath("$.items").isEmpty());
        for (User user : List.of(guest, outsider)) {
            invite(
                            user,
                            space,
                            UUID.randomUUID().toString(),
                            "{\"username\":\"" + recipient.getUsername() + "\"}")
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/spaces/" + space + "/invitations").session(login(user)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void idempotencyReplaysCreationButNewKeyPendingAndChangedPayloadConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = "{\"username\":\"" + recipient.getUsername() + "\"}";
        String first =
                invite(owner, space, key, body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(
                first,
                invite(owner, space, key, body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        invite(owner, space, UUID.randomUUID().toString(), body).andExpect(status().isConflict());
        invite(
                        owner,
                        space,
                        key,
                        "{\"username\":\"" + recipient.getUsername() + "\",\"role\":\"GUEST\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void concurrentCreationHasOnlyOnePendingInvitation() throws Exception {
        String body = "{\"username\":\"" + recipient.getUsername() + "\"}";
        List<Integer> codes =
                race(
                        () ->
                                invite(owner, space, UUID.randomUUID().toString(), body)
                                        .andReturn()
                                        .getResponse()
                                        .getStatus(),
                        () ->
                                invite(owner, space, UUID.randomUUID().toString(), body)
                                        .andReturn()
                                        .getResponse()
                                        .getStatus());
        assertEquals(List.of(201, 409), codes.stream().sorted().toList());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_invitations WHERE space_id=? AND state='PENDING'",
                        Integer.class,
                        space));
    }

    @Test
    void acceptCancelAndAcceptDeclineRacesKeepMembershipConsistent() throws Exception {
        String first = invitation(recipient).get("id").asString();
        List<Integer> codes =
                race(
                        () ->
                                decide(recipient, first, "ACCEPTED")
                                        .andReturn()
                                        .getResponse()
                                        .getStatus(),
                        () ->
                                decide(owner, first, "CANCELLED")
                                        .andReturn()
                                        .getResponse()
                                        .getStatus());
        assertEquals(List.of(200, 409), codes.stream().sorted().toList());
        assertMembershipMatches(first);
        String second = invitation(outsider).get("id").asString();
        codes =
                race(
                        () ->
                                decide(outsider, second, "ACCEPTED")
                                        .andReturn()
                                        .getResponse()
                                        .getStatus(),
                        () ->
                                decide(outsider, second, "DECLINED")
                                        .andReturn()
                                        .getResponse()
                                        .getStatus());
        assertEquals(List.of(200, 409), codes.stream().sorted().toList());
        assertMembershipMatches(second);
    }

    void assertMembershipMatches(String id) {
        String state =
                jdbc.queryForObject(
                        "SELECT state FROM space_invitations WHERE id=?",
                        String.class,
                        UUID.fromString(id));
        assertEquals(
                state.equals("ACCEPTED") ? 1 : 0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_memberships m JOIN space_invitations i ON i.space_id=m.space_id AND i.invitee_user_id=m.user_id WHERE i.id=?",
                        Integer.class,
                        UUID.fromString(id)));
    }

    @Test
    void rollsBackMembershipWhenInvitationDecisionWriteFails() throws Exception {
        String id = invitation(recipient).get("id").asString();
        jdbc.execute(
                "CREATE FUNCTION fail_vlad_accept() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.state='ACCEPTED' THEN RAISE EXCEPTION 'Injected decision failure'; END IF; RETURN NEW; END $$");
        jdbc.execute(
                "CREATE TRIGGER fail_vlad_accept BEFORE UPDATE ON space_invitations FOR EACH ROW EXECUTE FUNCTION fail_vlad_accept()");
        decide(recipient, id, "ACCEPTED")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertEquals(0, membership(recipient));
        assertEquals(
                "PENDING",
                jdbc.queryForObject(
                        "SELECT state FROM space_invitations WHERE id=?",
                        String.class,
                        UUID.fromString(id)));
    }

    @Test
    void validatesSessionsCsrfBodiesPaginationAndUnknownRecipient() throws Exception {
        String path = "/api/v1/spaces/" + space + "/invitations";
        mvc.perform(get("/api/v1/users/me/invitations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/spaces/" + space + "/memberships"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path).session(login(owner)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        for (String body :
                List.of(
                        "{}",
                        "{\"username\":null}",
                        "{\"username\":42}",
                        "{\"username\":\"abc\",\"actorId\":\"x\"}",
                        "{\"username\":\"abc\",\"username\":\"def\"}",
                        "[]",
                        "{\"username\":\"abc\",\"role\":null}",
                        "{\"username\":\"abc\",\"role\":\"ADMIN\"}")) {
            invite(owner, space, UUID.randomUUID().toString(), body)
                    .andExpect(status().isBadRequest());
        }
        invite(owner, space, UUID.randomUUID().toString(), "{\"username\":\"UnknownUser\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID_REFERENCE"));
        for (String route :
                List.of(
                        path,
                        "/api/v1/spaces/" + space + "/memberships",
                        "/api/v1/users/me/invitations")) {
            mvc.perform(get(route).session(login(owner)).param("size", "101"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(route).session(login(owner)).param("page", "2147483647"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(route).session(login(owner)).param("page", "0", "1"))
                    .andExpect(status().isBadRequest());
        }
    }

    <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<T> a =
                    executor.submit(
                            () -> {
                                ready.await();
                                return first.call();
                            });
            Future<T> b =
                    executor.submit(
                            () -> {
                                ready.await();
                                return second.call();
                            });
            ready.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSameKeyReplaysOneInvitation() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = "{\"username\":\"" + recipient.getUsername() + "\"}";
        List<String> ids =
                race(
                        () ->
                                invite(owner, space, key, body)
                                        .andExpect(status().isCreated())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString(),
                        () ->
                                invite(owner, space, key, body)
                                        .andExpect(status().isCreated())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString());
        assertEquals(ids.get(0), ids.get(1));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_invitations WHERE space_id=?",
                        Integer.class,
                        space));
    }

    @Test
    void pendingInvitationDoesNotExpireAndReplayCannotRestoreRevokedMembership() throws Exception {
        String id = invitation(recipient).get("id").asString();
        jdbc.update(
                "UPDATE space_invitations SET created_at=now()-interval '365 days' WHERE id=?",
                UUID.fromString(id));
        decide(recipient, id, "ACCEPTED").andExpect(status().isOk());
        jdbc.update(
                "DELETE FROM space_memberships WHERE space_id=? AND user_id=?",
                space,
                recipient.getId());
        decide(recipient, id, "ACCEPTED").andExpect(status().isOk());
        assertEquals(0, membership(recipient));
        mvc.perform(get("/api/v1/spaces/" + space).session(login(recipient)))
                .andExpect(status().isNotFound());
    }
}
