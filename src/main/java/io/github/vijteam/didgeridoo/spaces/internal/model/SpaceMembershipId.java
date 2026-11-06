package io.github.vijteam.didgeridoo.spaces.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SpaceMembershipId implements Serializable {

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected SpaceMembershipId() {}

    public SpaceMembershipId(UUID spaceId, UUID userId) {
        if (spaceId == null || userId == null) {
            throw new IllegalArgumentException("Space and user identifiers are required");
        }

        this.spaceId = spaceId;
        this.userId = userId;
    }

    public UUID getSpaceId() {
        return spaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof SpaceMembershipId that)) {
            return false;
        }

        return Objects.equals(spaceId, that.spaceId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spaceId, userId);
    }
}
