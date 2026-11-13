CREATE TABLE space_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id UUID NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    invitee_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    inviter_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    request_id UUID NOT NULL,
    role space_role NOT NULL DEFAULT 'MEMBER',
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (inviter_user_id, request_id)
);
CREATE UNIQUE INDEX uq_pending_space_invitation
    ON space_invitations(space_id, invitee_user_id) WHERE state='PENDING';
CREATE INDEX ix_invitations_space_created ON space_invitations(space_id, created_at, id);
CREATE INDEX ix_invitations_recipient_created ON space_invitations(invitee_user_id, created_at, id);
