package io.github.vijteam.didgeridoo.spaces.api;

/** Bounded offset pagination supported by all spaces routes. */
public record SpacePageWindow(int page, int size) {
    public SpacePageWindow {
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100");
        }
        if (page < 0 || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid page offset");
        }
    }

    public long offset() {
        return (long) page * size;
    }
}
