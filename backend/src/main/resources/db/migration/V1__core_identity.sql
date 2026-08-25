-- V1: core identity - users, organizations, memberships, invitations, refresh token sessions
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    full_name     TEXT        NOT NULL,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

CREATE TABLE organizations (
    id         UUID PRIMARY KEY,
    name       TEXT        NOT NULL,
    slug       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_organizations_slug UNIQUE (slug),
    CONSTRAINT ck_organizations_slug_format
        CHECK (slug ~ '^[a-z0-9](?:[a-z0-9-]{0,38}[a-z0-9])?$')
);

CREATE TABLE memberships (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    user_id         UUID        NOT NULL REFERENCES users (id),
    role            TEXT        NOT NULL,
    status          TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_memberships_org_user UNIQUE (organization_id, user_id),
    CONSTRAINT ck_memberships_role CHECK (
        role IN ('OWNER', 'ADMIN', 'INCIDENT_COMMANDER', 'RESPONDER', 'VIEWER')
    ),
    CONSTRAINT ck_memberships_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_memberships_user ON memberships (user_id);
CREATE INDEX ix_memberships_org_status ON memberships (organization_id, status);

CREATE TABLE invitations (
    id                UUID PRIMARY KEY,
    organization_id   UUID        NOT NULL REFERENCES organizations (id),
    email             TEXT        NOT NULL,
    role              TEXT        NOT NULL,
    token_hash        TEXT        NOT NULL,
    invited_by_user_id UUID       NOT NULL REFERENCES users (id),
    expires_at        TIMESTAMPTZ NOT NULL,
    accepted_at       TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_invitations_role CHECK (
        role IN ('ADMIN', 'INCIDENT_COMMANDER', 'RESPONDER', 'VIEWER')
    )
);

CREATE INDEX ix_invitations_org_accepted ON invitations (organization_id, accepted_at);
CREATE INDEX ix_invitations_email_lower ON invitations (lower(email));

CREATE TABLE refresh_token_sessions (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users (id),
    family_id       UUID        NOT NULL,
    token_hash      TEXT        NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by_id  UUID REFERENCES refresh_token_sessions (id),
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_refresh_token_sessions_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_token_sessions_family ON refresh_token_sessions (family_id);
CREATE INDEX ix_refresh_token_sessions_user ON refresh_token_sessions (user_id);
