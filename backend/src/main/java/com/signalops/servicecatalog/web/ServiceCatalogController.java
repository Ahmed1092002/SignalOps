package com.signalops.servicecatalog.web;

import com.signalops.servicecatalog.api.ServiceCatalogDtos.CreateServiceRequest;
import com.signalops.servicecatalog.api.ServiceCatalogDtos.ServiceView;
import com.signalops.servicecatalog.api.ServiceCatalogDtos.UpdateServiceRequest;
import com.signalops.servicecatalog.app.ServiceCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceCatalogController {

  private final ServiceCatalogService serviceCatalogService;
  private final com.signalops.incidents.api.IncidentQueryApi incidentQueryApi;

  @PostMapping
  public ResponseEntity<ServiceView> create(@Valid @RequestBody CreateServiceRequest request) {
    return ResponseEntity.status(201).body(serviceCatalogService.create(request));
  }

  @GetMapping
  public List<ServiceView> list(
      @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived) {
    return serviceCatalogService.list(includeArchived);
  }

  @GetMapping("/{serviceId}")
  public ServiceView get(@PathVariable UUID serviceId) {
    return serviceCatalogService.get(serviceId);
  }

  @PatchMapping("/{serviceId}")
  public ServiceView update(
      @PathVariable UUID serviceId, @Valid @RequestBody UpdateServiceRequest request) {
    return serviceCatalogService.update(serviceId, request);
  }

  @PostMapping("/{serviceId}/archive")
  public ServiceView archive(@PathVariable UUID serviceId) {
    return serviceCatalogService.archive(serviceId);
  }

  @GetMapping("/{serviceId}/incidents")
  public com.signalops.shared.api.PageResponse<com.signalops.incidents.api.IncidentQueryApi.IncidentSummary>
      incidents(
          @PathVariable UUID serviceId,
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "20") int size) {
    var context = com.signalops.shared.security.TenantContext.require();
    var result = incidentQueryApi.findByService(context.organizationId(), serviceId, page, size);
    long total = incidentQueryApi.countByService(context.organizationId(), serviceId);
    return com.signalops.shared.api.PageResponse.of(result, page, size, total);
  }
}
