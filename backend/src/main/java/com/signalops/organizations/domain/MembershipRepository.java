package com.signalops.organizations.domain;

import com.signalops.shared.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

  List<Membership> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<Membership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

  List<Membership> findByOrganizationId(UUID organizationId);

  @Query(
      "select (count(m) > 0) from Membership m where m.organizationId = :organizationId"
          + " and m.role = :role and m.status = 'ACTIVE'")
  boolean existsActiveWithRole(
      @Param("organizationId") UUID organizationId, @Param("role") Role role);

  @Query(
      "select count(m) from Membership m where m.organizationId = :organizationId"
          + " and m.role = 'OWNER' and m.status = 'ACTIVE'")
  long countActiveOwners(@Param("organizationId") UUID organizationId);
}
