package com.signalops.incidents.app;

import com.signalops.incidents.api.IncidentInsightsApi;
import com.signalops.incidents.api.IncidentStatus;
import com.signalops.incidents.api.Severity;
import com.signalops.incidents.domain.Incident;
import com.signalops.incidents.domain.IncidentRepository;
import com.signalops.incidents.domain.IncidentResponderRepository;
import com.signalops.incidents.domain.IncidentServiceLinkRepository;
import com.signalops.servicecatalog.api.ActiveIncidentLookup;
import com.signalops.servicecatalog.api.ServiceReader;
import com.signalops.shared.domain.Role;
import com.signalops.shared.security.TenantContext;
import com.signalops.users.api.UserDirectory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-model adapters exposing incident insights to other modules (dashboard analytics,
 * service archive guard) without leaking incident internals.
 */
@Component
@RequiredArgsConstructor
public class IncidentInsightsAdapter implements IncidentInsightsApi, ActiveIncidentLookup {

  private final IncidentRepository incidentRepository;
  private final IncidentServiceLinkRepository serviceLinkRepository;
  private final IncidentResponderRepository responderRepository;
  private final ServiceReader serviceReader;
  private final UserDirectory userDirectory;
  private final com.signalops.incidents.api.TimelineReader timelineReader;

  @Override
  @Transactional(readOnly = true)
  public long countActiveIncidentsForService(UUID organizationId, UUID serviceId) {
    return incidentRepository.findAllInOrganizationActive(organizationId).stream()
        .filter(
            incident ->
                serviceLinkRepository
                    .findByIncidentIdAndServiceIdAndOrganizationId(
                        incident.getId(), serviceId, organizationId)
                    .isPresent())
        .count();
  }

  @Override
  @Transactional(readOnly = true)
  public long countActive(UUID organizationId) {
    return incidentRepository.countByOrganizationIdAndArchivedAtIsNullAndStatusNot(
        organizationId, IncidentStatus.RESOLVED);
  }

  @Override
  @Transactional(readOnly = true)
  public long countActiveCritical(UUID organizationId) {
    return incidentRepository.countByOrganizationIdAndArchivedAtIsNullAndStatusNotAndSeverity(
        organizationId, IncidentStatus.RESOLVED, Severity.CRITICAL);
  }

  @Override
  @Transactional(readOnly = true)
  public long countCreatedSince(UUID organizationId, Instant from) {
    return incidentRepository.countByOrganizationIdAndArchivedAtIsNullAndCreatedAtGreaterThanEqual(
        organizationId, from);
  }

  @Override
  @Transactional(readOnly = true)
  public Double meanResolutionSeconds(UUID organizationId) {
    List<Incident> resolved =
        incidentRepository.findByOrganizationIdAndArchivedAtIsNullAndDurationSecondsNotNull(
            organizationId);
    if (resolved.isEmpty()) {
      return null;
    }
    return resolved.stream()
        .mapToLong(Incident::getDurationSeconds)
        .average()
        .orElse(Double.NaN);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ServiceIncidentLoad> activeSeveritiesByService(UUID organizationId) {
    List<Incident> active = incidentRepository.findAllInOrganizationActive(organizationId);
    return serviceReader.listActive(organizationId).stream()
        .map(
            service ->
                new ServiceIncidentLoad(
                    service.id(),
                    active.stream()
                        .filter(
                            incident ->
                                serviceLinkRepository
                                    .findByIncidentIdAndServiceIdAndOrganizationId(
                                        incident.getId(), service.id(), organizationId)
                                    .isPresent())
                        .map(Incident::getSeverity)
                        .toList()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActivityItem> recentActivity(UUID organizationId, int limit) {
    return timelineReader.recentForOrganization(organizationId, limit).stream()
        .map(
            item -> {
              Optional<Incident> incident =
                  incidentRepository.findByIdAndOrganizationId(item.incidentId(), organizationId);
              return new ActivityItem(
                  incident.map(Incident::getId).orElse(null),
                  incident.map(Incident::getKey).orElse(""),
                  incident.map(Incident::getTitle).orElse(""),
                  item.type(),
                  item.actorUserId(),
                  item.actorName(),
                  item.occurredAt(),
                  item.payload());
            })
        .toList();
  }
}
