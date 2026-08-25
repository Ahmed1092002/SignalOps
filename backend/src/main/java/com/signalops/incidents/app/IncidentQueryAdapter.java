package com.signalops.incidents.app;

import com.signalops.incidents.api.IncidentQueryApi;
import com.signalops.incidents.domain.Incident;
import com.signalops.incidents.domain.IncidentRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class IncidentQueryAdapter implements IncidentQueryApi {

  private final IncidentRepository incidentRepository;

  @Override
  @Transactional(readOnly = true)
  public List<IncidentSummary> findByService(UUID organizationId, UUID serviceId, int page, int size) {
    return incidentRepository
        .search(
            organizationId,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            serviceId,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")))
        .getContent()
        .stream()
        .map(IncidentQueryAdapter::toSummary)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countByService(UUID organizationId, UUID serviceId) {
    return incidentRepository
        .search(
            organizationId,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            serviceId,
            PageRequest.of(0, 1))
        .getTotalElements();
  }

  private static IncidentSummary toSummary(Incident incident) {
    return new IncidentSummary(
        incident.getId(),
        incident.getKey(),
        incident.getTitle(),
        incident.getSeverity(),
        incident.getStatus(),
        incident.getCreatedAt());
  }
}
