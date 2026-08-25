-- V8: incident attachments (Phase 3) - content stored in the database, hard-capped at 5 MiB
CREATE TABLE attachments (
    id                  UUID PRIMARY KEY,
    organization_id     UUID        NOT NULL REFERENCES organizations (id),
    incident_id         UUID        NOT NULL,
    filename            TEXT        NOT NULL,
    content_type        TEXT        NOT NULL,
    size_bytes          BIGINT      NOT NULL,
    content             BYTEA       NOT NULL,
    uploaded_by_user_id UUID        NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_attachments_incident
        FOREIGN KEY (organization_id, incident_id) REFERENCES incidents (organization_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_attachments_size CHECK (size_bytes BETWEEN 1 AND 5242880)
);

CREATE INDEX ix_attachments_incident ON attachments (incident_id, created_at DESC);
