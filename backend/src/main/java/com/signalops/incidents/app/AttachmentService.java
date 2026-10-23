package com.signalops.incidents.app;

import com.signalops.incidents.api.IncidentEvent;
import com.signalops.incidents.api.IncidentEvent.IncidentEventType;
import com.signalops.incidents.domain.Attachment;
import com.signalops.incidents.domain.AttachmentRepository;
import com.signalops.incidents.domain.Incident;
import com.signalops.incidents.domain.IncidentRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AttachmentService {

  public static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;

  private final AttachmentRepository attachmentRepository;
  private final IncidentRepository incidentRepository;
  private final IncidentAccessPolicy accessPolicy;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public record AttachmentView(
      UUID id, String filename, String contentType, long sizeBytes, Instant createdAt) {}

  public record AttachmentDownload(
      String filename, String contentType, byte[] content) {}

  @Transactional
  public AttachmentView upload(UUID incidentId, MultipartFile file) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    accessPolicy.requireOperate(context, incident, "upload attachments to");
    if (file == null || file.isEmpty()) {
      throw ApiException.badRequest("VALIDATION_FAILED", "Choose a file to upload.");
    }
    if (file.getSize() > MAX_SIZE_BYTES) {
      throw ApiException.badRequest("VALIDATION_FAILED", "Attachments are limited to 5 MB.");
    }
    try {
      Attachment attachment = new Attachment();
      attachment.setOrganizationId(context.organizationId());
      attachment.setIncidentId(incidentId);
      attachment.setFilename(sanitize(file.getOriginalFilename()));
      attachment.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
      attachment.setSizeBytes(file.getSize());
      attachment.setContent(file.getBytes());
      attachment.setUploadedByUserId(context.userId());
      attachment = attachmentRepository.save(attachment);
      publish(incident, context.userId(), attachment);
      return toView(attachment);
    } catch (java.io.IOException ex) {
      throw new ApiException("VALIDATION_FAILED", "The file could not be read.", 400);
    }
  }

  @Transactional(readOnly = true)
  public List<AttachmentView> list(UUID incidentId) {
    var context = TenantContext.require();
    requireExists(incidentId, context.organizationId());
    return attachmentRepository
        .findByIncidentIdAndOrganizationIdOrderByCreatedAtDesc(incidentId, context.organizationId())
        .stream()
        .map(AttachmentService::toView)
        .toList();
  }

  @Transactional(readOnly = true)
  public AttachmentDownload download(UUID incidentId, UUID attachmentId) {
    var context = TenantContext.require();
    requireExists(incidentId, context.organizationId());
    Attachment attachment =
        attachmentRepository
            .findByIdAndIncidentIdAndOrganizationId(attachmentId, incidentId, context.organizationId())
            .orElseThrow(() -> ApiException.notFound("Attachment not found."));
    return new AttachmentDownload(
        attachment.getFilename(), attachment.getContentType(), attachment.getContent());
  }

  private void publish(Incident incident, UUID actorUserId, Attachment attachment) {
    events.publishEvent(
        new IncidentEvent(
            IncidentEventType.ATTACHMENT_ADDED,
            incident.getOrganizationId(),
            incident.getId(),
            actorUserId,
            Map.of("filename", attachment.getFilename(), "body", "Attached " + attachment.getFilename()),
            Instant.now(clock)));
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

  private String sanitize(String filename) {
    String name = filename == null || filename.isBlank() ? "attachment" : filename;
    return name.replaceAll("[\\\\/\\r\\n]", "_");
  }

  private static AttachmentView toView(Attachment attachment) {
    return new AttachmentView(
        attachment.getId(),
        attachment.getFilename(),
        attachment.getContentType(),
        attachment.getSizeBytes(),
        attachment.getCreatedAt());
  }
}
