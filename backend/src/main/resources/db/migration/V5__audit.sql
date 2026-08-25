-- V5: organization-level append-only audit log
CREATE TABLE audit_events (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    actor_user_id   UUID REFERENCES users (id),
    action          TEXT        NOT NULL,
    entity_type     TEXT        NOT NULL,
    entity_id       UUID,
    metadata        JSONB       NOT NULL DEFAULT '{}',
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_events_action CHECK (
        action IN (
            'USER_INVITED', 'MEMBER_ROLE_CHANGED', 'MEMBER_DISABLED', 'MEMBER_REMOVED',
            'SERVICE_CREATED', 'SERVICE_UPDATED', 'SERVICE_ARCHIVED', 'ORG_SETTINGS_UPDATED',
            'INCIDENT_EDITED', 'PASSWORD_CHANGED', 'AUTH_LOGIN_FAILED'
        )
    )
);

CREATE INDEX ix_audit_events_org_occurred ON audit_events (organization_id, occurred_at DESC);
CREATE INDEX ix_audit_events_org_action ON audit_events (organization_id, action);
