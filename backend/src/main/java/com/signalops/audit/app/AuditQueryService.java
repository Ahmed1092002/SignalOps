package com.signalops.audit.app;

import com.signalops.audit.domain.AuditEvent;
import com.signalops.audit.domain.AuditEventRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.api.PageResponse;
import com.signalops.shared.security.TenantContext;
import com.signalops.shared.security.TenantContextValue;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-side queries over the append-only audit log. Owner/Admin visibility enforced here. */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

  private final AuditEventRepository auditEventRepository;

  public record AuditEventView(
      UUID id,
      UUID actorUserId,
      String action,
      String entityType,
      UUID entityId,
      Map<String, Object> metadata,
      Instant occurredAt) {}

  @Transactional(readOnly = true)
  public PageResponse<AuditEventView> search(
      String action,
      String entityType,
      UUID actorUserId,
      Instant from,
      Instant to,
      int page,
      int size) {
    TenantContextValue context = TenantContext.require();
    if (!context.isAdminLike()) {
      throw ApiException.forbidden("Only Owners and Admins can view the audit log.");
    }
    Page<AuditEvent> result =
        auditEventRepository.search(
            context.organizationId(),
            action,
            entityType,
            actorUserId,
            from,
            to,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    List<AuditEventView> items =
        result.getContent().stream()
            .map(
                event ->
                    new AuditEventView(
                        event.getId(),
                        event.getActorUserId(),
                        event.getAction(),
                        event.getEntityType(),
                        event.getEntityId(),
                        event.getMetadata(),
                        event.getOccurredAt()))
            .toList();
    return new PageResponse<>(
        items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
  }
}
