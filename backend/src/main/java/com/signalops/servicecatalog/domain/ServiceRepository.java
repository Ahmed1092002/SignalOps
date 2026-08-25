package com.signalops.servicecatalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRepository extends JpaRepository<ServiceEntity, UUID> {

  @Query("select s from ServiceEntity s where s.id = :id and s.organizationId = :organizationId")
  Optional<ServiceEntity> findByIdAndOrganizationId(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  @Query(
      "select s from ServiceEntity s where s.organizationId = :organizationId"
          + " and (:includeArchived = true or s.archivedAt is null)"
          + " order by s.name asc")
  List<ServiceEntity> findAllInOrganization(
      @Param("organizationId") UUID organizationId,
      @Param("includeArchived") boolean includeArchived);

  @Query(
      "select (count(s) > 0) from ServiceEntity s where s.organizationId = :organizationId"
          + " and s.slug = :slug")
  boolean existsBySlug(@Param("organizationId") UUID organizationId, @Param("slug") String slug);

  @Query(
      "select (count(s) > 0) from ServiceEntity s where s.organizationId = :organizationId"
          + " and s.id in :ids")
  boolean allExistInOrganization(
      @Param("organizationId") UUID organizationId, @Param("ids") List<UUID> ids);
}
