package io.github.vijteam.didgeridoo.analytics.internal.service;

import io.github.vijteam.didgeridoo.tasks.api.StatusTaskCount;
import io.github.vijteam.didgeridoo.tasks.api.TaskMetrics;

import java.util.List;
import java.util.UUID;

public record AnalyticsSnapshot(long total, long overdue, List<Status> statuses) {
    public record Status(UUID statusId, String name, int position, long count) {}

    static AnalyticsSnapshot from(TaskMetrics metrics, List<StatusTaskCount> counts) {
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
        return new AnalyticsSnapshot(metrics.total(), metrics.overdue(), statuses);
    }
}
