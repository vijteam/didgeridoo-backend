CREATE TABLE space_creation_requests (
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    space_id UUID REFERENCES spaces(id) ON DELETE CASCADE,
    PRIMARY KEY (actor_id, request_id)
);
