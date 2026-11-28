package io.github.vijteam.didgeridoo.workflows.internal.repository;

import io.github.vijteam.didgeridoo.workflows.internal.service.SpaceCreationRequests;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcSpaceCreationRequests implements SpaceCreationRequests {
    private final JdbcTemplate jdbc;

    public JdbcSpaceCreationRequests(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Reservation reserve(UUID actorId, UUID requestId, String name) {
        jdbc.update(
                "INSERT INTO space_creation_requests(actor_id, request_id, name) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                actorId,
                requestId,
                name);
        return jdbc.queryForObject(
                "SELECT name, space_id FROM space_creation_requests WHERE actor_id=? AND request_id=? FOR UPDATE",
                (row, index) ->
                        new Reservation(
                                row.getString("name"), row.getObject("space_id", UUID.class)),
                actorId,
                requestId);
    }

    @Override
    public void complete(UUID actorId, UUID requestId, UUID spaceId) {
        jdbc.update(
                "UPDATE space_creation_requests SET space_id=? WHERE actor_id=? AND request_id=?",
                spaceId,
                actorId,
                requestId);
    }
}
