package com.signalops.servicecatalog.app;

import com.signalops.servicecatalog.api.MaintenanceWindowLookup;
import com.signalops.servicecatalog.domain.MaintenanceWindow;
import com.signalops.servicecatalog.domain.MaintenanceWindowRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaintenanceWindowService implements MaintenanceWindowLookup {

  private final MaintenanceWindowRepository repository;
  private final CatalogAccessPolicy accessPolicy;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public boolean activeWindowExists(UUID organizationId, UUID serviceId) {
    return repository.activeWindowExists(organizationId, serviceId, Instant.now(clock));
  }

  public record MaintenanceWindowView(
      UUID id, UUID serviceId, String title, Instant startsAt, Instant endsAt, boolean active) {}

  public record CreateWindowRequest(UUID serviceId, String title, Instant startsAt, Instant endsAt) {}

  @Transactional
  public MaintenanceWindowView create(CreateWindowRequest request) {
    var context = TenantContext.require();
    accessPolicy.requireAdminLike(context, "schedule maintenance");
    if (!request.endsAt().isAfter(request.startsAt())) {
      throw ApiException.badRequest(
          ErrorCodes.VALIDATION_FAILED, "The maintenance end must be after the start.");
    }
    MaintenanceWindow window = new MaintenanceWindow();
    window.setOrganizationId(context.organizationId());
    window.setServiceId(request.serviceId());
    window.setTitle(request.title().trim());
    window.setStartsAt(request.startsAt());
    window.setEndsAt(request.endsAt());
    window.setCreatedByUserId(context.userId());
    return toView(repository.save(window));
  }

  @Transactional(readOnly = true)
  public List<MaintenanceWindowView> list() {
    var context = TenantContext.require();
    return repository.findByOrganizationIdOrderByStartsAtDesc(context.organizationId()).stream()
        .map(this::toView)
        .toList();
  }

  @Transactional
  public void delete(UUID windowId) {
    var context = TenantContext.require();
    accessPolicy.requireAdminLike(context, "delete maintenance windows");
    MaintenanceWindow window =
        repository
            .findByIdAndOrganizationId(windowId, context.organizationId())
            .orElseThrow(() -> ApiException.notFound("Maintenance window not found."));
    repository.delete(window);
  }

  private MaintenanceWindowView toView(MaintenanceWindow window) {
    return new MaintenanceWindowView(
        window.getId(),
        window.getServiceId(),
        window.getTitle(),
        window.getStartsAt(),
        window.getEndsAt(),
        window.isActive(Instant.now(clock)));
  }
}
