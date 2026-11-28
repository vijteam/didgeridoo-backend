package io.github.vijteam.didgeridoo.workflows.internal.repository;

import io.github.vijteam.didgeridoo.workflows.internal.service.SpaceDepartureRequests;
import io.github.vijteam.didgeridoo.workflows.internal.service.SpaceMembershipWorkflow.Departure;
import io.github.vijteam.didgeridoo.workflows.internal.service.SpaceMembershipWorkflow.DepartureOutcome;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcSpaceDepartureRequests implements SpaceDepartureRequests {
    private final JdbcTemplate jdbc;

    public JdbcSpaceDepartureRequests(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lock(UUID actor, UUID requestId) {
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                row -> null,
                "space-departure:" + actor + ":" + requestId);
    }

    @Override
    public Optional<Recorded> find(UUID actor, UUID requestId) {
        return jdbc
                .query(
                        """
                        SELECT space_id, requested_successor, requested_deletion, outcome
                        FROM space_departure_requests WHERE actor_id = ? AND request_id = ?
                        """,
                        (row, index) -> {
                            UUID successor = row.getObject("requested_successor", UUID.class);
                            return new Recorded(
                                    successor,
                                    row.getBoolean("requested_deletion"),
                                    new Departure(
                                            row.getObject("space_id", UUID.class),
                                            DepartureOutcome.valueOf(row.getString("outcome")),
                                            successor));
                        },
                        actor,
                        requestId)
                .stream()
                .findFirst();
    }

    @Override
    public void save(
            UUID actor, UUID requestId, UUID successor, boolean deleteSpace, Departure result) {
        jdbc.update(
                """
                INSERT INTO space_departure_requests
                    (actor_id, request_id, space_id, requested_successor, requested_deletion, outcome)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                actor,
                requestId,
                result.spaceId(),
                successor,
                deleteSpace,
                result.outcome().name());
    }
}
