-- V6: in-app notifications (Phase 2 / M10)
CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    user_id         UUID        NOT NULL REFERENCES users (id),
    type            TEXT        NOT NULL,
    title           TEXT        NOT NULL,
    body            TEXT        NOT NULL DEFAULT '',
    entity_type     TEXT,
    entity_id       UUID,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notifications_type CHECK (
        type IN ('RESPONDER_ASSIGNED', 'SEVERITY_CRITICAL', 'PUBLIC_UPDATE_PUBLISHED')
    )
);

CREATE INDEX ix_notifications_user_recent ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;
