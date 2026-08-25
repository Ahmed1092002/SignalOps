package com.signalops.incidents.web;

import com.signalops.incidents.api.IncidentDtos;
import com.signalops.incidents.api.IncidentStatus;
import com.signalops.incidents.api.Severity;
import com.signalops.incidents.app.IncidentAppService;
import com.signalops.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IncidentController {

  private final IncidentAppService incidentAppService;

  @PostMapping("/incidents")
  public ResponseEntity<IncidentDtos.IncidentView> create(
      @Valid @RequestBody IncidentDtos.CreateIncidentRequest request) {
    return ResponseEntity.status(201).body(incidentAppService.create(request));
  }

  @GetMapping("/incidents")
  public PageResponse<IncidentDtos.IncidentRowView> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) IncidentStatus status,
      @RequestParam(required = false) Severity severity,
      @RequestParam(required = false) UUID serviceId,
      @RequestParam(required = false) UUID responderId,
      @RequestParam(required = false) Instant createdFrom,
      @RequestParam(required = false) Instant createdTo,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return incidentAppService.list(
        q, status, severity, serviceId, responderId, createdFrom, createdTo, archived, page, size);
  }

  @GetMapping("/incidents/{incidentId}")
  public IncidentDtos.IncidentDetailView get(@PathVariable UUID incidentId) {
    return incidentAppService.get(incidentId);
  }

  @PatchMapping("/incidents/{incidentId}")
  public IncidentDtos.IncidentView update(
      @PathVariable UUID incidentId, @Valid @RequestBody IncidentDtos.UpdateIncidentRequest request) {
    return incidentAppService.update(incidentId, request.title(), request.description(), request.version());
  }

  @PostMapping("/incidents/{incidentId}/transitions")
  public IncidentDtos.IncidentView transition(
      @PathVariable UUID incidentId, @Valid @RequestBody IncidentDtos.TransitionRequest request) {
    return incidentAppService.transition(incidentId, request);
  }

  @PutMapping("/incidents/{incidentId}/severity")
  public IncidentDtos.IncidentView changeSeverity(
      @PathVariable UUID incidentId, @Valid @RequestBody IncidentDtos.SeverityRequest request) {
    return incidentAppService.changeSeverity(incidentId, request.severity(), request.version());
  }

  @PutMapping("/incidents/{incidentId}/commander")
  public IncidentDtos.IncidentView setCommander(
      @PathVariable UUID incidentId, @Valid @RequestBody IncidentDtos.CommanderRequest request) {
    return incidentAppService.setCommander(incidentId, request.commanderUserId(), request.version());
  }

  @PostMapping("/incidents/{incidentId}/responders")
  public IncidentDtos.IncidentView addResponder(
      @PathVariable UUID incidentId, @Valid @RequestBody IncidentDtos.ResponderRequest request) {
    return incidentAppService.addResponder(incidentId, request.responderUserId(), request.version());
  }

  @DeleteMapping("/incidents/{incidentId}/responders/{responderUserId}")
  public IncidentDtos.IncidentView removeResponder(
      @PathVariable UUID incidentId,
      @PathVariable UUID responderUserId,
      @RequestParam long version) {
    return incidentAppService.removeResponder(incidentId, responderUserId, version);
  }

  @PostMapping("/incidents/{incidentId}/comments")
  public ResponseEntity<IncidentDtos.IncidentDetailView> addComment(
      @PathVariable UUID incidentId, @Valid @RequestBody IncidentDtos.CommentRequest request) {
    return ResponseEntity.status(201).body(incidentAppService.addComment(incidentId, request));
  }

  @GetMapping("/incidents/{incidentId}/comments")
  public List<IncidentDtos.CommentView> comments(@PathVariable UUID incidentId) {
    return incidentAppService.comments(incidentId);
  }

  @PostMapping("/incidents/{incidentId}/public-updates")
  public ResponseEntity<IncidentDtos.IncidentDetailView> publishUpdate(
      @PathVariable UUID incidentId, @Valid @RequestBody IncidentDtos.PublicUpdateRequest request) {
    return ResponseEntity.status(201).body(incidentAppService.publishPublicUpdate(incidentId, request));
  }

  @GetMapping("/incidents/{incidentId}/public-updates")
  public List<IncidentDtos.PublicUpdateView> publicUpdates(@PathVariable UUID incidentId) {
    return incidentAppService.publicUpdates(incidentId);
  }

  @PostMapping("/incidents/{incidentId}/archive")
  public IncidentDtos.IncidentView archive(
      @PathVariable UUID incidentId, @Valid @RequestBody IncidentDtos.ArchiveRequest request) {
    return incidentAppService.archive(incidentId, request.version());
  }
}
