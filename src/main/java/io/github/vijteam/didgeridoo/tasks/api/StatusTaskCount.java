package io.github.vijteam.didgeridoo.tasks.api;

import java.util.UUID;

/** Current task count for a status; no persistence entities cross the module boundary. */
public record StatusTaskCount(UUID statusId, String statusName, int position, long taskCount) {}
