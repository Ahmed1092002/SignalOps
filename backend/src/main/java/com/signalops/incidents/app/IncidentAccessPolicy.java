package com.signalops.incidents.app;

import com.signalops.incidents.domain.Incident;
import com.signalops.incidents.domain.IncidentResponderRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.domain.Role;
import com.signalops.shared.security.TenantContextValue;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The permission matrix for incident-scoped operations (07-security §3). Every incident
 * mutation funnels through here; services never compare raw role strings.
 */
@Component
@RequiredArgsConstructor
public class IncidentAccessPolicy {

  private final IncidentResponderRepository responderRepository;

  public void requireCreate(TenantContextValue context) {
    if (context.isAdminLike() || context.role() == Role.INCIDENT_COMMANDER) {
      return;
    }
    throw forbidden(context, "create incidents");
  }

  public void requireOperate(TenantContextValue context, Incident incident, String action) {
    if (context.isAdminLike()) {
      return;
    }
    if (context.role() == Role.INCIDENT_COMMANDER && isCommander(context, incident)) {
      return;
    }
    throw forbidden(context, action);
  }

  public void requireComment(TenantContextValue context, Incident incident) {
    if (context.isAdminLike()) {
      return;
    }
    if (context.role() == Role.INCIDENT_COMMANDER && isCommander(context, incident)) {
      return;
    }
    if (context.role() == Role.RESPONDER && isResponder(context, incident)) {
      return;
    }
    throw forbidden(context, "comment on this incident");
  }

  public boolean isCommander(TenantContextValue context, Incident incident) {
    return incident.getCommanderUserId() != null
        && incident.getCommanderUserId().equals(context.userId());
  }

  public boolean isResponder(TenantContextValue context, Incident incident) {
    return responderRepository
        .findByIncidentIdAndUserIdAndOrganizationId(
            incident.getId(), context.userId(), incident.getOrganizationId())
        .isPresent();
  }

  public void requireSameOrganization(TenantContextValue context, Incident incident) {
    if (!context.organizationId().equals(incident.getOrganizationId())) {
      throw ApiException.notFound("Incident not found.");
    }
  }

  private ApiException forbidden(TenantContextValue context, String action) {
    if (context.role() == Role.VIEWER) {
      return ApiException.forbidden("Your role has read-only access.");
    }
    if (context.role() == Role.INCIDENT_COMMANDER) {
      return ApiException.forbidden(
          "Incident Commanders can " + action.replace("create", "create") + " only incidents they command.");
    }
    if (context.role() == Role.RESPONDER) {
      return ApiException.forbidden(
          "Responders can act only on incidents they are assigned to.");
    }
    return ApiException.forbidden("You do not have permission to " + action + ".");
  }
}
