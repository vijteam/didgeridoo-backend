package io.github.vijteam.didgeridoo.spaces.api;

public class SpaceAccessDeniedException extends RuntimeException {
    public SpaceAccessDeniedException() {
        super("Space owner role is required");
    }
}
