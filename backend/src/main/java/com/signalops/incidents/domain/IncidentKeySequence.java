package com.signalops.incidents.domain;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL-atomic organization-scoped sequence generator for Incident keys (INC-1, INC-2, etc.).
 * Uses an organization-scoped counter row with row-level locking via INSERT ... ON CONFLICT.
 */
@Repository
@RequiredArgsConstructor
public class IncidentKeySequence {

  private final JdbcTemplate jdbcTemplate;

  public int nextNumber(UUID organizationId) {
    String sql = """
        INSERT INTO incident_counters (organization_id, last_number)
        VALUES (?, (SELECT COALESCE(MAX(CAST(substring(key FROM 5) AS INT)), 0) + 1 FROM incidents WHERE organization_id = ?))
        ON CONFLICT (organization_id)
        DO UPDATE SET last_number = GREATEST(
            incident_counters.last_number + 1,
            COALESCE((SELECT MAX(CAST(substring(key FROM 5) AS INT)) FROM incidents WHERE organization_id = EXCLUDED.organization_id), 0) + 1
        )
        RETURNING last_number
        """;
    Integer val = jdbcTemplate.queryForObject(sql, Integer.class, organizationId, organizationId);
    return val != null ? val : 1;
  }
}
