package com.signalops.audit.app;

import com.signalops.audit.domain.AuditEvent;
import com.signalops.audit.domain.AuditEventRepository;
import com.signalops.organizations.api.OrganizationEvents;
import com.signalops.servicecatalog.api.ServiceCatalogEvents;
import com.signalops.shared.security.TenantContext;
import com.signalops.shared.security.TenantContextValue;
import com.signalops.users.api.events.PasswordChanged;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records organization-level security/administrative activity in the same transaction as the
 * mutation it describes. Metadata carries safe summaries only — never secrets or raw payloads.
 */
@Component
@RequiredArgsConstructor
public class AuditRecorder {

  private final AuditEventRepository repository;

  @EventListener
  @Transactional
  public void onUserInvited(OrganizationEvents.UserInvited event) {
    record(
        event.organizationId(),
        event.actorUserId(),
        "USER_INVITED",
        "Membership",
        null,
        Map.of("email", event.email(), "role", event.role().name()),
        event.occurredAt());
  }

  @EventListener
  @Transactional
  public void onMemberRoleChanged(OrganizationEvents.MemberRoleChanged event) {
    record(
        event.organizationId(),
        event.actorUserId(),
        "MEMBER_ROLE_CHANGED",
        "Membership",
        event.memberId(),
        Map.of("from", event.from().name(), "to", event.to().name(), "email", event.memberEmail()),
        event.occurredAt());
  }

  @EventListener
  @Transactional
  public void onMemberDisabled(OrganizationEvents.MemberDisabled event) {
    record(
        event.organizationId(),
        event.actorUserId(),
        "MEMBER_DISABLED",
        "Membership",
        event.memberId(),
        Map.of("email", event.memberEmail()),
        event.occurredAt());
  }

  @EventListener
  @Transactional
  public void onMemberRemoved(OrganizationEvents.MemberRemoved event) {
    record(
        event.organizationId(),
        event.actorUserId(),
        "MEMBER_REMOVED",
        "Membership",
        event.memberId(),
        Map.of("email", event.memberEmail()),
        event.occurredAt());
  }

  @EventListener
  @Transactional
  public void onOrganizationSettingsUpdated(OrganizationEvents.OrganizationSettingsUpdated event) {
    record(
        event.organizationId(),
        event.actorUserId(),
        "ORG_SETTINGS_UPDATED",
        "Organization",
        event.organizationId(),
        Map.of("summary", event.summary()),
        event.occurredAt());
  }

  @EventListener
  @Transactional
  public void onServiceCreated(ServiceCatalogEvents.ServiceCreated event) {
    record(
        event.organizationId(),
        event.actorUserId(),
        "SERVICE_CREATED",
        "Service",
        event.serviceId(),
        Map.of("name", event.serviceName()),
        event.occurredAt());
  }

  @EventListener
  @Transactional
  public void onServiceUpdated(ServiceCatalogEvents.ServiceUpdated event) {
    record(
        event.organizationId(),
        event.actorUserId(),
        "SERVICE_UPDATED",
        "Service",
        event.serviceId(),
        Map.of("name", event.serviceName()),
        event.occurredAt());
  }

  @EventListener
  @Transactional
  public void onServiceArchived(ServiceCatalogEvents.ServiceArchived event) {
    record(
        event.organizationId(),
        event.actorUserId(),
        "SERVICE_ARCHIVED",
        "Service",
        event.serviceId(),
        Map.of("name", event.serviceName()),
        event.occurredAt());
  }

  @EventListener
  @Transactional
  public void onIncidentEdited(com.signalops.incidents.api.IncidentEvent event) {
    if (event.type() != com.signalops.incidents.api.IncidentEvent.IncidentEventType.EDITED) {
      return;
    }
    record(
        event.organizationId(),
        event.actorUserId(),
        "INCIDENT_EDITED",
        "Incident",
        event.incidentId(),
        Map.of("key", String.valueOf(event.payload().getOrDefault("key", ""))),
        event.occurredAt());
  }

  @EventListener
  @Transactional
  public void onPasswordChanged(PasswordChanged event) {
    for (UUID organizationId : event.organizationIds()) {
      record(
          organizationId,
          event.userId(),
          "PASSWORD_CHANGED",
          "User",
          event.userId(),
          Map.of(),
          event.occurredAt());
    }
  }

  @Transactional(readOnly = true)
  public Page<AuditEvent> search(
      String action,
      String entityType,
      UUID actorUserId,
      Instant from,
      Instant to,
      int page,
      int size) {
    TenantContextValue context = TenantContext.require();
    if (!context.isAdminLike()) {
      throw com.signalops.shared.api.ApiException.forbidden(
          "Only Owners and Admins can view the audit log.");
    }
    return repository.search(
        context.organizationId(),
        action,
        entityType,
        actorUserId,
        from,
        to,
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
  }

  private void record(
      UUID organizationId,
      UUID actorUserId,
      String action,
      String entityType,
      UUID entityId,
      Map<String, Object> metadata,
      Instant occurredAt) {
    AuditEvent event = new AuditEvent();
    event.setOrganizationId(organizationId);
    event.setActorUserId(actorUserId);
    event.setAction(action);
    event.setEntityType(entityType);
    event.setEntityId(entityId);
    event.setMetadata(metadata);
    event.setOccurredAt(occurredAt);
    repository.save(event);
  }
}
