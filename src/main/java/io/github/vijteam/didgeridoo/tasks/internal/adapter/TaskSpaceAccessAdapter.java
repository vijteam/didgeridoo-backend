package io.github.vijteam.didgeridoo.tasks.internal.adapter;

import io.github.vijteam.didgeridoo.spaces.api.SpaceAccess;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskSpaceAccess;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Tasks consumes only the public spaces API; membership remains owned by spaces. */
@Component
public class TaskSpaceAccessAdapter implements TaskSpaceAccess {
    private final SpaceAccess spaces;

    public TaskSpaceAccessAdapter(SpaceAccess spaces) {
        this.spaces = spaces;
    }

    @Override
    public Role roleOf(UUID spaceId, UUID userId) {
        return spaces.findRole(spaceId, userId)
                .map(
                        role ->
                                switch (role) {
                                    case OWNER -> Role.OWNER;
                                    case MEMBER -> Role.MEMBER;
                                    case GUEST -> Role.GUEST;
                                })
                .orElse(null);
    }
}
