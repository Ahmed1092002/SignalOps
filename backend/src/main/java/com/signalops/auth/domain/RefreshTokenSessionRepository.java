package com.signalops.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, UUID> {

  Optional<RefreshTokenSession> findByTokenHash(String tokenHash);

  List<RefreshTokenSession> findByFamilyId(UUID familyId);

  @Modifying
  @Query(
      "update RefreshTokenSession s set s.revokedAt = :now"
          + " where s.familyId = :familyId and s.revokedAt is null")
  int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
