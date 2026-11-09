package io.github.vijteam.didgeridoo.spaces.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.identity.internal.model.User;
import io.github.vijteam.didgeridoo.identity.internal.repository.UserRepository;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;
import io.github.vijteam.didgeridoo.spaces.api.SpaceSummary;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembership;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembershipId;
import io.github.vijteam.didgeridoo.spaces.internal.repository.SpaceMembershipRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SpaceServiceIntegrationTest {
    @Autowired private SpaceService service;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private SpaceMembershipRepository memberships;

    private User creator;
    private String spaceName;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        creator =
                users.saveAndFlush(
                        new User("u" + suffix, "Creator", suffix + "@example.com", "hash"));
        spaceName = "service-" + suffix;
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM spaces WHERE name = ?", spaceName);
        users.deleteById(creator.getId());
    }

    @Test
    void shouldCommitSpaceAndOwnerTogether() {
        SpaceSummary created = service.createWithOwner(creator.getId(), spaceName);

        assertNotNull(created.createdAt());
        assertEquals(spaceName, service.get(creator.getId(), created.id()).name());
        assertEquals(
                SpaceRole.OWNER,
                memberships
                        .findById(new SpaceMembershipId(created.id(), creator.getId()))
                        .orElseThrow()
                        .getRole());
        assertEquals(1, service.search(creator.getId(), spaceName, 0, 20).getTotalElements());
    }

    @Test
    void shouldRollbackFlushedSpaceWhenMembershipWriteFails() {
        assertTrue(AopUtils.isAopProxy(service));
        doThrow(new IllegalStateException("Membership storage failure"))
                .when(memberships)
                .saveAndFlush(any(SpaceMembership.class));

        assertThrows(
                IllegalStateException.class,
                () -> service.createWithOwner(creator.getId(), spaceName));

        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM spaces WHERE name = ?", Integer.class, spaceName));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_memberships WHERE user_id = ?",
                        Integer.class,
                        creator.getId()));
    }
}
