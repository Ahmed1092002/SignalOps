package com.signalops.incidents.web;

import com.signalops.incidents.app.FollowUpTaskService;
import com.signalops.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/follow-up-tasks")
@RequiredArgsConstructor
public class FollowUpTaskController {

  private final FollowUpTaskService followUpTaskService;

  public record CreateRequest(
      @NotBlank @Size(min = 1, max = 200) String title,
      UUID assigneeUserId,
      Instant dueAt) {}

  @PostMapping
  public ResponseEntity<FollowUpTaskService.FollowUpTaskView> create(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID incidentId,
      @Valid @RequestBody CreateRequest request) {
    return ResponseEntity.status(201)
        .body(followUpTaskService.create(incidentId, request.title(), request.assigneeUserId(), request.dueAt()));
  }

  @GetMapping
  public List<FollowUpTaskService.FollowUpTaskView> list(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID incidentId) {
    return followUpTaskService.list(incidentId);
  }

  @PostMapping("/{taskId}/toggle")
  public FollowUpTaskService.FollowUpTaskView toggle(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID incidentId,
      @PathVariable UUID taskId) {
    return followUpTaskService.toggleDone(incidentId, taskId);
  }
}
