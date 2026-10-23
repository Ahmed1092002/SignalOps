package com.signalops.organizations.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

  Optional<Organization> findBySlug(String slug);

  boolean existsBySlug(String slug);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Organization o where o.id = :id")
  Optional<Organization> findByIdWithLock(@Param("id") UUID id);
}

