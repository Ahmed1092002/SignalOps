package com.signalops.servicecatalog.web;

import com.signalops.servicecatalog.app.MaintenanceWindowService;
import com.signalops.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maintenance-windows")
@RequiredArgsConstructor
public class MaintenanceWindowController {

  private final MaintenanceWindowService maintenanceWindowService;

  public record MaintenanceWindowView(
      UUID id, UUID serviceId, String title, Instant startsAt, Instant endsAt, boolean active) {}

  public record CreateRequest(
      @NotNull UUID serviceId,
      @NotBlank String title,
      @NotNull Instant startsAt,
      @NotNull Instant endsAt) {}

  @PostMapping
  public ResponseEntity<MaintenanceWindowService.MaintenanceWindowView> create(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateRequest request) {
    return ResponseEntity.status(201)
        .body(maintenanceWindowService.create(new MaintenanceWindowService.CreateWindowRequest(
            request.serviceId(), request.title(), request.startsAt(), request.endsAt())));
  }

  @GetMapping
  public List<MaintenanceWindowService.MaintenanceWindowView> list() {
    return maintenanceWindowService.list();
  }

  @DeleteMapping("/{windowId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID windowId) {
    maintenanceWindowService.delete(windowId);
    return ResponseEntity.noContent().build();
  }
}
