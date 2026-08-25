package com.signalops.analytics.app;

import com.signalops.incidents.api.IncidentInsightsApi;
import com.signalops.servicecatalog.api.MaintenanceWindowLookup;
import com.signalops.servicecatalog.api.ServiceReader;
import com.signalops.analytics.app.ServiceStatusDerivation;
import com.signalops.shared.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

  private final IncidentInsightsApi incidentInsights;
  private final ServiceReader serviceReader;
  private final MaintenanceWindowLookup maintenanceWindowLookup;
  private final Clock clock;

  private String withMaintenanceOverride(java.util.UUID serviceId, String derived) {
    return maintenanceWindowLookup.activeWindowExists(
            TenantContext.require().organizationId(), serviceId)
        ? ServiceStatusDerivation.worst(derived, ServiceStatusDerivation.MAINTENANCE)
        : derived;
  }

  public record ServiceHealth(UUID serviceId, String name, String status, long activeIncidents) {}

  public record ActivityItem(
      String incidentKey,
      String incidentTitle,
      String eventType,
      String actorName,
      Instant occurredAt) {}

  public record DashboardSummary(
      long activeIncidents,
      long criticalIncidents,
      long incidentsThisMonth,
      Double meanResolutionSeconds,
      List<ServiceHealth> serviceHealth,
      List<ActivityItem> recentActivity) {}

  @Transactional(readOnly = true)
  public DashboardSummary summary() {
    UUID organizationId = TenantContext.require().organizationId();
    Instant monthStart =
        Instant.now(clock).truncatedTo(ChronoUnit.DAYS).atZone(ZoneOffset.UTC).toInstant();

    List<ServiceHealth> serviceHealth =
        incidentInsights.activeSeveritiesByService(organizationId).stream()
            .map(
                load ->
                    serviceReader
                        .summary(organizationId, load.serviceId())
                        .map(
                            service ->
                                new ServiceHealth(
                                    service.id(),
                                    service.name(),
                                    withMaintenanceOverride(load.serviceId(),
                                        ServiceStatusDerivation.derive(load.activeSeverities())),
                                    load.activeSeverities().size()))
                        .orElse(null))
            .filter(health -> health != null)
            .toList();

    List<ActivityItem> recentActivity =
        incidentInsights.recentActivity(organizationId, 8).stream()
            .map(
                item ->
                    new ActivityItem(
                        item.incidentKey(),
                        item.incidentTitle(),
                        item.eventType(),
                        item.actorName(),
                        item.occurredAt()))
            .toList();

    return new DashboardSummary(
        incidentInsights.countActive(organizationId),
        incidentInsights.countActiveCritical(organizationId),
        incidentInsights.countCreatedSince(organizationId, monthStart),
        incidentInsights.meanResolutionSeconds(organizationId),
        serviceHealth,
        recentActivity);
  }
}
