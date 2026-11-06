package io.github.vijteam.didgeridoo.spaces.internal.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.identity.internal.model.User;
import io.github.vijteam.didgeridoo.identity.internal.repository.UserRepository;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;
import io.github.vijteam.didgeridoo.spaces.internal.model.Space;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembership;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembershipId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SpaceRepositoryTest {

    @Autowired private SpaceRepository spaces;

    @Autowired private SpaceMembershipRepository memberships;

    @Autowired private UserRepository users;

    @Autowired private JdbcTemplate jdbc;

    @PersistenceContext private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(SpaceRole.class)
    void shouldPersistSpaceAndMembershipWithEveryRole(SpaceRole role) {
        User user = createUser();
        Space space = spaces.saveAndFlush(new Space("  Project space  "));
        SpaceMembership membership =
                memberships.saveAndFlush(new SpaceMembership(space.getId(), user.getId(), role));
        SpaceMembershipId key = membership.getId();

        entityManager.clear();

        Space loaded = spaces.findById(space.getId()).orElseThrow();
        SpaceMembership loadedMembership = memberships.findById(key).orElseThrow();
        assertEquals("  Project space  ", loaded.getName());
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
        assertEquals(role, loadedMembership.getRole());
        assertEquals(space.getId(), loadedMembership.getId().getSpaceId());
        assertEquals(user.getId(), loadedMembership.getId().getUserId());
        assertNotNull(loadedMembership.getJoinedAt());
    }

    @Test
    void shouldReturnOnlyMembersSpacesAndSearchIgnoringCase() {
        User alice = createUser();
        User bob = createUser();
        Space alpha = createSpace("Alpha Project", alice, SpaceRole.GUEST);
        createSpace("Beta Project", alice, SpaceRole.MEMBER);
        createSpace("Alpha Secret", bob, SpaceRole.OWNER);

        Page<Space> result = search(alice.getId(), "ALPHA", 0, 10);

        assertEquals(List.of(alpha.getId()), result.map(Space::getId).getContent());
        assertEquals(1, result.getTotalElements());
        assertEquals(2, search(alice.getId(), "", 0, 10).getTotalElements());
        assertTrue(search(UUID.randomUUID(), "", 0, 10).isEmpty());
        assertTrue(search(alice.getId(), "missing", 0, 10).isEmpty());
    }

    @Test
    void shouldTreatPercentAndUnderscoreAsLiteralSearchCharacters() {
        User user = createUser();
        Space percent = createSpace("Progress 100%", user, SpaceRole.OWNER);
        Space underscore = createSpace("team_one", user, SpaceRole.OWNER);
        createSpace("teamXone", user, SpaceRole.OWNER);

        assertEquals(
                List.of(percent.getId()),
                search(user.getId(), "%", 0, 10).map(Space::getId).getContent());
        assertEquals(
                List.of(underscore.getId()),
                search(user.getId(), "_", 0, 10).map(Space::getId).getContent());
    }

    @Test
    void shouldPageWithStableNameAndIdOrdering() {
        User user = createUser();
        createSpace("Same", user, SpaceRole.OWNER);
        createSpace("Same", user, SpaceRole.OWNER);
        createSpace("Same", user, SpaceRole.OWNER);
        createSpace("Zebra", user, SpaceRole.OWNER);

        List<UUID> expected =
                jdbc.queryForList(
                        "SELECT s.id FROM spaces s JOIN space_memberships m ON m.space_id = s.id"
                                + " WHERE m.user_id = ? ORDER BY s.name, s.id",
                        UUID.class,
                        user.getId());
        Page<Space> first = search(user.getId(), "", 0, 2);
        Page<Space> second = search(user.getId(), "", 1, 2);

        assertEquals(4, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        assertEquals(expected.subList(0, 2), first.map(Space::getId).getContent());
        assertEquals(expected.subList(2, 4), second.map(Space::getId).getContent());
        assertEquals("Zebra", second.getContent().get(1).getName());
        assertTrue(search(user.getId(), "", 2, 2).isEmpty());
    }

    @Test
    void shouldReturnEmptyForUnknownMembership() {
        assertFalse(
                memberships
                        .findById(new SpaceMembershipId(UUID.randomUUID(), UUID.randomUUID()))
                        .isPresent());
    }

    @Test
    void shouldRejectDuplicateMembershipInDatabase() {
        User user = createUser();
        Space space = createSpace("Project", user, SpaceRole.OWNER);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertMembership(space.getId(), user.getId(), "MEMBER"));
    }

    @Test
    void shouldRejectUnknownUserForeignKey() {
        Space space = spaces.saveAndFlush(new Space("Project"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertMembership(space.getId(), UUID.randomUUID(), "MEMBER"));
    }

    @Test
    void shouldRejectUnknownSpaceForeignKey() {
        User user = createUser();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertMembership(UUID.randomUUID(), user.getId(), "MEMBER"));
    }

    @Test
    void shouldRejectUnknownRoleInDatabase() {
        User user = createUser();
        Space space = spaces.saveAndFlush(new Space("Project"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertMembership(space.getId(), user.getId(), "ADMIN"));
    }

    @Test
    void shouldRejectNullRoleInDatabase() {
        User user = createUser();
        Space space = spaces.saveAndFlush(new Space("Project"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertMembership(space.getId(), user.getId(), null));
    }

    @Test
    void shouldCascadeMembershipDeletionWhenSpaceIsDeleted() {
        User user = createUser();
        Space space = createSpace("Project", user, SpaceRole.OWNER);

        jdbc.update("DELETE FROM spaces WHERE id = ?", space.getId());
        entityManager.clear();

        assertTrue(
                memberships.findById(new SpaceMembershipId(space.getId(), user.getId())).isEmpty());
        assertTrue(users.existsById(user.getId()));
    }

    @Test
    void shouldCascadeMembershipDeletionWhenUserIsDeleted() {
        User user = createUser();
        Space space = createSpace("Project", user, SpaceRole.OWNER);

        jdbc.update("DELETE FROM users WHERE id = ?", user.getId());
        entityManager.clear();

        assertTrue(
                memberships.findById(new SpaceMembershipId(space.getId(), user.getId())).isEmpty());
        assertTrue(spaces.existsById(space.getId()));
    }

    @Test
    void shouldAcceptMaximumUnicodeNameLengthInPostgres() {
        String name = "\uD83D\uDE00".repeat(150);
        Space space = spaces.saveAndFlush(new Space(name));
        entityManager.clear();

        assertEquals(name, spaces.findById(space.getId()).orElseThrow().getName());
    }

    @Test
    void shouldRejectOverlongNameInDatabase() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO spaces (name) VALUES (?)", "x".repeat(151)));
    }

    @Test
    void shouldRejectNullNameInDatabase() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO spaces (name) VALUES (?)", (Object) null));
    }

    private User createUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return users.saveAndFlush(
                new User("u" + suffix, "Test User", suffix + "@example.com", "hash"));
    }

    private Space createSpace(String name, User user, SpaceRole role) {
        Space space = spaces.saveAndFlush(new Space(name));
        memberships.saveAndFlush(new SpaceMembership(space.getId(), user.getId(), role));
        return space;
    }

    private Page<Space> search(UUID userId, String name, int page, int size) {
        return spaces.findByMemberUserIdAndNameContainingIgnoreCase(
                userId, name, PageRequest.of(page, size, Sort.by("name", "id")));
    }

    private void insertMembership(UUID spaceId, UUID userId, String role) {
        jdbc.update(
                "INSERT INTO space_memberships (space_id, user_id, role) VALUES (?, ?, CAST(? AS space_role))",
                spaceId,
                userId,
                role);
    }
}
