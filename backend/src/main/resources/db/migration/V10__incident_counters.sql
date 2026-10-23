-- V10: organization-scoped counter for atomic incident key generation
CREATE TABLE incident_counters (
    organization_id UUID PRIMARY KEY REFERENCES organizations (id) ON DELETE CASCADE,
    last_number     INT  NOT NULL DEFAULT 0
);

INSERT INTO incident_counters (organization_id, last_number)
SELECT o.id, COALESCE((SELECT MAX(CAST(substring(i.key FROM 5) AS INT)) FROM incidents i WHERE i.organization_id = o.id), 0)
FROM organizations o
ON CONFLICT (organization_id) DO NOTHING;
