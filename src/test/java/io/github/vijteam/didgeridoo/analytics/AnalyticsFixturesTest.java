package io.github.vijteam.didgeridoo.analytics;

import static org.junit.jupiter.api.Assertions.*;

import io.github.vijteam.didgeridoo.analytics.api.TaskStatisticsSource;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class AnalyticsFixturesTest {
    @Test
    void currentCountsRespectSpaceAssignmentFinalAndDeadlineBoundary() throws Exception {
        try (var input = getClass().getResourceAsStream("/analytics/s1-current.json")) {
            assertNotNull(input);
            JsonNode root = JsonMapper.builder().build().readTree(input);
            Instant asOf = Instant.parse(root.get("asOf").asString());
            for (JsonNode expected : root.get("expected")) {
                String space = expected.get("spaceId").asString();
                String assignee =
                        expected.get("assigneeId").isNull()
                                ? null
                                : expected.get("assigneeId").asString();
                List<JsonNode> selected = new ArrayList<>();
                for (JsonNode task : root.get("tasks")) {
                    if (task.get("spaceId").asString().equals(space)
                            && (assignee == null
                                    || assignee.equals(task.get("assigneeId").asString()))) {
                        selected.add(task);
                    }
                }
                long overdue = 0;
                List<TaskStatisticsSource.StatusCount> counts = new ArrayList<>();
                for (JsonNode status : root.get("statuses")) {
                    if (!status.get("spaceId").asString().equals(space)) {
                        continue;
                    }
                    long count = 0;
                    for (JsonNode task : selected) {
                        if (!task.get("statusId").asString().equals(status.get("id").asString())) {
                            continue;
                        }
                        count++;
                        if (!status.get("terminal").asBoolean()
                                && !task.get("deadline").isNull()
                                && Instant.parse(task.get("deadline").asString()).isBefore(asOf)) {
                            overdue++;
                        }
                    }
                    counts.add(
                            new TaskStatisticsSource.StatusCount(
                                    UUID.fromString(status.get("id").asString()),
                                    status.get("name").asString(),
                                    status.get("position").asInt(),
                                    status.get("terminal").asBoolean(),
                                    count));
                }
                var snapshot =
                        new TaskStatisticsSource.Snapshot(
                                UUID.fromString(space),
                                assignee == null ? null : UUID.fromString(assignee),
                                asOf,
                                selected.size(),
                                overdue,
                                counts);
                assertEquals(expected.get("total").asLong(), snapshot.total());
                assertEquals(expected.get("overdue").asLong(), snapshot.overdue());
                assertEquals(expected.get("counts").size(), snapshot.statuses().size());
                for (int i = 0; i < counts.size(); i++) {
                    assertEquals(
                            expected.get("counts").get(i).asLong(),
                            snapshot.statuses().get(i).count());
                }
            }
        }
    }
}
