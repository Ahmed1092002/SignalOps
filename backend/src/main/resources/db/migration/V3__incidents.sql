-- V3: incidents, affected services, responders
CREATE TABLE incidents (
    id                 UUID PRIMARY KEY,
    organization_id    UUID        NOT NULL REFERENCES organizations (id),
    key                TEXT        NOT NULL,
    title              TEXT        NOT NULL,
    description        TEXT        NOT NULL DEFAULT '',
    severity           TEXT        NOT NULL,
    status             TEXT        NOT NULL,
    commander_user_id  UUID REFERENCES users (id),
    resolver_user_id   UUID REFERENCES users (id),
    resolution_summary TEXT,
    acknowledged_at    TIMESTAMPTZ,
    resolved_at        TIMESTAMPTZ,
    duration_seconds   BIGINT,
    archived_at        TIMESTAMPTZ,
    created_by_user_id UUID        NOT NULL REFERENCES users (id),
    version            BIGINT      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_incidents_org_id UNIQUE (organization_id, id),
    CONSTRAINT ux_incidents_org_key UNIQUE (organization_id, key),
    CONSTRAINT ck_incidents_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_incidents_status CHECK (
        status IN ('DETECTED', 'INVESTIGATING', 'IDENTIFIED', 'MONITORING', 'RESOLVED')
    ),
    CONSTRAINT ck_incidents_title_length CHECK (char_length(title) BETWEEN 3 AND 200)
);

CREATE INDEX ix_incidents_org_created ON incidents (organization_id, created_at DESC);
CREATE INDEX ix_incidents_org_active_status ON incidents (organization_id, status) WHERE archived_at IS NULL;
CREATE INDEX ix_incidents_org_active_severity ON incidents (organization_id, severity) WHERE archived_at IS NULL;
CREATE INDEX ix_incidents_commander ON incidents (commander_user_id);
CREATE INDEX ix_incidents_org_resolved ON incidents (organization_id, resolved_at) WHERE resolved_at IS NOT NULL;

CREATE TABLE incident_services (
    incident_id     UUID NOT NULL,
    service_id      UUID NOT NULL,
    organization_id UUID NOT NULL,
    PRIMARY KEY (incident_id, service_id),
    CONSTRAINT fk_incident_services_incident
        FOREIGN KEY (organization_id, incident_id) REFERENCES incidents (organization_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_services_service
        FOREIGN KEY (organization_id, service_id) REFERENCES services (organization_id, id)
);

CREATE INDEX ix_incident_services_service ON incident_services (organization_id, service_id);

CREATE TABLE incident_responders (
    incident_id       UUID        NOT NULL,
    user_id           UUID        NOT NULL,
    organization_id   UUID        NOT NULL,
    assigned_by_user_id UUID      NOT NULL REFERENCES users (id),
    assigned_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (incident_id, user_id),
    CONSTRAINT fk_incident_responders_incident
        FOREIGN KEY (organization_id, incident_id) REFERENCES incidents (organization_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_responders_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_incident_responders_user ON incident_responders (user_id);
