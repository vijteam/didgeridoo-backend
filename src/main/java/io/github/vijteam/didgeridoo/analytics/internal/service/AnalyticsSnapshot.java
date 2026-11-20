package io.github.vijteam.didgeridoo.analytics.internal.service;

import io.github.vijteam.didgeridoo.tasks.api.StatusTaskCount;

import java.util.List;
import java.util.UUID;

public record AnalyticsSnapshot(long total, List<Status> statuses) {
    public record Status(UUID statusId, String name, int position, long count) {}

    static AnalyticsSnapshot from(List<StatusTaskCount> counts) {
        long total = 0;
        for (StatusTaskCount status : counts) {
            total = Math.addExact(total, status.taskCount());
        }
        List<Status> statuses =
                counts.stream()
                        .map(
                                status ->
                                        new Status(
                                                status.statusId(),
                                                status.statusName(),
                                                status.position(),
                                                status.taskCount()))
                        .toList();
        return new AnalyticsSnapshot(total, statuses);
    }
}
