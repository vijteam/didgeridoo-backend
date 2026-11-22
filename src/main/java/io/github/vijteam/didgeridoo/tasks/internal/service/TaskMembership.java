package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskDependencyException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class TaskMembership {
    private final ObjectProvider<TaskSpaceAccess> access;

    TaskMembership(ObjectProvider<TaskSpaceAccess> access) {
        this.access = access;
    }

    TaskSpaceAccess.Role requireMember(UUID spaceId, UUID actorId) {
        if (spaceId == null || actorId == null) {
            throw new TaskValidationException("spaceId and actorId are required");
        }
        var membership = access.getIfAvailable();
        if (membership == null) {
            throw new TaskDependencyException(
                    TaskDependencyException.Reason.SPACES_UNAVAILABLE,
                    "Spaces access adapter is not configured");
        }
        var role = membership.roleOf(spaceId, actorId);
        if (role == null) {
            throw new TaskAccessException(
                    TaskAccessException.Reason.NOT_MEMBER, "Space membership is required");
        }
        return role;
    }

    void requireOwner(UUID spaceId, UUID actorId) {
        if (requireMember(spaceId, actorId) != TaskSpaceAccess.Role.OWNER) {
            throw new TaskAccessException("Only the owner can manage the task catalog");
        }
    }
}
