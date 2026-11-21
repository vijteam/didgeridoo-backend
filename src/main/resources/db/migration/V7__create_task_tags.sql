CREATE TABLE space_tags (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL CHECK (length(btrim(name)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (space_id, id),
    UNIQUE (space_id, name)
);

CREATE TABLE task_tags (
    space_id UUID NOT NULL,
    task_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    PRIMARY KEY (task_id, tag_id),
    FOREIGN KEY (space_id, task_id) REFERENCES tasks(space_id, id) ON DELETE CASCADE,
    FOREIGN KEY (space_id, tag_id) REFERENCES space_tags(space_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_task_tags_space_tag ON task_tags(space_id, tag_id);
