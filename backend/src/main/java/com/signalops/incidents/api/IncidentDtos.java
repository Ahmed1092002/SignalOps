package com.signalops.incidents.api;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class IncidentDtos {

  public record CreateIncidentRequest(
      @NotBlank @Size(min = 3, max = 200) String title,
      @Size(max = 10000) String description,
      @NotNull Severity severity,
      @NotNull List<UUID> serviceIds,
      UUID commanderUserId,
      List<UUID> responderUserIds) {}

  public record UpdateIncidentRequest(
      @Size(min = 3, max = 200) String title,
      @Size(max = 10000) String description,
      @NotNull long version) {}

  public record TransitionRequest(
      @NotNull IncidentStatus to,
      @Size(max = 10000) String resolutionSummary,
      @Size(max = 2000) String reason,
      @NotNull long version) {}

  public record SeverityRequest(@NotNull Severity severity, @NotNull long version) {}

  public record CommanderRequest(UUID commanderUserId, @NotNull long version) {}

  public record ResponderRequest(@NotNull UUID responderUserId, @NotNull long version) {}

  public record ArchiveRequest(@NotNull long version) {}

  public record CommentRequest(@NotBlank @Size(min = 1, max = 10000) String body) {}

  public record PublicUpdateRequest(@NotBlank @Size(min = 1, max = 10000) String message) {}

  public record MemberRef(UUID userId, String fullName, String initials) {}

  public record ServiceRef(UUID id, String name, String slug) {}

  public record CommentView(UUID id, MemberRef author, String body, java.time.Instant createdAt) {}

  public record PublicUpdateView(
      UUID id,
      MemberRef author,
      String message,
      IncidentStatus statusAtPublish,
      Severity severityAtPublish,
      java.time.Instant publishedAt) {}

  public record TimelineEntryView(
      UUID id,
      String type,
      MemberRef actor,
      java.time.Instant occurredAt,
      Map<String, Object> payload) {}

  public record IncidentView(
      UUID id,
      String key,
      String title,
      String description,
      Severity severity,
      IncidentStatus status,
      MemberRef commander,
      List<MemberRef> responders,
      List<ServiceRef> services,
      String resolutionSummary,
      java.time.Instant acknowledgedAt,
      java.time.Instant resolvedAt,
      Long durationSeconds,
      java.time.Instant archivedAt,
      java.time.Instant createdAt,
      java.time.Instant updatedAt,
      long version) {}

  public record IncidentDetailView(IncidentView incident, List<TimelineEntryView> timeline) {}

  public record IncidentRowView(
      UUID id,
      String key,
      String title,
      Severity severity,
      IncidentStatus status,
      List<String> serviceNames,
      String commanderName,
      java.time.Instant createdAt,
      java.time.Instant updatedAt,
      Long durationSeconds,
      boolean archived) {}

  private IncidentDtos() {}
}
