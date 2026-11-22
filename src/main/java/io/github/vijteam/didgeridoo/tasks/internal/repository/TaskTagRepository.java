package io.github.vijteam.didgeridoo.tasks.internal.repository;

import io.github.vijteam.didgeridoo.tasks.internal.model.SpaceTag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class TaskTagRepository {
    private final JdbcClient queries;
    private final JdbcTemplate jdbc;

    TaskTagRepository(JdbcClient queries, JdbcTemplate jdbc) {
        this.queries = queries;
        this.jdbc = jdbc;
    }

    public List<SpaceTag> list(UUID spaceId) {
        return queries.sql(
                        "select id, name from space_tags where space_id = :space order by name, id")
                .param("space", spaceId)
                .query(SpaceTag.class)
                .list();
    }

    public Optional<SpaceTag> find(UUID spaceId, UUID tagId) {
        return queries.sql("select id, name from space_tags where space_id = :space and id = :tag")
                .param("space", spaceId)
                .param("tag", tagId)
                .query(SpaceTag.class)
                .optional();
    }

    public boolean nameTaken(UUID spaceId, String name) {
        return queries.sql(
                        "select exists (select 1 from space_tags where space_id = :space and name = :name)")
                .param("space", spaceId)
                .param("name", name)
                .query(Boolean.class)
                .single();
    }

    public void insert(UUID spaceId, SpaceTag tag) {
        jdbc.update(
                "insert into space_tags (space_id, id, name) values (?, ?, ?)",
                spaceId,
                tag.id(),
                tag.name());
    }

    public void rename(UUID spaceId, SpaceTag tag) {
        jdbc.update(
                "update space_tags set name = ? where space_id = ? and id = ?",
                tag.name(),
                spaceId,
                tag.id());
    }

    public void delete(UUID spaceId, UUID tagId) {
        jdbc.update("delete from space_tags where space_id = ? and id = ?", spaceId, tagId);
    }

    public void deleteSpaceTags(UUID spaceId) {
        jdbc.update("delete from space_tags where space_id = ?", spaceId);
    }

    public long countTasks(UUID spaceId, UUID tagId) {
        return queries.sql(
                        "select count(*) from task_tags where space_id = :space and tag_id = :tag")
                .param("space", spaceId)
                .param("tag", tagId)
                .query(Long.class)
                .single();
    }

    public List<SpaceTag> byIds(UUID spaceId, Set<UUID> tagIds) {
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return queries.sql(
                        "select id, name from space_tags where space_id = :space and id in (:ids) order by name, id")
                .param("space", spaceId)
                .param("ids", tagIds)
                .query(SpaceTag.class)
                .list();
    }

    public List<SpaceTag> forTask(UUID spaceId, UUID taskId) {
        return queries.sql(
                        """
                select s.id, s.name from task_tags t
                join space_tags s on s.space_id = t.space_id and s.id = t.tag_id
                where t.space_id = :space and t.task_id = :task
                order by s.name, s.id
                """)
                .param("space", spaceId)
                .param("task", taskId)
                .query(SpaceTag.class)
                .list();
    }

    public void replace(UUID spaceId, UUID taskId, List<SpaceTag> tags) {
        jdbc.update("delete from task_tags where space_id = ? and task_id = ?", spaceId, taskId);
        insertLinks(spaceId, taskId, tags);
    }

    public void insertLinks(UUID spaceId, UUID taskId, List<SpaceTag> tags) {
        if (!tags.isEmpty()) {
            jdbc.batchUpdate(
                    "insert into task_tags (space_id, task_id, tag_id) values (?, ?, ?)",
                    tags.stream().map(tag -> new Object[] {spaceId, taskId, tag.id()}).toList());
        }
    }
}
