-- Only tasks-owned tables are created here.
-- Space IDs are opaque until the spaces module provides its tables.
-- Add space/membership foreign keys in a later integration migration.
CREATE TABLE task_statuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL CHECK (length(btrim(name)) > 0),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('INITIAL', 'INTERMEDIATE', 'FINAL')),
    position INTEGER NOT NULL CHECK (position >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (space_id, id),
    UNIQUE (space_id, position)
);
CREATE UNIQUE INDEX uq_initial_status_per_space ON task_statuses(space_id) WHERE kind = 'INITIAL';
CREATE UNIQUE INDEX uq_final_status_per_space ON task_statuses(space_id) WHERE kind = 'FINAL';

CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id UUID NOT NULL,
    creator_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    assignee_user_id UUID REFERENCES users(id),
    status_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL CHECK (length(btrim(title)) > 0),
    description TEXT,
    deadline_at TIMESTAMPTZ,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM'
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    assignee_can_edit BOOLEAN NOT NULL DEFAULT false,
    assignment_source VARCHAR(16) CHECK (assignment_source IN ('DIRECT', 'SELF_CLAIM')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (space_id, id),
    FOREIGN KEY (space_id, status_id) REFERENCES task_statuses(space_id, id),
    CONSTRAINT task_assignment_consistency CHECK (
        (assignee_user_id IS NULL AND assignment_source IS NULL) OR
        (assignee_user_id IS NOT NULL AND assignment_source IS NOT NULL)
    )
);
CREATE INDEX ix_tasks_space_status ON tasks(space_id, status_id);
CREATE INDEX ix_tasks_space_assignee ON tasks(space_id, assignee_user_id);
CREATE INDEX ix_tasks_space_deadline ON tasks(space_id, deadline_at);
CREATE INDEX ix_tasks_space_created ON tasks(space_id, created_at);

CREATE TABLE task_changes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('CREATED', 'UPDATED')),
    changed_fields JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_changes_task_created ON task_changes(task_id, created_at);
