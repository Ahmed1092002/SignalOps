package com.signalops.incidents.app;

import com.signalops.incidents.api.IncidentDtos;
import com.signalops.incidents.api.IncidentDtos.CommentRequest;
import com.signalops.incidents.api.IncidentDtos.CreateIncidentRequest;
import com.signalops.incidents.api.IncidentDtos.IncidentDetailView;
import com.signalops.incidents.api.IncidentDtos.IncidentRowView;
import com.signalops.incidents.api.IncidentDtos.IncidentView;
import com.signalops.incidents.api.IncidentDtos.MemberRef;
import com.signalops.incidents.api.IncidentDtos.PublicUpdateRequest;
import com.signalops.incidents.api.IncidentDtos.ServiceRef;
import com.signalops.incidents.api.IncidentDtos.TransitionRequest;
import com.signalops.incidents.api.IncidentEvent;
import com.signalops.incidents.api.IncidentEvent.IncidentEventType;
import com.signalops.incidents.api.IncidentStatus;
import com.signalops.incidents.api.Severity;
import com.signalops.incidents.domain.Incident;
import com.signalops.incidents.domain.IncidentComment;
import com.signalops.incidents.domain.IncidentCommentRepository;
import com.signalops.incidents.domain.IncidentKeySequence;
import com.signalops.incidents.domain.IncidentPublicUpdate;
import com.signalops.incidents.domain.IncidentPublicUpdateRepository;
import com.signalops.incidents.domain.IncidentRepository;
import com.signalops.incidents.domain.IncidentResponder;
import com.signalops.incidents.domain.IncidentResponderRepository;
import com.signalops.incidents.domain.IncidentServiceLink;
import com.signalops.incidents.domain.IncidentServiceLinkRepository;
import com.signalops.servicecatalog.api.ServiceCatalogApi;
import com.signalops.servicecatalog.api.ServiceReader;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.api.PageResponse;
import com.signalops.shared.security.TenantContext;
import com.signalops.users.api.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentAppService {

  private final IncidentRepository incidentRepository;
  private final IncidentKeySequence incidentKeySequence;
  private final IncidentResponderRepository responderRepository;
  private final IncidentServiceLinkRepository serviceLinkRepository;
  private final IncidentCommentRepository commentRepository;
  private final IncidentPublicUpdateRepository publicUpdateRepository;
  private final IncidentAccessPolicy accessPolicy;
  private final ServiceCatalogApi serviceCatalogApi;
  private final ServiceReader serviceReader;
  private final UserDirectory userDirectory;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final com.signalops.organizations.api.MembershipDirectory membershipDirectory;
  private final com.signalops.incidents.api.TimelineReader timelineReader;

  @Transactional
  public IncidentView create(CreateIncidentRequest request) {
    var context = TenantContext.require();
    accessPolicy.requireCreate(context);

    if (request.serviceIds() == null || request.serviceIds().isEmpty()) {
      throw ApiException.badRequest(
          ErrorCodes.INCIDENT_REQUIRES_SERVICE, "Select at least one affected service.");
    }
    serviceCatalogApi.assertAllExistInOrganization(context.organizationId(), request.serviceIds());
    if (request.severity() == Severity.CRITICAL && request.commanderUserId() == null) {
      throw ApiException.badRequest(
          ErrorCodes.CRITICAL_REQUIRES_COMMANDER,
          "A Critical incident requires an Incident Commander.");
    }
    UUID commanderUserId = request.commanderUserId();
    if (commanderUserId != null) {
      assertEligibleActiveMember(context.organizationId(), commanderUserId);
    }
    List<UUID> responderUserIds =
        request.responderUserIds() == null ? List.of() : request.responderUserIds();
    for (UUID responderUserId : responderUserIds) {
      assertActiveMember(context.organizationId(), responderUserId);
    }

    Incident incident = new Incident();
    incident.setOrganizationId(context.organizationId());
    incident.setKey(nextKey(context.organizationId()));
    incident.setTitle(request.title().trim());
    incident.setDescription(request.description() == null ? "" : request.description().trim());
    incident.setSeverity(request.severity());
    incident.setStatus(IncidentStatus.DETECTED);
    incident.setCommanderUserId(commanderUserId);
    incident.setCreatedByUserId(context.userId());
    incidentRepository.save(incident);

    for (UUID serviceId : request.serviceIds()) {
      IncidentServiceLink link = new IncidentServiceLink();
      link.setIncidentId(incident.getId());
      link.setServiceId(serviceId);
      link.setOrganizationId(context.organizationId());
      serviceLinkRepository.save(link);
    }
    for (UUID responderUserId : responderUserIds) {
      IncidentResponder responder = new IncidentResponder();
      responder.setIncidentId(incident.getId());
      responder.setUserId(responderUserId);
      responder.setOrganizationId(context.organizationId());
      responder.setAssignedByUserId(context.userId());
      responderRepository.save(responder);
    }

    publish(IncidentEventType.CREATED, incident, context.userId(), Map.of("key", incident.getKey()));
    for (UUID responderUserId : responderUserIds) {
      publish(
          IncidentEventType.RESPONDER_ASSIGNED,
          incident,
          context.userId(),
          Map.of("userId", responderUserId.toString()));
    }
    return toView(incident);
  }

  @Transactional(readOnly = true)
  public IncidentDetailView get(UUID incidentId) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    return new IncidentDetailView(toView(incident), timelineOf(incident));
  }

  @Transactional(readOnly = true)
  public PageResponse<IncidentRowView> list(
      String q,
      IncidentStatus status,
      Severity severity,
      UUID serviceId,
      UUID responderUserId,
      java.time.Instant createdFrom,
      java.time.Instant createdTo,
      boolean archived,
      int page,
      int size) {
    var context = TenantContext.require();
    PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    Page<Incident> result =
        incidentRepository.search(
            context.organizationId(),
            archived,
            status == null ? null : status.name(),
            severity == null ? null : severity.name(),
            (q == null || q.isBlank()) ? null : q.trim(),
            createdFrom,
            createdTo,
            responderUserId,
            serviceId,
            pageRequest);

    List<UUID> ids = result.getContent().stream().map(Incident::getId).toList();
    Map<UUID, List<String>> serviceNames = serviceReader.namesByIncident(context.organizationId(), ids);
    Map<UUID, String> commanderNames = commanderNames(result.getContent());

    List<IncidentRowView> rows =
        result.getContent().stream()
            .map(
                incident ->
                    new IncidentRowView(
                        incident.getId(),
                        incident.getKey(),
                        incident.getTitle(),
                        incident.getSeverity(),
                        incident.getStatus(),
                        serviceNames.getOrDefault(incident.getId(), List.of()),
                        commanderNames.get(incident.getId()),
                        incident.getCreatedAt(),
                        incident.getUpdatedAt(),
                        incident.getDurationSeconds(),
                        incident.isArchived()))
            .toList();
    return new PageResponse<>(
        rows, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
  }

  @Transactional
  public IncidentView update(UUID incidentId, String title, String description, long expectedVersion) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    accessPolicy.requireOperate(context, incident, "edit");
    assertFresh(incident, expectedVersion);
    incident.editDetails(title, description);
    incident = incidentRepository.saveAndFlush(incident);
    publish(IncidentEventType.EDITED, incident, context.userId(), Map.of());
    return toView(incident);
  }

  @Transactional
  public IncidentView transition(UUID incidentId, TransitionRequest request) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    accessPolicy.requireOperate(context, incident, "transition");
    assertFresh(incident, request.version());
    IncidentStatus from = incident.getStatus();
    incident.applyTransition(request.to(), request.resolutionSummary(), request.reason(), context.userId(), Instant.now(clock));
    incident = incidentRepository.saveAndFlush(incident);

    if (request.to() == IncidentStatus.RESOLVED) {
      publish(
          IncidentEventType.RESOLVED,
          incident,
          context.userId(),
          Map.of("from", from.name(), "to", request.to().name(), "body", nz(incident.getResolutionSummary())));
    } else if (from == IncidentStatus.RESOLVED) {
      publish(
          IncidentEventType.REOPENED,
          incident,
          context.userId(),
          Map.of("from", from.name(), "to", request.to().name(), "body", nz(request.reason())));
    } else {
      publish(
          IncidentEventType.STATUS_CHANGED,
          incident,
          context.userId(),
          Map.of("from", from.name(), "to", request.to().name()));
    }
    return toView(incident);
  }

  @Transactional
  public IncidentView changeSeverity(UUID incidentId, Severity severity, long expectedVersion) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    accessPolicy.requireOperate(context, incident, "change severity on");
    assertFresh(incident, expectedVersion);
    Severity previous = incident.getSeverity();
    incident.changeSeverity(severity);
    incident = incidentRepository.saveAndFlush(incident);
    publish(
        IncidentEventType.SEVERITY_CHANGED,
        incident,
        context.userId(),
        Map.of("from", previous.name(), "to", severity.name()));
    return toView(incident);
  }

  @Transactional
  public IncidentView setCommander(UUID incidentId, UUID commanderUserId, long expectedVersion) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    accessPolicy.requireOperate(context, incident, "assign the commander on");
    assertFresh(incident, expectedVersion);
    if (commanderUserId == null) {
      incident.removeCommander();
      incident = incidentRepository.saveAndFlush(incident);
      publish(
          IncidentEventType.COMMANDER_ASSIGNED, incident, context.userId(), Map.of("removed", true));
    } else {
      assertEligibleActiveMember(context.organizationId(), commanderUserId);
      incident.assignCommander(commanderUserId);
      incident = incidentRepository.saveAndFlush(incident);
      publish(
          IncidentEventType.COMMANDER_ASSIGNED,
          incident,
          context.userId(),
          Map.of("userId", commanderUserId.toString()));
    }
    return toView(incident);
  }

  @Transactional
  public IncidentView addResponder(UUID incidentId, UUID responderUserId, long expectedVersion) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    accessPolicy.requireOperate(context, incident, "assign responders on");
    assertFresh(incident, expectedVersion);
    assertActiveMember(context.organizationId(), responderUserId);
    if (responderRepository
        .findByIncidentIdAndUserIdAndOrganizationId(incidentId, responderUserId, context.organizationId())
        .isEmpty()) {
      IncidentResponder responder = new IncidentResponder();
      responder.setIncidentId(incidentId);
      responder.setUserId(responderUserId);
      responder.setOrganizationId(context.organizationId());
      responder.setAssignedByUserId(context.userId());
      responderRepository.save(responder);
      publish(
          IncidentEventType.RESPONDER_ASSIGNED,
          incident,
          context.userId(),
          Map.of("userId", responderUserId.toString()));
    }
    return toView(incident);
  }

  @Transactional
  public IncidentView removeResponder(UUID incidentId, UUID responderUserId, long expectedVersion) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    accessPolicy.requireOperate(context, incident, "remove responders on");
    assertFresh(incident, expectedVersion);
    responderRepository
        .findByIncidentIdAndUserIdAndOrganizationId(incidentId, responderUserId, context.organizationId())
        .ifPresent(responderRepository::delete);
    publish(
        IncidentEventType.RESPONDER_REMOVED,
        incident,
        context.userId(),
        Map.of("userId", responderUserId.toString()));
    return toView(incident);
  }

  @Transactional
  public IncidentDetailView addComment(UUID incidentId, CommentRequest request) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    accessPolicy.requireComment(context, incident);
    IncidentComment comment = new IncidentComment();
    comment.setOrganizationId(context.organizationId());
    comment.setIncidentId(incidentId);
    comment.setAuthorUserId(context.userId());
    comment.setBody(request.body().trim());
    commentRepository.save(comment);
    publish(
        IncidentEventType.COMMENT_ADDED, incident, context.userId(), Map.of("body", comment.getBody()));
    return get(incidentId);
  }

  @Transactional
  public IncidentDetailView publishPublicUpdate(UUID incidentId, PublicUpdateRequest request) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    accessPolicy.requireOperate(context, incident, "publish public updates on");
    IncidentPublicUpdate update = new IncidentPublicUpdate();
    update.setOrganizationId(context.organizationId());
    update.setIncidentId(incidentId);
    update.setAuthorUserId(context.userId());
    update.setMessage(request.message().trim());
    update.setStatusAtPublish(incident.getStatus());
    update.setSeverityAtPublish(incident.getSeverity());
    update.setPublishedAt(Instant.now(clock));
    publicUpdateRepository.save(update);
    publish(
        IncidentEventType.PUBLIC_UPDATE_PUBLISHED,
        incident,
        context.userId(),
        Map.of("body", update.getMessage()));
    return get(incidentId);
  }

  @Transactional(readOnly = true)
  public List<IncidentDtos.CommentView> comments(UUID incidentId) {
    var context = TenantContext.require();
    requireExists(incidentId, context.organizationId());
    return commentRepository
        .findByIncidentIdAndOrganizationIdOrderByCreatedAtDesc(incidentId, context.organizationId())
        .stream()
        .map(this::toCommentView)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<IncidentDtos.PublicUpdateView> publicUpdates(UUID incidentId) {
    var context = TenantContext.require();
    requireExists(incidentId, context.organizationId());
    return publicUpdateRepository
        .findByIncidentIdAndOrganizationIdOrderByPublishedAtDesc(incidentId, context.organizationId())
        .stream()
        .map(this::toPublicUpdateView)
        .toList();
  }

  @Transactional
  public IncidentView archive(UUID incidentId, long expectedVersion) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    accessPolicy.requireOperate(context, incident, "archive");
    assertFresh(incident, expectedVersion);
    incident.archive(Instant.now(clock));
    incident = incidentRepository.saveAndFlush(incident);
    publish(IncidentEventType.ARCHIVED, incident, context.userId(), Map.of());
    return toView(incident);
  }

  private Incident load(UUID incidentId, UUID organizationId) {
    return incidentRepository
        .findByIdAndOrganizationId(incidentId, organizationId)
        .orElseThrow(() -> ApiException.notFound("Incident not found."));
  }

  private void requireExists(UUID incidentId, UUID organizationId) {
    if (!incidentRepository.existsByIdAndOrganizationId(incidentId, organizationId)) {
      throw ApiException.notFound("Incident not found.");
    }
  }

  private void assertFresh(Incident incident, long expectedVersion) {
    if (incident.getVersion() != expectedVersion) {
      throw new ApiException(
          ErrorCodes.STALE_WRITE,
          "This incident was modified by someone else. Reload and try again.",
          409,
          List.of(Map.of("currentVersion", incident.getVersion())));
    }
  }

  private void assertActiveMember(UUID organizationId, UUID userId) {
    var membership = membershipDirectory.findMembership(organizationId, userId);
    if (membership.isEmpty() || !membership.get().active()) {
      throw ApiException.badRequest(
          ErrorCodes.MEMBER_NOT_ACTIVE, "Participants must be active members of this organization.");
    }
    var user = userDirectory.findSummary(userId);
    if (user.isEmpty() || !user.get().active()) {
      throw ApiException.badRequest(ErrorCodes.MEMBER_NOT_ACTIVE, "User not found or inactive.");
    }
  }

  private void assertEligibleActiveMember(UUID organizationId, UUID userId) {
    var user =
        userDirectory
            .findSummary(userId)
            .orElseThrow(() -> ApiException.badRequest(ErrorCodes.MEMBER_NOT_ACTIVE, "User not found."));
    if (!user.active()) {
      throw ApiException.badRequest(ErrorCodes.MEMBER_NOT_ACTIVE, "Commander must be an active member.");
    }
    if (!membershipDirectory.isCommanderEligible(organizationId, userId)) {
      throw ApiException.badRequest(
          ErrorCodes.VALIDATION_FAILED,
          "Commanders must hold the Owner, Admin, or Incident Commander role.");
    }
  }

  private String nextKey(UUID organizationId) {
    int next = incidentKeySequence.nextNumber(organizationId);
    return "INC-" + next;
  }

  private void publish(IncidentEventType type, Incident incident, UUID actorUserId, Map<String, Object> payload) {
    events.publishEvent(
        new IncidentEvent(
            type, incident.getOrganizationId(), incident.getId(), actorUserId, payload, Instant.now(clock)));
  }

  private List<IncidentDtos.TimelineEntryView> timelineOf(Incident incident) {
    return timelineReader
        .findByIncident(incident.getOrganizationId(), incident.getId(), 0, 100)
        .stream()
        .map(
            item ->
                new IncidentDtos.TimelineEntryView(
                    item.id(),
                    item.type(),
                    actorRef(item.actorUserId(), item.actorName()),
                    item.occurredAt(),
                    item.payload()))
        .toList();
  }

  private IncidentView toView(Incident incident) {
    var context = TenantContext.require();
    List<IncidentResponder> responders =
        responderRepository.findByIncidentIdAndOrganizationId(incident.getId(), context.organizationId());
    List<ServiceRef> services =
        serviceLinkRepository.findByIncidentIdAndOrganizationId(incident.getId(), context.organizationId())
            .stream()
            .map(
                link ->
                    serviceReader
                        .summary(context.organizationId(), link.getServiceId())
                        .map(service -> new ServiceRef(service.id(), service.name(), service.slug()))
                        .orElse(new ServiceRef(link.getServiceId(), "", "")))
            .toList();
    return new IncidentView(
        incident.getId(),
        incident.getKey(),
        incident.getTitle(),
        incident.getDescription(),
        incident.getSeverity(),
        incident.getStatus(),
        commanderRef(incident),
        responders.stream()
            .map(responder -> userRef(responder.getUserId()))
            .sorted(Comparator.comparing(ref -> ref.fullName() == null ? "" : ref.fullName()))
            .toList(),
        services,
        incident.getResolutionSummary(),
        incident.getAcknowledgedAt(),
        incident.getResolvedAt(),
        incident.getDurationSeconds(),
        incident.getArchivedAt(),
        incident.getCreatedAt(),
        incident.getUpdatedAt(),
        incident.getVersion());
  }

  private Map<UUID, String> commanderNames(List<Incident> incidents) {
    return incidents.stream()
        .filter(incident -> incident.getCommanderUserId() != null)
        .distinct()
        .collect(
            java.util.stream.Collectors.toMap(
                Incident::getId,
                incident ->
                    userDirectory
                        .findSummary(incident.getCommanderUserId())
                        .map(summary -> summary.fullName())
                        .orElse("")));
  }

  private MemberRef commanderRef(Incident incident) {
    if (incident.getCommanderUserId() == null) {
      return null;
    }
    return userRef(incident.getCommanderUserId());
  }

  private MemberRef userRef(UUID userId) {
    return userDirectory
        .findSummary(userId)
        .map(summary -> new MemberRef(summary.userId(), summary.fullName(), initialsOf(summary.fullName())))
        .orElse(new MemberRef(userId, "Unknown", "?"));
  }

  private MemberRef actorRef(UUID actorUserId, String actorName) {
    if (actorUserId == null) {
      return null;
    }
    return new MemberRef(actorUserId, actorName, initialsOf(actorName));
  }

  private IncidentDtos.CommentView toCommentView(IncidentComment comment) {
    var author = userDirectory.findSummary(comment.getAuthorUserId());
    return new IncidentDtos.CommentView(
        comment.getId(),
        new MemberRef(
            comment.getAuthorUserId(),
            author.map(summary -> summary.fullName()).orElse("Unknown"),
            author.map(summary -> initialsOf(summary.fullName())).orElse("?")),
        comment.getBody(),
        comment.getCreatedAt());
  }

  private IncidentDtos.PublicUpdateView toPublicUpdateView(IncidentPublicUpdate update) {
    var author = userDirectory.findSummary(update.getAuthorUserId());
    return new IncidentDtos.PublicUpdateView(
        update.getId(),
        new MemberRef(
            update.getAuthorUserId(),
            author.map(summary -> summary.fullName()).orElse("Unknown"),
            author.map(summary -> initialsOf(summary.fullName())).orElse("?")),
        update.getMessage(),
        update.getStatusAtPublish(),
        update.getSeverityAtPublish(),
        update.getPublishedAt());
  }

  private String initialsOf(String fullName) {
    if (fullName == null || fullName.isBlank()) {
      return "?";
    }
    String[] parts = fullName.trim().split("\\s+");
    StringBuilder initials = new StringBuilder();
    for (String part : parts) {
      if (!part.isEmpty() && initials.length() < 2) {
        initials.append(Character.toUpperCase(part.charAt(0)));
      }
    }
    return initials.isEmpty() ? "?" : initials.toString();
  }

  private String nz(String value) {
    return value == null ? "" : value;
  }

  private static ApiException stale(long currentVersion) {
    return new ApiException(
        ErrorCodes.STALE_WRITE,
        "This incident was modified by someone else.",
        409,
        List.of(Map.of("currentVersion", currentVersion)));
  }
}
