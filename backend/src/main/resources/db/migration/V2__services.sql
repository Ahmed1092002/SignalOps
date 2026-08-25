-- V2: service catalog with composite (organization_id, id) uniqueness for tenant-safe FKs
CREATE TABLE services (
    id             UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    name           TEXT        NOT NULL,
    slug           TEXT        NOT NULL,
    description    TEXT        NOT NULL DEFAULT '',
    owning_team    TEXT        NOT NULL DEFAULT '',
    is_public      BOOLEAN     NOT NULL DEFAULT TRUE,
    archived_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_services_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT ux_services_org_id UNIQUE (organization_id, id)
);

CREATE INDEX ix_services_org_active ON services (organization_id) WHERE archived_at IS NULL;
