package io.github.vijteam.didgeridoo.analytics.internal.service;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceAccess;
import io.github.vijteam.didgeridoo.spaces.api.SpaceNotFoundException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;
import io.github.vijteam.didgeridoo.tasks.api.TaskQueries;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SpaceAnalytics {
    private final SpaceAccess access;
    private final TaskQueries tasks;

    public SpaceAnalytics(SpaceAccess access, TaskQueries tasks) {
        this.access = access;
        this.tasks = tasks;
    }

    public AnalyticsSnapshot forSpace(UUID actorId, UUID spaceId) {
        access.requireMember(spaceId, actorId);
        return AnalyticsSnapshot.from(tasks.countByStatus(spaceId));
    }

    public AnalyticsSnapshot forMember(UUID actorId, UUID spaceId, UUID userId) {
        SpaceRole role = access.findRole(spaceId, actorId).orElseThrow(SpaceNotFoundException::new);
        boolean ownAnalytics = actorId.equals(userId);
        if (role == SpaceRole.GUEST || (role == SpaceRole.MEMBER && !ownAnalytics)) {
            throw new AnalyticsAccessDeniedException();
        }
        if (!ownAnalytics && !access.isAssignable(spaceId, userId)) {
            throw new SpaceNotFoundException();
        }
        return AnalyticsSnapshot.from(tasks.countByStatusForAssignee(spaceId, userId));
    }

    private static class AnalyticsAccessDeniedException extends ApplicationException {
        AnalyticsAccessDeniedException() {
            super(ApiErrorCode.FORBIDDEN, "Access denied");
        }
    }
}
