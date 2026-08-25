package com.signalops.servicecatalog.api;

import com.signalops.shared.domain.Role;
import java.time.Instant;
import java.util.UUID;

public final class ServiceCatalogDtos {

  public record ServiceView(
      UUID id,
      String name,
      String slug,
      String description,
      String owningTeam,
      boolean isPublic,
      Instant archivedAt,
      Instant createdAt) {}

  public record CreateServiceRequest(
      @jakarta.validation.constraints.NotBlank
          @jakarta.validation.constraints.Size(min = 2, max = 80)
          String name,
      @jakarta.validation.constraints.Size(max = 2000) String description,
      @jakarta.validation.constraints.Size(max = 80) String owningTeam,
      Boolean isPublic) {}

  public record UpdateServiceRequest(
      @jakarta.validation.constraints.Size(min = 2, max = 80) String name,
      @jakarta.validation.constraints.Size(max = 2000) String description,
      @jakarta.validation.constraints.Size(max = 80) String owningTeam,
      Boolean isPublic) {}

  private ServiceCatalogDtos() {}
}
