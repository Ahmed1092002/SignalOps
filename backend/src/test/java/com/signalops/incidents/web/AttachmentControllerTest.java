package com.signalops.incidents.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.signalops.incidents.app.AttachmentService;
import com.signalops.incidents.app.AttachmentService.AttachmentDownload;
import com.signalops.shared.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class AttachmentControllerTest {

  private AttachmentService attachmentService;
  private AttachmentController controller;

  @BeforeEach
  void setUp() {
    attachmentService = mock(AttachmentService.class);
    controller = new AttachmentController(attachmentService);
  }

  @Test
  void downloadSetsContentDispositionAndMediaType() throws Exception {
    UUID incidentId = UUID.randomUUID();
    UUID attachmentId = UUID.randomUUID();
    byte[] content = "incident report file content".getBytes(StandardCharsets.UTF_8);
    String filename = "incident-report.pdf";

    when(attachmentService.download(incidentId, attachmentId))
        .thenReturn(new AttachmentDownload(filename, "application/pdf", content));

    UserPrincipal principal = new UserPrincipal(UUID.randomUUID());
    ResponseEntity<Resource> response = controller.download(principal, incidentId, attachmentId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
    assertThat(contentDisposition).isNotNull().startsWith("attachment");
    assertThat(ContentDisposition.parse(contentDisposition).getFilename()).isEqualTo(filename);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getContentAsByteArray()).isEqualTo(content);
  }

  @Test
  void downloadFallsBackToOctetStreamWhenContentTypeIsInvalid() throws Exception {
    UUID incidentId = UUID.randomUUID();
    UUID attachmentId = UUID.randomUUID();
    byte[] content = new byte[] {1, 2, 3};

    when(attachmentService.download(incidentId, attachmentId))
        .thenReturn(new AttachmentDownload("data.bin", "invalid-content-type", content));

    UserPrincipal principal = new UserPrincipal(UUID.randomUUID());
    ResponseEntity<Resource> response = controller.download(principal, incidentId, attachmentId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
  }
}
