-- V4: collaboration and history - internal comments, public updates, timeline
CREATE TABLE incident_comments (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    incident_id     UUID        NOT NULL,
    author_user_id  UUID        NOT NULL REFERENCES users (id),
    body            TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_incident_comments_incident
        FOREIGN KEY (organization_id, incident_id) REFERENCES incidents (organization_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_incident_comments_body_length CHECK (char_length(body) BETWEEN 1 AND 10000)
);

CREATE INDEX ix_incident_comments_incident ON incident_comments (incident_id, created_at DESC);

CREATE TABLE incident_public_updates (
    id                  UUID PRIMARY KEY,
    organization_id     UUID        NOT NULL REFERENCES organizations (id),
    incident_id         UUID        NOT NULL,
    author_user_id      UUID        NOT NULL REFERENCES users (id),
    message             TEXT        NOT NULL,
    status_at_publish   TEXT        NOT NULL,
    severity_at_publish TEXT        NOT NULL,
    published_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_incident_public_updates_incident
        FOREIGN KEY (organization_id, incident_id) REFERENCES incidents (organization_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_incident_public_updates_status CHECK (
        status_at_publish IN ('DETECTED', 'INVESTIGATING', 'IDENTIFIED', 'MONITORING', 'RESOLVED')
    ),
    CONSTRAINT ck_incident_public_updates_severity CHECK (
        severity_at_publish IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    CONSTRAINT ck_incident_public_updates_message_length CHECK (char_length(message) BETWEEN 1 AND 10000)
);

CREATE INDEX ix_incident_public_updates_incident ON incident_public_updates (incident_id, published_at DESC);

CREATE TABLE timeline_events (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    incident_id     UUID        NOT NULL,
    type            TEXT        NOT NULL,
    actor_user_id   UUID REFERENCES users (id),
    payload         JSONB       NOT NULL DEFAULT '{}',
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_timeline_events_incident
        FOREIGN KEY (organization_id, incident_id) REFERENCES incidents (organization_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_timeline_events_type CHECK (
        type IN (
            'INCIDENT_CREATED', 'STATUS_CHANGED', 'SEVERITY_CHANGED', 'COMMANDER_ASSIGNED',
            'COMMANDER_REMOVED', 'RESPONDER_ASSIGNED', 'RESPONDER_REMOVED', 'COMMENT_ADDED',
            'PUBLIC_UPDATE_PUBLISHED', 'INCIDENT_RESOLVED', 'INCIDENT_REOPENED', 'INCIDENT_ARCHIVED',
            'DETECTION_SOURCE'
        )
    )
);

CREATE INDEX ix_timeline_events_incident ON timeline_events (incident_id, occurred_at DESC);
