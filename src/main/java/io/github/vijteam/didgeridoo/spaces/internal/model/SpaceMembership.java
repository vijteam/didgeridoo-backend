package io.github.vijteam.didgeridoo.spaces.internal.model;

import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "space_memberships")
public class SpaceMembership {

    @EmbeddedId private SpaceMembershipId id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "space_role")
    private SpaceRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected SpaceMembership() {}

    public SpaceMembership(UUID spaceId, UUID userId, SpaceRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Space role is required");
        }

        this.id = new SpaceMembershipId(spaceId, userId);
        this.role = role;
    }

    public SpaceMembershipId getId() {
        return id;
    }

    public SpaceRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
