package io.github.vijteam.didgeridoo.spaces.api;

public class SpaceNotFoundException extends RuntimeException {
    public SpaceNotFoundException() {
        super("Space not found");
    }
}
