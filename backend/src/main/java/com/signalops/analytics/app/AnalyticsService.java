package com.signalops.analytics.app;

import com.signalops.incidents.api.Severity;
import com.signalops.incidents.domain.Incident;
import com.signalops.incidents.domain.IncidentRepository;
import com.signalops.servicecatalog.api.ServiceReader;
import com.signalops.shared.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

  private final IncidentRepository incidentRepository;
  private final ServiceReader serviceReader;
  private final Clock clock;

  public record DayPoint(String date, long created, long resolved) {}

  public record SeverityCount(String severity, long count) {}

  public record ServiceCount(String serviceName, long count) {}

  public record Overview(
      int days,
      List<DayPoint> series,
      List<SeverityCount> severityDistribution,
      List<ServiceCount> perService,
      Double meanTimeToResolveSeconds,
      Double meanTimeToDetectSeconds,
      long resolvedCount) {}

  @Transactional(readOnly = true)
  public Overview overview(int days) {
    UUID organizationId = TenantContext.require().organizationId();
    LocalDate today = LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC);
    LocalDate start = today.minusDays(days - 1L);
    Instant startInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant();

    List<Incident> incidents =
        incidentRepository.findByOrganizationIdAndArchivedAtIsNullAndCreatedAtGreaterThanEqual(
            organizationId, startInstant);

    Map<LocalDate, long[]> byDay = new HashMap<>();
    Map<Severity, Long> bySeverity = new HashMap<>();
    Map<UUID, Long> byService = new HashMap<>();
    List<Long> durations = new ArrayList<>();
    List<Long> detections = new ArrayList<>();

    for (Incident incident : incidents) {
      LocalDate created = LocalDate.ofInstant(incident.getCreatedAt(), ZoneOffset.UTC);
      byDay.computeIfAbsent(created, key -> new long[2])[0]++;
      bySeverity.merge(incident.getSeverity(), 1L, Long::sum);
      if (incident.getResolvedAt() != null) {
        LocalDate resolved = LocalDate.ofInstant(incident.getResolvedAt(), ZoneOffset.UTC);
        byDay.computeIfAbsent(resolved, key -> new long[2])[1]++;
      }
      if (incident.getDurationSeconds() != null) {
        durations.add(incident.getDurationSeconds());
      }
      if (incident.getAcknowledgedAt() != null) {
        detections.add(
            java.time.Duration.between(incident.getCreatedAt(), incident.getAcknowledgedAt()).getSeconds());
      }
      for (var link : serviceLinkRepository.findByIncidentIdAndOrganizationId(
          incident.getId(), organizationId)) {
        byService.merge(link.getServiceId(), 1L, Long::sum);
      }
    }

    List<DayPoint> series = new ArrayList<>();
    for (int offset = 0; offset < days; offset++) {
      LocalDate day = start.plusDays(offset);
      long[] counts = byDay.getOrDefault(day, new long[2]);
      series.add(new DayPoint(day.toString(), counts[0], counts[1]));
    }

    List<SeverityCount> severityDistribution =
        java.util.Arrays.stream(Severity.values())
            .map(severity -> new SeverityCount(severity.name(), bySeverity.getOrDefault(severity, 0L)))
            .toList();

    List<ServiceCount> perService =
        byService.entrySet().stream()
            .map(
                entry ->
                    new ServiceCount(
                        serviceReader
                            .summary(organizationId, entry.getKey())
                            .map(com.signalops.servicecatalog.api.ServiceReader.ServiceSummary::name)
                            .orElse("Unknown"),
                        entry.getValue()))
            .sorted((a, b) -> Long.compare(b.count(), a.count()))
            .toList();

    return new Overview(
        days,
        series,
        severityDistribution,
        perService,
        durations.isEmpty() ? null : durations.stream().mapToLong(Long::longValue).average().orElse(0),
        detections.isEmpty() ? null : detections.stream().mapToLong(Long::longValue).average().orElse(0),
        durations.size());
  }

  private final com.signalops.incidents.domain.IncidentServiceLinkRepository serviceLinkRepository;
}
