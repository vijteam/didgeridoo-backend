package io.github.vijteam.didgeridoo.spaces.internal.repository;

import io.github.vijteam.didgeridoo.spaces.api.*;
import io.github.vijteam.didgeridoo.spaces.internal.service.InvitationStore;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcInvitationStore implements InvitationStore {
    private static final String SELECT =
            "SELECT i.*, s.name AS space_name FROM space_invitations i JOIN spaces s ON s.id=i.space_id ";
    private static final RowMapper<SpaceInvitation> MAPPER =
            (row, index) ->
                    new SpaceInvitation(
                            row.getObject("id", UUID.class),
                            row.getObject("space_id", UUID.class),
                            row.getString("space_name"),
                            row.getObject("invitee_user_id", UUID.class),
                            row.getObject("inviter_user_id", UUID.class),
                            SpaceRole.valueOf(row.getString("role")),
                            InvitationState.valueOf(row.getString("state")),
                            row.getTimestamp("created_at").toInstant(),
                            row.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;

    public JdbcInvitationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockRequest(UUID actorId, UUID requestId) {
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                result -> {
                    return null;
                },
                actorId + ":" + requestId);
    }

    @Override
    public boolean lockSpace(UUID spaceId) {
        return !jdbc.queryForList(
                        "SELECT id FROM spaces WHERE id=? FOR UPDATE", UUID.class, spaceId)
                .isEmpty();
    }

    @Override
    public Optional<SpaceInvitation> findByRequest(UUID actorId, UUID requestId) {
        return jdbc
                .query(
                        SELECT + "WHERE i.inviter_user_id=? AND i.request_id=?",
                        MAPPER,
                        actorId,
                        requestId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<SpaceInvitation> find(UUID invitationId) {
        return jdbc.query(SELECT + "WHERE i.id=?", MAPPER, invitationId).stream().findFirst();
    }

    @Override
    public Optional<SpaceInvitation> lock(UUID invitationId) {
        return jdbc.query(SELECT + "WHERE i.id=? FOR UPDATE OF i", MAPPER, invitationId).stream()
                .findFirst();
    }

    @Override
    public boolean isMember(UUID spaceId, UUID userId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM space_memberships WHERE space_id=? AND user_id=?)",
                        Boolean.class,
                        spaceId,
                        userId));
    }

    @Override
    public boolean hasPending(UUID spaceId, UUID userId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM space_invitations WHERE space_id=? AND invitee_user_id=? AND state='PENDING')",
                        Boolean.class,
                        spaceId,
                        userId));
    }

    @Override
    public SpaceInvitation create(
            UUID spaceId, UUID inviterId, UUID inviteeId, UUID requestId, SpaceRole role) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO space_invitations(id,space_id,inviter_user_id,invitee_user_id,request_id,role) VALUES (?,?,?,?,?,?::space_role)",
                id,
                spaceId,
                inviterId,
                inviteeId,
                requestId,
                role.name());
        return find(id).orElseThrow();
    }

    @Override
    public SpaceInvitation decide(UUID invitationId, InvitationState state) {
        int changed =
                jdbc.update(
                        "UPDATE space_invitations SET state=?,updated_at=now() WHERE id=? AND state='PENDING'",
                        state.name(),
                        invitationId);
        if (changed != 1) {
            throw new IllegalStateException("Pending invitation changed concurrently");
        }
        return find(invitationId).orElseThrow();
    }

    @Override
    public void addMember(UUID spaceId, UUID userId, SpaceRole role) {
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,?::space_role)",
                spaceId,
                userId,
                role.name());
    }

    @Override
    public Page forSpace(UUID spaceId, SpacePageWindow window) {
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_invitations WHERE space_id=?",
                        Long.class,
                        spaceId);
        var items =
                jdbc.query(
                        SELECT + "WHERE i.space_id=? ORDER BY i.created_at,i.id LIMIT ? OFFSET ?",
                        MAPPER,
                        spaceId,
                        window.size(),
                        window.offset());
        return new Page(items, total);
    }

    @Override
    public Page forRecipient(UUID actorId, SpacePageWindow window) {
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_invitations WHERE invitee_user_id=?",
                        Long.class,
                        actorId);
        var items =
                jdbc.query(
                        SELECT
                                + "WHERE i.invitee_user_id=? ORDER BY i.created_at,i.id LIMIT ? OFFSET ?",
                        MAPPER,
                        actorId,
                        window.size(),
                        window.offset());
        return new Page(items, total);
    }
}
