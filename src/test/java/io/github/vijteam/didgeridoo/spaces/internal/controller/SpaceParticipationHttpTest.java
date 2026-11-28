package io.github.vijteam.didgeridoo.spaces.internal.controller;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.identity.internal.model.User;
import io.github.vijteam.didgeridoo.identity.internal.repository.UserRepository;
import io.github.vijteam.didgeridoo.spaces.api.SpaceMembershipLifecycle;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.model.AssignmentSource;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskAssignmentService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskTagService;

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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SpaceParticipationHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired TaskAssignmentService assignments;
    @Autowired TaskTagService tags;
    @Autowired TransactionTemplate transaction;
    @Autowired SpaceMembershipLifecycle lifecycle;
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
        jdbc.update("DELETE FROM tasks WHERE space_id IN (?,?)", space, otherSpace);
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

    private String membershipPath(User target) {
        return "/api/v1/spaces/" + space + "/memberships/" + target.getId();
    }

    private ResultActions changeRole(User actor, User target, SpaceRole role) throws Exception {
        return mvc.perform(
                patch(membershipPath(target))
                        .session(login(actor))
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content("{\"role\":\"" + role + "\"}"));
    }

    @Test
    void invitationAcceptanceEnablesClaimAndRecordsTheRecipientInHistory() throws Exception {
        String invitationId = invitation(recipient).get("id").asString();
        UUID taskId =
                jdbc.queryForObject(
                        """
                INSERT INTO tasks(space_id, creator_user_id, status_id, title)
                SELECT space_id, ?, id, 'Claim after joining'
                FROM task_statuses WHERE space_id = ? AND kind = 'INITIAL'
                RETURNING id
                """,
                        UUID.class,
                        owner.getId(),
                        space);
        String claim = "/api/v1/spaces/" + space + "/tasks/" + taskId + "/claim";
        mvc.perform(
                        post(claim)
                                .session(login(recipient))
                                .with(csrf().asHeader())
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isNotFound());

        mvc.perform(
                        post(claim)
                                .with(csrf().asHeader())
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isUnauthorized());

        decide(recipient, invitationId, "ACCEPTED").andExpect(status().isOk());
        mvc.perform(
                        post(claim)
                                .session(login(recipient))
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        mvc.perform(
                        post(claim)
                                .session(login(recipient))
                                .with(csrf().asHeader())
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(recipient.getId().toString()))
                .andExpect(
                        jsonPath("$.assignmentSource").value(AssignmentSource.SELF_CLAIM.name()));
        mvc.perform(
                        post(claim)
                                .session(login(recipient))
                                .with(csrf().asHeader())
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ASSIGNMENT_CONFLICT"));
        assertEquals(
                recipient.getId(),
                jdbc.queryForObject(
                        "SELECT actor_user_id FROM task_changes WHERE task_id=? AND kind='UPDATED'",
                        UUID.class,
                        taskId));
        mvc.perform(delete(claim).session(login(recipient)).with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").isEmpty());
        assertNull(assignee(taskId));
    }

    @Test
    void accountRemovalCanFindSpacesAndChooseMemberBeforeOlderGuest() {
        member(guest, space, "GUEST");
        member(recipient, space, "MEMBER");
        member(outsider, space, "MEMBER");
        jdbc.update(
                "UPDATE space_memberships SET joined_at=TIMESTAMPTZ '2025-01-01' WHERE space_id=? AND user_id=?",
                space,
                guest.getId());
        jdbc.update(
                "UPDATE space_memberships SET joined_at=TIMESTAMPTZ '2025-02-01' WHERE space_id=? AND role='MEMBER'",
                space);
        assertEquals(
                java.util.stream.Stream.of(space, otherSpace)
                        .sorted(java.util.Comparator.comparing(UUID::toString))
                        .toList(),
                lifecycle.spacesForUser(owner.getId()));
        var firstMember =
                java.util.stream.Stream.of(recipient.getId(), outsider.getId())
                        .min(java.util.Comparator.comparing(UUID::toString))
                        .orElseThrow();
        transaction.executeWithoutResult(
                status -> {
                    lifecycle.lockSpace(space);
                    assertEquals(
                            firstMember, lifecycle.successor(space, owner.getId()).orElseThrow());
                    lifecycle.remove(space, recipient.getId());
                    lifecycle.remove(space, outsider.getId());
                    assertEquals(
                            guest.getId(), lifecycle.successor(space, owner.getId()).orElseThrow());
                    assertTrue(lifecycle.successor(space, guest.getId()).isEmpty());
                });
    }

    @Test
    void changesEligibleRolesAndProtectsMembershipWithRoleChecksAndCsrf() throws Exception {
        member(recipient, space, "MEMBER");
        member(guest, space, "GUEST");
        member(outsider, otherSpace, "MEMBER");
        var roleRequest =
                patch(membershipPath(guest))
                        .contentType("application/json")
                        .content("{\"role\":\"MEMBER\"}");
        mvc.perform(roleRequest.with(csrf().asHeader())).andExpect(status().isUnauthorized());
        mvc.perform(
                        patch(membershipPath(guest))
                                .session(login(owner))
                                .contentType("application/json")
                                .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        changeRole(outsider, guest, SpaceRole.MEMBER).andExpect(status().isNotFound());
        changeRole(owner, outsider, SpaceRole.OWNER).andExpect(status().isNotFound());
        changeRole(recipient, guest, SpaceRole.MEMBER).andExpect(status().isForbidden());
        changeRole(owner, owner, SpaceRole.MEMBER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_OWNER"));
        changeRole(owner, recipient, SpaceRole.GUEST)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("affectedCount"));
        changeRole(owner, guest, SpaceRole.MEMBER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(guest.getId().toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.email").doesNotExist());
        changeRole(owner, guest, SpaceRole.MEMBER).andExpect(status().isOk());
        changeRole(owner, guest, SpaceRole.OWNER).andExpect(status().isOk());
        changeRole(guest, owner, SpaceRole.MEMBER).andExpect(status().isOk());
        changeRole(owner, recipient, SpaceRole.OWNER).andExpect(status().isForbidden());
    }

    @Test
    void guestDemotionCountsAssignmentsAndRollsBackStaleConfirmation() throws Exception {
        member(recipient, space, "MEMBER");
        UUID task = claimFixture(recipient);
        String path = membershipPath(recipient);
        mvc.perform(get(path + "/assignment-impact").session(login(recipient)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path + "/assignment-impact").session(login(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(1));
        setGuest(path, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_CHANGE_CONFLICT"));
        assertEquals(recipient.getId(), assignee(task));
        assertEquals(1L, history(task));

        setGuest(path, 1).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("GUEST"));
        assertNull(assignee(task));
        assertEquals(2L, history(task));
        assertThrows(
                TaskAccessException.class, () -> assignments.claim(space, recipient.getId(), task));
        setGuest(path, 1).andExpect(status().isOk());
        assertEquals(2L, history(task));
    }

    @Test
    void concurrentClaimRechecksMembershipAfterGuestDemotionCommits() throws Exception {
        member(recipient, space, "MEMBER");
        UUID task = claimFixture(recipient);
        assignments.release(space, recipient.getId(), task);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var blockedClaim =
                    transaction.execute(
                            status -> {
                                assertDoesNotThrow(
                                        () ->
                                                setGuest(membershipPath(recipient), 0)
                                                        .andExpect(status().isOk()));
                                int blocker =
                                        jdbc.queryForObject(
                                                "SELECT pg_backend_pid()", Integer.class);
                                var pending =
                                        executor.submit(
                                                () ->
                                                        assignments.claim(
                                                                space, recipient.getId(), task));
                                awaitRequestBlockedBy(blocker);
                                return pending;
                            });
            var failure =
                    assertThrows(
                            ExecutionException.class, () -> blockedClaim.get(20, TimeUnit.SECONDS));
            assertInstanceOf(TaskAccessException.class, failure.getCause());
        }
        assertNull(assignee(task));
        assertEquals(2L, history(task));
    }

    @Test
    void exclusionClearsAssignmentsAndRetainsTaskAndHistory() throws Exception {
        member(recipient, space, "MEMBER");
        UUID task = claimFixture(recipient);
        String path = membershipPath(recipient);
        mvc.perform(delete(path).session(login(recipient)).with(csrf().asHeader()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(membershipPath(owner)).session(login(owner)).with(csrf().asHeader()))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(path).session(login(owner)).with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        assertEquals(0, membership(recipient));
        assertNull(assignee(task));
        assertEquals(2L, history(task));
    }

    @Test
    void ownerDepartureTransfersOwnershipAndReplaysTheStoredResult() throws Exception {
        member(recipient, space, "MEMBER");
        UUID task = claimFixture(owner);
        String key = UUID.randomUUID().toString();
        depart(owner, UUID.randomUUID().toString(), "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_OWNER"));
        String transfer = "{\"successorId\":\"" + recipient.getId() + "\"}";
        for (int attempt = 0; attempt < 2; attempt++) {
            depart(owner, key, transfer)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.outcome").value("TRANSFERRED"))
                    .andExpect(jsonPath("$.successorId").value(recipient.getId().toString()));
        }
        depart(owner, key, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertEquals(0, membership(owner));
        assertNull(assignee(task));
        assertEquals(2L, history(task));
        mvc.perform(get(membershipPath(recipient) + "/assignment-impact").session(login(recipient)))
                .andExpect(status().isOk());
    }

    @Test
    void departureWaitsForConcurrentTagCreationWithoutBlockingItsForeignKey() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var departure =
                    transaction.execute(
                            status -> {
                                jdbc.queryForObject(
                                        "SELECT id FROM task_statuses WHERE space_id = ? AND kind = 'INITIAL' FOR UPDATE",
                                        UUID.class,
                                        space);
                                int blocker =
                                        jdbc.queryForObject(
                                                "SELECT pg_backend_pid()", Integer.class);
                                var pending =
                                        executor.submit(
                                                () ->
                                                        depart(
                                                                owner,
                                                                UUID.randomUUID().toString(),
                                                                "{}"));
                                awaitRequestBlockedBy(blocker);
                                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                                tags.create(space, owner.getId(), "During departure");
                                return pending;
                            });
            departure
                    .get(20, TimeUnit.SECONDS)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.outcome").value("SPACE_DELETED"));
        }
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_tags WHERE space_id = ?", Long.class, space));
    }

    @Test
    void guardedDepartureCannotDeleteAfterAnotherOwnerLeaves() throws Exception {
        member(recipient, space, "OWNER");
        member(guest, space, "GUEST");
        depart(recipient, UUID.randomUUID().toString(), "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("LEFT"));

        String key = UUID.randomUUID().toString();
        depart(owner, key, "{\"allowAutomaticDeletion\":false}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_CHANGE_CONFLICT"));
        assertEquals(1, membership(owner));
        assertEquals(1, membership(guest));

        depart(owner, key, "{\"allowAutomaticDeletion\":false,\"deleteSpace\":true}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("SPACE_DELETED"));
        depart(owner, key, "{\"deleteSpace\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void guestCanLeaveAndLastOwnerDeletesSpaceWithOnlyGuests() throws Exception {
        member(guest, space, "GUEST");
        member(recipient, space, "GUEST");
        UUID task = claimFixture(owner);
        depart(guest, UUID.randomUUID().toString(), "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("LEFT"));
        assertEquals(0, membership(guest));
        String key = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            depart(owner, key, "{}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.outcome").value("SPACE_DELETED"));
        }
        assertEquals(0, membership(recipient));
        assertEquals(0L, history(task));
        assertEquals(
                0L,
                jdbc.queryForObject("SELECT count(*) FROM spaces WHERE id=?", Long.class, space));
        assertEquals(
                0L, jdbc.queryForObject("SELECT count(*) FROM tasks WHERE id=?", Long.class, task));
    }

    private ResultActions setGuest(String path, long count) throws Exception {
        return mvc.perform(
                patch(path)
                        .session(login(owner))
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content("{\"role\":\"GUEST\",\"affectedCount\":" + count + "}"));
    }

    private ResultActions depart(User actor, String key, String body) throws Exception {
        return mvc.perform(
                post("/api/v1/spaces/" + space + "/departures")
                        .session(login(actor))
                        .with(csrf().asHeader())
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body));
    }

    private UUID claimFixture(User actor) {
        UUID task =
                jdbc.queryForObject(
                        """
                INSERT INTO tasks(space_id, creator_user_id, status_id, title)
                SELECT space_id, ?, id, 'Membership assignment'
                FROM task_statuses WHERE space_id = ? AND kind = 'INITIAL'
                RETURNING id
                """,
                        UUID.class,
                        owner.getId(),
                        space);
        assignments.claim(space, actor.getId(), task);
        return task;
    }

    private UUID assignee(UUID task) {
        return jdbc.queryForObject(
                "SELECT assignee_user_id FROM tasks WHERE id=?", UUID.class, task);
    }

    private long history(UUID task) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM task_changes WHERE task_id=?", Long.class, task);
    }

    @Test
    void validatesRoleChangesWithoutChangingMembership() throws Exception {
        member(recipient, space, "MEMBER");
        for (String invalid :
                List.of(
                        "{}",
                        "{\"role\":null}",
                        "{\"role\":\"ADMIN\"}",
                        "{\"role\":\"MEMBER\",\"role\":\"OWNER\"}",
                        "{\"role\":\"OWNER\",\"userId\":\"override\"}")) {
            mvc.perform(
                            patch(membershipPath(recipient))
                                    .session(login(owner))
                                    .with(csrf().asHeader())
                                    .contentType("application/json")
                                    .content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(
                        patch(membershipPath(recipient))
                                .session(login(owner))
                                .with(csrf().asHeader())
                                .contentType("text/plain")
                                .content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        assertEquals(
                "MEMBER",
                jdbc.queryForObject(
                        "SELECT role FROM space_memberships WHERE space_id=? AND user_id=?",
                        String.class,
                        space,
                        recipient.getId()));
    }

    @Test
    void concurrentOwnerDemotionsRetainOneOwner() throws Exception {
        member(recipient, space, "OWNER");
        try (var executor = Executors.newSingleThreadExecutor()) {
            var second =
                    transaction.execute(
                            transactionStatus -> {
                                assertDoesNotThrow(
                                        () ->
                                                changeRole(owner, owner, SpaceRole.MEMBER)
                                                        .andExpect(status().isOk()));
                                int blocker =
                                        jdbc.queryForObject(
                                                "SELECT pg_backend_pid()", Integer.class);
                                var pending =
                                        executor.submit(
                                                () ->
                                                        changeRole(
                                                                recipient,
                                                                recipient,
                                                                SpaceRole.MEMBER));
                                awaitRequestBlockedBy(blocker);
                                return pending;
                            });
            second.get(20, TimeUnit.SECONDS)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LAST_OWNER"));
        }
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_memberships WHERE space_id=? AND role='OWNER'",
                        Integer.class,
                        space));
    }

    private void awaitRequestBlockedBy(int backendPid) {
        String query =
                """
                SELECT EXISTS (
                    SELECT 1 FROM pg_stat_activity
                    WHERE ? = ANY(pg_blocking_pids(pid))
                )
                """;
        await().pollInSameThread()
                .atMost(Duration.ofSeconds(10))
                .until(
                        () ->
                                Boolean.TRUE.equals(
                                        jdbc.queryForObject(query, Boolean.class, backendPid)));
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
