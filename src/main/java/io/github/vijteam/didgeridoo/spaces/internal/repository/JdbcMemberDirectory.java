package io.github.vijteam.didgeridoo.spaces.internal.repository;

import io.github.vijteam.didgeridoo.spaces.api.SpacePageWindow;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;
import io.github.vijteam.didgeridoo.spaces.internal.service.MemberDirectoryStore;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcMemberDirectory implements MemberDirectoryStore {
    private final JdbcTemplate jdbc;

    public JdbcMemberDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page list(UUID spaceId, SpacePageWindow window) {
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_memberships WHERE space_id=?",
                        Long.class,
                        spaceId);
        var entries =
                jdbc.query(
                        "SELECT user_id, role, joined_at FROM space_memberships WHERE space_id=? ORDER BY joined_at, user_id LIMIT ? OFFSET ?",
                        (row, index) ->
                                new Entry(
                                        row.getObject("user_id", UUID.class),
                                        SpaceRole.valueOf(row.getString("role")),
                                        row.getTimestamp("joined_at").toInstant()),
                        spaceId,
                        window.size(),
                        window.offset());
        return new Page(entries, total);
    }
}
