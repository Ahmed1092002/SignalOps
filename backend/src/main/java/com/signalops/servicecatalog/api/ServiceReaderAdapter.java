package com.signalops.servicecatalog.api;

import com.signalops.servicecatalog.domain.ServiceEntity;
import com.signalops.servicecatalog.domain.ServiceRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ServiceReaderAdapter implements ServiceReader {

  private final ServiceRepository serviceRepository;
  private final JdbcTemplate jdbcTemplate;

  @Override
  @Transactional(readOnly = true)
  public Optional<ServiceSummary> summary(UUID organizationId, UUID serviceId) {
    return serviceRepository
        .findByIdAndOrganizationId(serviceId, organizationId)
        .map(ServiceReaderAdapter::toSummary);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, List<String>> namesByIncident(UUID organizationId, List<UUID> incidentIds) {
    Map<UUID, List<String>> result = new HashMap<>();
    if (incidentIds == null || incidentIds.isEmpty()) {
      return result;
    }
    String sql =
        "SELECT ils.incident_id, s.name FROM incident_services ils "
            + "JOIN services s ON s.id = ils.service_id "
            + "WHERE ils.organization_id = ? AND ils.incident_id = ANY (?)";
    jdbcTemplate.query(
        sql,
        resultSet -> {
          result
              .computeIfAbsent(resultSet.getObject("incident_id", UUID.class), key -> new ArrayList<>())
              .add(resultSet.getString("name"));
        },
        organizationId,
        incidentIds.toArray(new UUID[0]));
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ServiceSummary> listActive(UUID organizationId) {
    return serviceRepository.findAllInOrganization(organizationId, false).stream()
        .map(ServiceReaderAdapter::toSummary)
        .toList();
  }

  private static ServiceSummary toSummary(ServiceEntity service) {
    return new ServiceSummary(service.getId(), service.getName(), service.getSlug(), service.isPublic());
  }
}
