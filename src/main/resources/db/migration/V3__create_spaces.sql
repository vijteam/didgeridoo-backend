CREATE TYPE space_role AS ENUM ('GUEST', 'MEMBER', 'OWNER');

CREATE TABLE spaces (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(150) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE space_memberships (
    space_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role space_role NOT NULL,
    joined_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (space_id, user_id),
    FOREIGN KEY (space_id) REFERENCES spaces(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX space_memberships_space_id_role_idx
    ON space_memberships (space_id, role);

CREATE INDEX space_memberships_user_id_joined_at_idx
    ON space_memberships (user_id, joined_at);
