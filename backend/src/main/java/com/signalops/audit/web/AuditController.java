package com.signalops.audit.web;

import com.signalops.audit.app.AuditQueryService;
import com.signalops.audit.app.AuditQueryService.AuditEventView;
import com.signalops.shared.api.PageResponse;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
@RequiredArgsConstructor
public class AuditController {

  private final AuditQueryService auditQueryService;

  @GetMapping
  public PageResponse<AuditEventView> list(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return auditQueryService.search(action, entityType, actorId, from, to, page, size);
  }
}
