-- V9: scheduled maintenance windows (Phase 3) - override derived status to MAINTENANCE while active
CREATE TABLE maintenance_windows (
    id                 UUID PRIMARY KEY,
    organization_id    UUID        NOT NULL REFERENCES organizations (id),
    service_id         UUID        NOT NULL,
    title              TEXT        NOT NULL,
    starts_at          TIMESTAMPTZ NOT NULL,
    ends_at            TIMESTAMPTZ NOT NULL,
    created_by_user_id UUID        NOT NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_maintenance_windows_service
        FOREIGN KEY (organization_id, service_id) REFERENCES services (organization_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_maintenance_window_period CHECK (ends_at > starts_at)
);

CREATE INDEX ix_maintenance_windows_service ON maintenance_windows (service_id, starts_at, ends_at);
