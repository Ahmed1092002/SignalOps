-- V7: follow-up tasks (Phase 3) + timeline types for tasks and attachments
CREATE TABLE follow_up_tasks (
    id                 UUID PRIMARY KEY,
    organization_id    UUID        NOT NULL REFERENCES organizations (id),
    incident_id        UUID        NOT NULL,
    title              TEXT        NOT NULL,
    status             TEXT        NOT NULL DEFAULT 'OPEN',
    assignee_user_id   UUID REFERENCES users (id),
    created_by_user_id UUID        NOT NULL REFERENCES users (id),
    due_at             TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_follow_up_tasks_incident
        FOREIGN KEY (organization_id, incident_id) REFERENCES incidents (organization_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_follow_up_tasks_title CHECK (char_length(title) BETWEEN 1 AND 200),
    CONSTRAINT ck_follow_up_tasks_status CHECK (status IN ('OPEN', 'DONE'))
);

CREATE INDEX ix_follow_up_tasks_incident ON follow_up_tasks (incident_id, created_at DESC);
CREATE INDEX ix_follow_up_tasks_assignee_open ON follow_up_tasks (assignee_user_id) WHERE status = 'OPEN';

ALTER TABLE timeline_events DROP CONSTRAINT ck_timeline_events_type;
ALTER TABLE timeline_events ADD CONSTRAINT ck_timeline_events_type CHECK (
    type IN (
        'INCIDENT_CREATED', 'STATUS_CHANGED', 'SEVERITY_CHANGED', 'COMMANDER_ASSIGNED',
        'COMMANDER_REMOVED', 'RESPONDER_ASSIGNED', 'RESPONDER_REMOVED', 'COMMENT_ADDED',
        'PUBLIC_UPDATE_PUBLISHED', 'INCIDENT_RESOLVED', 'INCIDENT_REOPENED', 'INCIDENT_ARCHIVED',
        'DETECTION_SOURCE', 'FOLLOW_UP_CREATED', 'FOLLOW_UP_COMPLETED', 'ATTACHMENT_ADDED'
    )
);
