package io.github.vijteam.didgeridoo.tasks.internal.dto;

import java.util.List;

public record TaskPageResponse<T>(List<T> items, int page, int size, long totalElements) {
    public TaskPageResponse {
        items = List.copyOf(items);
    }
}
