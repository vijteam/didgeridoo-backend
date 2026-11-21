package io.github.vijteam.didgeridoo.tasks.internal.repository;

import io.github.vijteam.didgeridoo.tasks.internal.model.Task;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id and t.spaceId = :spaceId")
    Optional<Task> findForUpdateByIdAndSpaceId(UUID id, UUID spaceId);

    Page<Task> findAllBySpaceId(UUID spaceId, Pageable pageable);

    String SEARCH_FILTERS =
            """
            where t.spaceId = :spaceId
              and (:pattern is null or t.title ilike :pattern escape '!'
                                    or t.description ilike :pattern escape '!')
              and (:assigneeId is null or t.assigneeId = :assigneeId)
              and (:statusId is null or t.statusId = :statusId)
              and (:tagId is null or exists (
                  select 1 from TaskTag x where x.spaceId = t.spaceId
                      and x.taskId = t.id and x.tagId = :tagId))
              and (:hasFrom = false or t.deadline >= :from)
              and (:hasUntil = false or t.deadline < :until)
            """;

    @Query(
            value =
                    """
                    select t from Task t
                    join TaskStatus s on s.spaceId = t.spaceId and s.id = t.statusId
                    """
                            + SEARCH_FILTERS
                            + """
                    order by case when :orderBy = 'STATUS' then s.position end,
                             case when :orderBy = 'DEADLINE' then t.deadline end nulls last,
                             t.createdAt, t.id
                    """,
            countQuery = "select count(t) from Task t " + SEARCH_FILTERS)
    Page<Task> search(
            UUID spaceId,
            String pattern,
            UUID assigneeId,
            UUID statusId,
            UUID tagId,
            boolean hasFrom,
            Instant from,
            boolean hasUntil,
            Instant until,
            String orderBy,
            Pageable pageable);

    Optional<Task> findByIdAndSpaceId(UUID id, UUID spaceId);

    List<Task> findAllBySpaceIdOrderByCreatedAtAscIdAsc(UUID spaceId);

    long countBySpaceIdAndStatusId(UUID spaceId, UUID statusId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Task> findAllBySpaceIdAndStatusIdOrderById(UUID spaceId, UUID statusId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select t from Task t where t.spaceId = :spaceId and exists (
                select 1 from TaskTag x where x.spaceId = :spaceId
                    and x.taskId = t.id and x.tagId = :tagId)
            order by t.id
            """)
    List<Task> lockAllWithTag(UUID spaceId, UUID tagId);

    long countBySpaceIdAndAssigneeId(UUID spaceId, UUID assigneeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Task> findAllBySpaceIdAndAssigneeIdOrderById(UUID spaceId, UUID assigneeId);

    @Modifying
    @Query("delete from Task t where t.spaceId = :spaceId")
    void deleteSpaceTasks(UUID spaceId);
}
