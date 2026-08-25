package com.signalops.servicecatalog.app;

import com.signalops.servicecatalog.api.ActiveIncidentLookup;
import com.signalops.servicecatalog.api.ServiceCatalogDtos.CreateServiceRequest;
import com.signalops.servicecatalog.api.ServiceCatalogDtos.ServiceView;
import com.signalops.servicecatalog.api.ServiceCatalogDtos.UpdateServiceRequest;
import com.signalops.servicecatalog.api.ServiceCatalogEvents.ServiceArchived;
import com.signalops.servicecatalog.api.ServiceCatalogEvents.ServiceCreated;
import com.signalops.servicecatalog.api.ServiceCatalogEvents.ServiceUpdated;
import com.signalops.servicecatalog.domain.ServiceEntity;
import com.signalops.servicecatalog.domain.ServiceRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.domain.SlugGenerator;
import com.signalops.shared.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceCatalogService {

  private final ServiceRepository serviceRepository;
  private final CatalogAccessPolicy accessPolicy;
  private final SlugGenerator slugGenerator;
  private final ObjectProvider<ActiveIncidentLookup> activeIncidentLookup;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Transactional
  public ServiceView create(CreateServiceRequest request) {
    var context = TenantContext.require();
    accessPolicy.requireAdminLike(context, "manage services");
    ServiceEntity service = new ServiceEntity();
    service.setOrganizationId(context.organizationId());
    applyRequest(service, request.name(), request.description(), request.owningTeam(), request.isPublic());
    String slug = slugGenerator.slugify(request.name());
    if (serviceRepository.existsBySlug(context.organizationId(), slug)) {
      throw ApiException.conflict(ErrorCodes.DUPLICATE_SLUG, "A service with this name already exists.");
    }
    service.setSlug(slug);
    try {
      serviceRepository.saveAndFlush(service);
    } catch (DataIntegrityViolationException ex) {
      throw ApiException.conflict(ErrorCodes.DUPLICATE_SLUG, "A service with this name already exists.");
    }
    events.publishEvent(
        new ServiceCreated(context.organizationId(), context.userId(), service.getId(), service.getName(), Instant.now(clock)));
    return toView(service);
  }

  @Transactional(readOnly = true)
  public List<ServiceView> list(boolean includeArchived) {
    var context = TenantContext.require();
    return serviceRepository.findAllInOrganization(context.organizationId(), includeArchived).stream()
        .map(ServiceCatalogService::toView)
        .toList();
  }

  @Transactional(readOnly = true)
  public ServiceView get(UUID serviceId) {
    var context = TenantContext.require();
    return toView(load(serviceId, context.organizationId()));
  }

  @Transactional
  public ServiceView update(UUID serviceId, UpdateServiceRequest request) {
    var context = TenantContext.require();
    accessPolicy.requireAdminLike(context, "manage services");
    ServiceEntity service = load(serviceId, context.organizationId());
    applyRequest(
        service,
        request.name() != null ? request.name() : service.getName(),
        request.description() != null ? request.description() : service.getDescription(),
        request.owningTeam() != null ? request.owningTeam() : service.getOwningTeam(),
        request.isPublic() != null ? request.isPublic() : service.isPublic());
    events.publishEvent(
        new ServiceUpdated(context.organizationId(), context.userId(), service.getId(), service.getName(), Instant.now(clock)));
    return toView(service);
  }

  @Transactional
  public ServiceView archive(UUID serviceId) {
    var context = TenantContext.require();
    accessPolicy.requireAdminLike(context, "manage services");
    ServiceEntity service = load(serviceId, context.organizationId());
    if (service.isArchived()) {
      return toView(service);
    }
    ActiveIncidentLookup lookup = activeIncidentLookup.getIfAvailable();
    long active = lookup == null ? 0 : lookup.countActiveIncidentsForService(context.organizationId(), serviceId);
    if (active > 0) {
      throw ApiException.conflict(
          ErrorCodes.SERVICE_HAS_ACTIVE_INCIDENTS,
          "This service has " + active + " active incident(s) and cannot be archived.");
    }
    service.setArchivedAt(Instant.now(clock));
    events.publishEvent(
        new ServiceArchived(context.organizationId(), context.userId(), service.getId(), service.getName(), Instant.now(clock)));
    return toView(service);
  }

  private ServiceEntity load(UUID serviceId, UUID organizationId) {
    return serviceRepository
        .findByIdAndOrganizationId(serviceId, organizationId)
        .orElseThrow(() -> ApiException.notFound("Service not found."));
  }

  private void applyRequest(
      ServiceEntity service, String name, String description, String owningTeam, Boolean isPublic) {
    service.setName(name.trim());
    if (description != null) {
      service.setDescription(description.trim());
    }
    if (owningTeam != null) {
      service.setOwningTeam(owningTeam.trim());
    }
    if (isPublic != null) {
      service.setPublic(isPublic);
    }
  }

  private static ServiceView toView(ServiceEntity service) {
    return new ServiceView(
        service.getId(),
        service.getName(),
        service.getSlug(),
        service.getDescription(),
        service.getOwningTeam(),
        service.isPublic(),
        service.getArchivedAt(),
        service.getCreatedAt());
  }
}
