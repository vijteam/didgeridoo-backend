CREATE TABLE space_departure_requests (
    actor_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_id uuid NOT NULL,
    space_id uuid NOT NULL,
    requested_successor uuid,
    requested_deletion boolean NOT NULL,
    outcome varchar(16) NOT NULL CHECK (outcome IN ('LEFT', 'TRANSFERRED', 'SPACE_DELETED')),
    PRIMARY KEY (actor_id, request_id)
);
