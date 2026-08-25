package com.signalops.publicstatus.app;

import com.signalops.analytics.app.ServiceStatusDerivation;
import com.signalops.incidents.api.IncidentStatus;
import com.signalops.incidents.domain.Incident;
import com.signalops.incidents.domain.IncidentPublicUpdate;
import com.signalops.incidents.domain.IncidentPublicUpdateRepository;
import com.signalops.incidents.domain.IncidentRepository;
import com.signalops.incidents.domain.IncidentServiceLinkRepository;
import com.signalops.organizations.domain.Organization;
import com.signalops.organizations.domain.OrganizationRepository;
import com.signalops.servicecatalog.api.MaintenanceWindowLookup;
import com.signalops.servicecatalog.domain.ServiceEntity;
import com.signalops.servicecatalog.domain.ServiceRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the public status page from public-safe data only. Internal comments,
 * participants, audit data, and internal keys are structurally absent from these
 * records (R5/R14).
 */
@Service
public class PublicStatusService {

  private final OrganizationRepository organizationRepository;
  private final ServiceRepository serviceRepository;
  private final IncidentRepository incidentRepository;
  private final IncidentServiceLinkRepository serviceLinkRepository;
  private final IncidentPublicUpdateRepository publicUpdateRepository;
  private final MaintenanceWindowLookup maintenanceWindowLookup;

  public PublicStatusService(
      OrganizationRepository organizationRepository,
      ServiceRepository serviceRepository,
      IncidentRepository incidentRepository,
      IncidentServiceLinkRepository serviceLinkRepository,
      IncidentPublicUpdateRepository publicUpdateRepository,
      MaintenanceWindowLookup maintenanceWindowLookup) {
    this.organizationRepository = organizationRepository;
    this.serviceRepository = serviceRepository;
    this.incidentRepository = incidentRepository;
    this.serviceLinkRepository = serviceLinkRepository;
    this.publicUpdateRepository = publicUpdateRepository;
    this.maintenanceWindowLookup = maintenanceWindowLookup;
  }

  public record PublicServiceStatus(String name, String status) {}

  public record PublicUpdateView(String message, String statusAtPublish, String publishedAt) {}

  public record PublicActiveIncident(
      String title, String severity, String status, String updatedAt, List<PublicUpdateView> updates) {}

  public record PublicPastIncident(String date, String title, String latestUpdate, String resolvedAt) {}

  public record PublicStatusView(
      String organizationName,
      String overallStatus,
      List<PublicServiceStatus> services,
      List<PublicActiveIncident> activeIncidents,
      List<PublicPastIncident> pastIncidents) {}

  @Transactional(readOnly = true)
  public PublicStatusView status(String orgSlug) {
    Organization org = loadOrg(orgSlug);
    List<ServiceEntity> services =
        serviceRepository.findAllInOrganization(org.getId(), false).stream()
            .filter(ServiceEntity::isPublic)
            .toList();
    List<Incident> active = incidentRepository.findAllInOrganizationActive(org.getId());

    List<PublicServiceStatus> serviceViews = new ArrayList<>();
    for (ServiceEntity service : services) {
      List<com.signalops.incidents.api.Severity> severities = new ArrayList<>();
      for (Incident incident : active) {
        if (affects(incident, service.getId())) {
          severities.add(incident.getSeverity());
        }
      }
      String derived = ServiceStatusDerivation.derive(severities);
      if (maintenanceWindowLookup.activeWindowExists(org.getId(), service.getId())) {
        derived = ServiceStatusDerivation.worst(derived, ServiceStatusDerivation.MAINTENANCE);
      }
      serviceViews.add(new PublicServiceStatus(service.getName(), derived));
    }

    String overall = ServiceStatusDerivation.OPERATIONAL;
    for (PublicServiceStatus serviceView : serviceViews) {
      overall = ServiceStatusDerivation.worst(overall, serviceView.status());
    }

    List<PublicActiveIncident> activeIncidentViews = new ArrayList<>();
    publicActiveIncidents(active).stream()
        .sorted((a, b) -> Integer.compare(rank(b.getSeverity()), rank(a.getSeverity())))
        .forEach(
            incident ->
                activeIncidentViews.add(
                    new PublicActiveIncident(
                        incident.getTitle(),
                        incident.getSeverity().name(),
                        incident.getStatus().name(),
                        incident.getUpdatedAt().toString(),
                        updateViews(incident))));

    List<PublicPastIncident> pastIncidentViews = new ArrayList<>();
    incidentRepository
        .findByOrganizationIdAndArchivedAtIsNullAndDurationSecondsNotNull(org.getId())
        .stream()
        .filter(incident -> incident.getStatus() == IncidentStatus.RESOLVED)
        .filter(incident -> !publicUpdates(incident).isEmpty())
        .sorted((a, b) -> b.getResolvedAt().compareTo(a.getResolvedAt()))
        .limit(20)
        .forEach(
            incident ->
                pastIncidentViews.add(
                    new PublicPastIncident(
                        incident.getResolvedAt().toString().substring(0, 10),
                        incident.getTitle(),
                        latestPublicMessage(incident),
                        incident.getResolvedAt().toString())));

    return new PublicStatusView(
        org.getName(), overall, serviceViews, activeIncidentViews, pastIncidentViews);
  }

