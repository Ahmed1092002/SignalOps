package com.signalops.servicecatalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalops.incidents.api.IncidentQueryApi;
import com.signalops.incidents.api.IncidentQueryApi.IncidentSummary;
import com.signalops.servicecatalog.app.ServiceCatalogService;
import com.signalops.shared.api.PageResponse;
import com.signalops.shared.domain.Role;
import com.signalops.shared.security.TenantContext;
import com.signalops.shared.security.TenantContextValue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceCatalogControllerTest {

  private ServiceCatalogService serviceCatalogService;
  private IncidentQueryApi incidentQueryApi;
  private ServiceCatalogController controller;

  private UUID orgId;
  private UUID serviceId;

  @BeforeEach
  void setUp() {
    serviceCatalogService = mock(ServiceCatalogService.class);
    incidentQueryApi = mock(IncidentQueryApi.class);
    controller = new ServiceCatalogController(serviceCatalogService, incidentQueryApi);

    orgId = UUID.randomUUID();
    serviceId = UUID.randomUUID();
    TenantContext.set(new TenantContextValue(UUID.randomUUID(), orgId, UUID.randomUUID(), Role.OWNER, "User"));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void negativePageNormalizedToZero() {
    when(incidentQueryApi.findByService(eq(orgId), eq(serviceId), eq(0), eq(10)))
        .thenReturn(List.of());
    when(incidentQueryApi.countByService(eq(orgId), eq(serviceId)))
        .thenReturn(0L);

    PageResponse<IncidentSummary> response = controller.incidents(serviceId, -1, 10);

    assertThat(response.page()).isEqualTo(0);
    assertThat(response.size()).isEqualTo(10);
    verify(incidentQueryApi).findByService(orgId, serviceId, 0, 10);
  }

  @Test
  void zeroOrNegativeSizeNormalizedToOne() {
    when(incidentQueryApi.findByService(eq(orgId), eq(serviceId), eq(0), eq(1)))
        .thenReturn(List.of());
    when(incidentQueryApi.countByService(eq(orgId), eq(serviceId)))
        .thenReturn(0L);

    PageResponse<IncidentSummary> resZero = controller.incidents(serviceId, 0, 0);
    assertThat(resZero.page()).isEqualTo(0);
    assertThat(resZero.size()).isEqualTo(1);

    PageResponse<IncidentSummary> resNeg = controller.incidents(serviceId, 0, -5);
    assertThat(resNeg.page()).isEqualTo(0);
    assertThat(resNeg.size()).isEqualTo(1);

    verify(incidentQueryApi, org.mockito.Mockito.times(2)).findByService(orgId, serviceId, 0, 1);
  }

  @Test
  void oversizedSizeCappedAtOneHundred() {
    when(incidentQueryApi.findByService(eq(orgId), eq(serviceId), eq(0), eq(100)))
        .thenReturn(List.of());
    when(incidentQueryApi.countByService(eq(orgId), eq(serviceId)))
        .thenReturn(150L);

    PageResponse<IncidentSummary> response = controller.incidents(serviceId, 0, 500);

    assertThat(response.page()).isEqualTo(0);
    assertThat(response.size()).isEqualTo(100);
    assertThat(response.totalElements()).isEqualTo(150L);
    assertThat(response.totalPages()).isEqualTo(2);
    verify(incidentQueryApi).findByService(orgId, serviceId, 0, 100);
  }
}
