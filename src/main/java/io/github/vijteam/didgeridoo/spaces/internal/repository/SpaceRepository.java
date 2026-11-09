package io.github.vijteam.didgeridoo.spaces.internal.repository;

import io.github.vijteam.didgeridoo.spaces.internal.model.Space;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SpaceRepository extends JpaRepository<Space, UUID> {

    @Query(
            value =
                    """
                    SELECT s.* FROM spaces s
                    WHERE EXISTS (
                        SELECT 1 FROM space_memberships m
                        WHERE m.space_id = s.id AND m.user_id = :userId
                    )
                    AND strpos(lower(s.name), lower(:name)) > 0
                    """,
            countQuery =
                    """
                    SELECT count(*) FROM spaces s
                    WHERE EXISTS (
                        SELECT 1 FROM space_memberships m
                        WHERE m.space_id = s.id AND m.user_id = :userId
                    )
                    AND strpos(lower(s.name), lower(:name)) > 0
                    """,
            nativeQuery = true)
    Page<Space> findByMemberUserIdAndNameContainingIgnoreCase(
            @Param("userId") UUID userId, @Param("name") String name, Pageable pageable);
}