  @Transactional(readOnly = true)
  public List<PublicPastIncident> history(String orgSlug, int page) {
    Organization org = loadOrg(orgSlug);
    List<PublicPastIncident> results = new ArrayList<>();
    incidentRepository
        .search(
            org.getId(),
            false,
            "RESOLVED",
            null,
            null,
            null,
            null,
            null,
            null,
            PageRequest.of(Math.max(page, 0), 20))
        .getContent()
        .stream()
        .filter(incident -> !publicUpdates(incident).isEmpty())
        .forEach(
            incident ->
                results.add(
                    new PublicPastIncident(
                        incident.getResolvedAt() == null
                            ? incident.getUpdatedAt().toString().substring(0, 10)
                            : incident.getResolvedAt().toString().substring(0, 10),
                        incident.getTitle(),
                        latestPublicMessage(incident),
                        incident.getResolvedAt() == null
                            ? incident.getUpdatedAt().toString()
                            : incident.getResolvedAt().toString())));
    return results;
  }

  private Organization loadOrg(String orgSlug) {
    return organizationRepository
        .findBySlug(orgSlug)
        .orElseThrow(() -> ApiException.notFound("Status page not found."));
  }

  private List<Incident> publicActiveIncidents(List<Incident> active) {
    List<Incident> result = new ArrayList<>();
    for (Incident incident : active) {
      if (!publicUpdates(incident).isEmpty()) {
        result.add(incident);
      }
    }
    return result;
  }

  private boolean affects(Incident incident, java.util.UUID serviceId) {
    return serviceLinkRepository
        .findByIncidentIdAndServiceIdAndOrganizationId(
            incident.getId(), serviceId, incident.getOrganizationId())
        .isPresent();
  }

  private List<IncidentPublicUpdate> publicUpdates(Incident incident) {
    return publicUpdateRepository.findByIncidentIdAndOrganizationIdOrderByPublishedAtDesc(
        incident.getId(), incident.getOrganizationId());
  }

  private List<PublicUpdateView> updateViews(Incident incident) {
    List<PublicUpdateView> updates = new ArrayList<>();
    for (IncidentPublicUpdate update : publicUpdates(incident)) {
      updates.add(
          new PublicUpdateView(
              update.getMessage(),
              update.getStatusAtPublish().name(),
              update.getPublishedAt().toString()));
    }
    return updates;
  }

  private String latestPublicMessage(Incident incident) {
    List<IncidentPublicUpdate> updates = publicUpdates(incident);
    return updates.isEmpty() ? "" : updates.get(0).getMessage();
  }

  private int rank(com.signalops.incidents.api.Severity severity) {
    return switch (severity) {
      case LOW -> 0;
      case MEDIUM -> 1;
      case HIGH -> 2;
      case CRITICAL -> 3;
    };
  }
}
