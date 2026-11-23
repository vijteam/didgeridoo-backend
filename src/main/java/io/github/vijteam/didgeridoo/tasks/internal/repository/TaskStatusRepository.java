package io.github.vijteam.didgeridoo.tasks.internal.repository;

import io.github.vijteam.didgeridoo.tasks.api.StatusTaskCount;
import io.github.vijteam.didgeridoo.tasks.internal.model.StatusKind;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskStatusRepository extends JpaRepository<TaskStatus, UUID> {
    Optional<TaskStatus> findFirstBySpaceIdOrderByPositionAsc(UUID spaceId);

    Optional<TaskStatus> findByIdAndSpaceId(UUID id, UUID spaceId);

    Optional<TaskStatus> findFirstBySpaceIdAndKindOrderByPositionAsc(UUID spaceId, StatusKind kind);

    List<TaskStatus> findAllBySpaceIdOrderByPositionAsc(UUID spaceId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from TaskStatus s where s.spaceId = :spaceId and s.kind = INITIAL")
    Optional<TaskStatus> lockInitialForRead(UUID spaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TaskStatus s where s.spaceId = :spaceId and s.kind = INITIAL")
    Optional<TaskStatus> lockInitialForWrite(UUID spaceId);

    @Modifying
    @Query("delete from TaskStatus s where s.spaceId = :spaceId")
    void deleteSpaceStatuses(UUID spaceId);

    @Query(
            """
            select new io.github.vijteam.didgeridoo.tasks.api.StatusTaskCount(
                s.id, s.name, s.position, count(t.id))
            from TaskStatus s
            left join Task t on t.spaceId = s.spaceId and t.statusId = s.id
                and (:filterAssignee = false or t.assigneeId = :assigneeId)
            where s.spaceId = :spaceId
            group by s.id, s.name, s.position
            order by s.position, s.id
            """)
    List<StatusTaskCount> countTasksByStatus(UUID spaceId, boolean filterAssignee, UUID assigneeId);
}
