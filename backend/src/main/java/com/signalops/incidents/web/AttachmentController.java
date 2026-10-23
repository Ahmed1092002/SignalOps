package com.signalops.incidents.web;

import com.signalops.incidents.app.AttachmentService;
import com.signalops.shared.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

  private final AttachmentService attachmentService;

  public record AttachmentView(
      UUID id, String filename, String contentType, long sizeBytes, java.time.Instant createdAt) {}

  @PostMapping
  public ResponseEntity<AttachmentView> upload(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID incidentId,
      @RequestPart("file") MultipartFile file) {
    var view = attachmentService.upload(incidentId, file);
    return ResponseEntity.status(201).body(new AttachmentView(
        view.id(), view.filename(), view.contentType(), view.sizeBytes(), view.createdAt()));
  }

  @GetMapping
  public List<AttachmentView> list(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID incidentId) {
    return attachmentService.list(incidentId).stream()
        .map(view -> new AttachmentView(
            view.id(), view.filename(), view.contentType(), view.sizeBytes(), view.createdAt()))
        .toList();
  }

  @GetMapping("/{attachmentId}/download")
  public ResponseEntity<Resource> download(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID incidentId,
      @PathVariable UUID attachmentId) {
    var attachment = attachmentService.download(incidentId, attachmentId);
    Resource resource = new ByteArrayResource(attachment.content());
    MediaType mediaType;
    try {
      mediaType = attachment.contentType() != null
          ? MediaType.parseMediaType(attachment.contentType())
          : MediaType.APPLICATION_OCTET_STREAM;
    } catch (Exception ex) {
      mediaType = MediaType.APPLICATION_OCTET_STREAM;
    }
    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(attachment.filename(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(resource);
  }
}
