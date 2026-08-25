package com.signalops.seed;

import com.signalops.audit.domain.AuditEvent;
import com.signalops.audit.domain.AuditEventRepository;
import com.signalops.auth.app.RefreshTokenService;
import com.signalops.incidents.api.IncidentStatus;
import com.signalops.incidents.api.Severity;
import com.signalops.incidents.domain.Incident;
import com.signalops.incidents.domain.IncidentComment;
import com.signalops.incidents.domain.IncidentCommentRepository;
import com.signalops.incidents.domain.IncidentPublicUpdate;
import com.signalops.incidents.domain.IncidentPublicUpdateRepository;
import com.signalops.incidents.domain.IncidentRepository;
import com.signalops.incidents.domain.IncidentResponder;
import com.signalops.incidents.domain.IncidentResponderRepository;
import com.signalops.incidents.domain.IncidentServiceLink;
import com.signalops.incidents.domain.IncidentServiceLinkRepository;
import com.signalops.organizations.domain.Membership;
import com.signalops.organizations.domain.MembershipRepository;
import com.signalops.organizations.domain.MembershipStatus;
import com.signalops.organizations.domain.Organization;
import com.signalops.organizations.domain.OrganizationRepository;
import com.signalops.servicecatalog.domain.ServiceEntity;
import com.signalops.servicecatalog.domain.ServiceRepository;
import com.signalops.shared.domain.Role;
import com.signalops.timeline.domain.TimelineEventEntity;
import com.signalops.timeline.domain.TimelineEventRepository;
import com.signalops.users.domain.User;
import com.signalops.users.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data for local development and the public demo deployment. Runs ONLY with the `demo`
 * profile (ADR-0005) and is idempotent: it exits when the demo organization already exists.
 * All demo accounts share the documented throwaway password.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

  public static final String DEMO_PASSWORD = "Demo1234!";

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final MembershipRepository membershipRepository;
  private final ServiceRepository serviceRepository;
  private final IncidentRepository incidentRepository;
  private final IncidentServiceLinkRepository serviceLinkRepository;
  private final IncidentResponderRepository responderRepository;
  private final IncidentCommentRepository commentRepository;
  private final IncidentPublicUpdateRepository publicUpdateRepository;
  private final TimelineEventRepository timelineRepository;
  private final AuditEventRepository auditRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  @Override
  @Transactional
  public void run(String... args) {
    if (organizationRepository.findBySlug("northstar-cloud").isPresent()) {
      log.info("Demo data already present; skipping seed.");
      return;
    }
    log.info("Seeding demo data...");
    Instant now = Instant.now(clock);

    User ahmed = user("owner@demo.signalops.io", "Ahmed Tamer");
    User elena = user("admin@demo.signalops.io", "Elena Rodriguez");
    User sara = user("commander@demo.signalops.io", "Sara Ali");
    User omar = user("responder@demo.signalops.io", "Omar Hassan");
    User layla = user("viewer@demo.signalops.io", "Layla Chen");

    Organization northstar = organization("Northstar Cloud", "northstar-cloud");
    membership(northstar, ahmed, Role.OWNER);
    membership(northstar, elena, Role.ADMIN);
    membership(northstar, sara, Role.INCIDENT_COMMANDER);
    membership(northstar, omar, Role.RESPONDER);
    membership(northstar, layla, Role.VIEWER);

    ServiceEntity payments = service(northstar, "Payment Gateway API", "payment-gateway-api", "Card and wallet charge processing.", "Payments");
    ServiceEntity auth = service(northstar, "Authentication", "authentication", "Login, sessions, token issuance.", "Identity");
    ServiceEntity checkout = service(northstar, "Checkout Web", "checkout-web", "Customer-facing checkout.", "Frontend Platform");
    ServiceEntity orders = service(northstar, "Orders API", "orders-api", "Order placement and fulfillment.", "Commerce");
    ServiceEntity notifications = service(northstar, "Notifications", "notifications", "Email and push delivery.", "Platform", false);
    ServiceEntity search = service(northstar, "Search Cluster", "search-cluster", "Product search infrastructure.", "Platform", false);

    // INC-1042: active CRITICAL incident under investigation (matches the UI reference data).
    Incident inc1042 =
        incident(
            northstar,
            "INC-1042",
            "Payment transactions are failing",
            "Charge requests return 500s at ~40% error rate in US-East. Began after deploy #482.",
            Severity.CRITICAL,
            IncidentStatus.INVESTIGATING,
            ahmed,
            ahmed,
            now.minus(42, ChronoUnit.MINUTES),
            now.minus(2, ChronoUnit.MINUTES),
            7,
            now.minus(38, ChronoUnit.MINUTES),
            null,
            null,
            null);
    link(inc1042, payments);
    link(inc1042, checkout);
    respond(inc1042, sara, ahmed, now.minus(37, ChronoUnit.MINUTES));
    respond(inc1042, omar, ahmed, now.minus(36, ChronoUnit.MINUTES));
    timeline(inc1042, ahmed, "INCIDENT_CREATED", Map.of("key", "INC-1042"), now.minus(42, ChronoUnit.MINUTES));
    timeline(inc1042, ahmed, "SEVERITY_CHANGED", Map.of("from", "HIGH", "to", "CRITICAL"), now.minus(35, ChronoUnit.MINUTES));
    timeline(inc1042, ahmed, "RESPONDER_ASSIGNED", Map.of("userId", sara.getId().toString()), now.minus(37, ChronoUnit.MINUTES));
    timeline(inc1042, ahmed, "STATUS_CHANGED", Map.of("from", "DETECTED", "to", "INVESTIGATING"), now.minus(38, ChronoUnit.MINUTES));
    comment(inc1042, sara, "Charge failures isolated to card-not-present flows; wallet payments unaffected.", now.minus(20, ChronoUnit.MINUTES));
    comment(inc1042, omar, "Rollback rehearsed in staging; watching error rate before touching prod.", now.minus(9, ChronoUnit.MINUTES));
    publicUpdate(inc1042, ahmed, "We are investigating an issue causing payment failures across all regions. Engineering teams are engaged.", IncidentStatus.INVESTIGATING, Severity.CRITICAL, now.minus(6, ChronoUnit.MINUTES));

    // INC-1039: resolved CRITICAL with full history.
    Instant resolvedAt = now.minus(2, ChronoUnit.DAYS);
    Incident inc1039 =
        incident(
            northstar,
            "INC-1039",
            "Checkout total outage during peak hours",
            "Checkout unavailable for about four hours. Root cause: connection pool exhaustion after a config push.",
            Severity.CRITICAL,
            IncidentStatus.RESOLVED,
            sara,
            elena,
            now.minus(3110, ChronoUnit.MINUTES),
            resolvedAt,
            9,
            now.minus(2870, ChronoUnit.MINUTES),
            resolvedAt,
            15000L,
            sara.getId());
    link(inc1039, checkout);
    respond(inc1039, omar, elena, now.minus(3000, ChronoUnit.MINUTES));
    timeline(inc1039, elena, "INCIDENT_CREATED", Map.of("key", "INC-1039"), now.minus(3110, ChronoUnit.MINUTES));
    timeline(inc1039, sara, "INCIDENT_RESOLVED", Map.of("body", "Rolled back config; added saturation alerts."), resolvedAt);
    publicUpdate(inc1039, sara, "The issue has been identified and a fix deployed. Service has returned to normal operations.", IncidentStatus.RESOLVED, Severity.CRITICAL, resolvedAt);

    // INC-1038: LOW detected, no commander yet.
    Incident inc1038 =
        incident(
            northstar,
            "INC-1038",
            "Notification digest deliveries delayed",
            "Daily digests arriving up to 90 minutes late; provider queue depth elevated.",
            Severity.LOW,
            IncidentStatus.DETECTED,
            null,
            omar,
            now.minus(2, ChronoUnit.HOURS),
            now.minus(2, ChronoUnit.HOURS),
            1,
            null,
            null,
            null,
            null);
    link(inc1038, notifications);

    // INC-1041: HIGH identified.
    Incident inc1041 =
        incident(
            northstar,
            "INC-1041",
            "Elevated latency on Orders API",
            "p99 latency climbed from 180ms to 1.4s; missing index on fulfillment queue.",
            Severity.HIGH,
            IncidentStatus.IDENTIFIED,
            elena,
            elena,
            now.minus(4, ChronoUnit.HOURS),
            now.minus(25, ChronoUnit.MINUTES),
            3,
            now.minus(3, ChronoUnit.HOURS),
            null,
            null,
            null);
    link(inc1041, orders);
    respond(inc1041, omar, elena, now.minus(3, ChronoUnit.HOURS));
    timeline(inc1041, elena, "INCIDENT_CREATED", Map.of("key", "INC-1041"), now.minus(4, ChronoUnit.HOURS));
    timeline(inc1041, elena, "STATUS_CHANGED", Map.of("from", "INVESTIGATING", "to", "IDENTIFIED"), now.minus(30, ChronoUnit.MINUTES));

    // INC-1040: MEDIUM monitoring.
    Incident inc1040 =
        incident(
            northstar,
            "INC-1040",
            "Search cluster replica sync lag",
            "Search results up to 15 minutes stale during nightly reindex.",
            Severity.MEDIUM,
            IncidentStatus.MONITORING,
            sara,
            sara,
            now.minus(10, ChronoUnit.HOURS),
            now.minus(48, ChronoUnit.MINUTES),
            5,
            now.minus(9, ChronoUnit.HOURS),
            null,
            null,
            null);
    link(inc1040, search);

    // Archived example (R6/R23).
    Incident inc1035 =
        incident(
            northstar,
            "INC-1035",
            "Legacy billing webhook flapping",
            "Superseded by v2 integration; archived after resolution.",
            Severity.MEDIUM,
            IncidentStatus.RESOLVED,
            elena,
            elena,
            now.minus(9, ChronoUnit.DAYS),
            now.minus(7, ChronoUnit.DAYS),
            6,
            now.minus(9, ChronoUnit.DAYS),
            now.minus(8, ChronoUnit.DAYS),
            86400L,
            elena.getId());
    inc1035.setArchivedAt(now.minus(7, ChronoUnit.DAYS));
    link(inc1035, payments);

    audit(northstar, ahmed, "USER_INVITED", "Membership", layla.getId(), Map.of("email", layla.getEmail(), "role", "VIEWER"), now.minus(60, ChronoUnit.DAYS));
    audit(northstar, ahmed, "MEMBER_ROLE_CHANGED", "Membership", membershipId(northstar, sara), Map.of("from", "RESPONDER", "to", "INCIDENT_COMMANDER"), now.minus(45, ChronoUnit.DAYS));
    audit(northstar, elena, "SERVICE_UPDATED", "Service", payments.getId(), Map.of("name", "Payment Gateway API"), now.minus(12, ChronoUnit.DAYS));

    log.info("Demo data seeded: org 'northstar-cloud', 5 users, 6 services, 5 incidents.");
    log.info("Demo accounts (password '{}'): owner@ / admin@ / commander@ / responder@ / viewer@demo.signalops.io", DEMO_PASSWORD);
  }

  private UUID membershipId(Organization org, User user) {
    return membershipRepository
        .findByOrganizationIdAndUserId(org.getId(), user.getId())
        .map(Membership::getId)
        .orElse(null);
  }

  private User user(String email, String fullName) {
    User user = new User();
    user.setEmail(email);
    user.setFullName(fullName);
    user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
    user.setActive(true);
    return userRepository.save(user);
  }

  private Organization organization(String name, String slug) {
    Organization organization = new Organization();
    organization.setName(name);
    organization.setSlug(slug);
    return organizationRepository.save(organization);
  }

  private void membership(Organization org, User user, Role role) {
    Membership membership = new Membership();
    membership.setOrganizationId(org.getId());
    membership.setUserId(user.getId());
    membership.setRole(role);
    membership.setStatus(MembershipStatus.ACTIVE);
    membershipRepository.save(membership);
  }

  private ServiceEntity service(Organization org, String name, String slug, String description, String team) {
    return service(org, name, slug, description, team, true);
  }

  private ServiceEntity service(Organization org, String name, String slug, String description, String team, boolean isPublic) {
    ServiceEntity service = new ServiceEntity();
    service.setOrganizationId(org.getId());
    service.setName(name);
    service.setSlug(slug);
    service.setDescription(description);
    service.setOwningTeam(team);
    service.setPublic(isPublic);
    return serviceRepository.save(service);
  }

  private Incident incident(
      Organization org,
      String key,
      String title,
      String description,
      Severity severity,
      IncidentStatus status,
      User commander,
      User creator,
      Instant createdAt,
      Instant updatedAt,
      long version,
      Instant acknowledgedAt,
      Instant resolvedAt,
      Long durationSeconds,
      UUID resolverUserId) {
    Incident incident = new Incident();
    incident.setOrganizationId(org.getId());
    incident.setKey(key);
    incident.setTitle(title);
    incident.setDescription(description);
    incident.setSeverity(severity);
    incident.setStatus(status);
    incident.setCommanderUserId(commander == null ? null : commander.getId());
    incident.setCreatedByUserId(creator == null ? commander.getId() : creator.getId());
    incident.setAcknowledgedAt(acknowledgedAt);
    incident.setResolvedAt(resolvedAt);
    incident.setDurationSeconds(durationSeconds);
    incident.setResolverUserId(resolverUserId);
    if (durationSeconds != null && resolvedAt != null) {
      incident.setResolutionSummary("Rolled back the failing change; verified recovery in dashboards.");
    }
    incident.setVersion(version);
    incident.setCreatedAt(createdAt);
    incident.setUpdatedAt(updatedAt);
    return incidentRepository.save(incident);
  }

  private void link(Incident incident, ServiceEntity service) {
    IncidentServiceLink link = new IncidentServiceLink();
    link.setIncidentId(incident.getId());
    link.setServiceId(service.getId());
    link.setOrganizationId(incident.getOrganizationId());
    serviceLinkRepository.save(link);
  }

  private void respond(Incident incident, User responder, User assigner, Instant at) {
    IncidentResponder entity = new IncidentResponder();
    entity.setIncidentId(incident.getId());
    entity.setUserId(responder.getId());
    entity.setOrganizationId(incident.getOrganizationId());
    entity.setAssignedByUserId(assigner.getId());
    entity.setAssignedAt(at);
    responderRepository.save(entity);
  }

  private void timeline(Incident incident, User actor, String type, Map<String, Object> payload, Instant at) {
    TimelineEventEntity event = new TimelineEventEntity();
    event.setOrganizationId(incident.getOrganizationId());
    event.setIncidentId(incident.getId());
    event.setType(type);
    event.setActorUserId(actor == null ? null : actor.getId());
    event.setPayload(payload);
    event.setOccurredAt(at);
    timelineRepository.save(event);
  }

  private void comment(Incident incident, User author, String body, Instant at) {
    IncidentComment comment = new IncidentComment();
    comment.setOrganizationId(incident.getOrganizationId());
    comment.setIncidentId(incident.getId());
    comment.setAuthorUserId(author.getId());
    comment.setBody(body);
    comment.setCreatedAt(at);
    comment.setUpdatedAt(at);
    commentRepository.save(comment);
  }

  private void publicUpdate(Incident incident, User author, String message, IncidentStatus status, Severity severity, Instant at) {
    IncidentPublicUpdate update = new IncidentPublicUpdate();
    update.setOrganizationId(incident.getOrganizationId());
    update.setIncidentId(incident.getId());
    update.setAuthorUserId(author.getId());
    update.setMessage(message);
    update.setStatusAtPublish(status);
    update.setSeverityAtPublish(severity);
    update.setPublishedAt(at);
    publicUpdateRepository.save(update);
  }

  private void audit(Organization org, User actor, String action, String entityType, UUID entityId, Map<String, Object> metadata, Instant at) {
    AuditEvent event = new AuditEvent();
    event.setOrganizationId(org.getId());
    event.setActorUserId(actor.getId());
    event.setAction(action);
    event.setEntityType(entityType);
    event.setEntityId(entityId);
    event.setMetadata(metadata);
    event.setOccurredAt(at);
    auditRepository.save(event);
  }
}
