package com.signalops.incidents.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.signalops.incidents.domain.Attachment;
import com.signalops.incidents.domain.AttachmentRepository;
import com.signalops.incidents.domain.IncidentRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.domain.Role;
import com.signalops.shared.security.TenantContext;
import com.signalops.shared.security.TenantContextValue;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AttachmentServiceTest {

  private AttachmentRepository attachmentRepository;
  private IncidentRepository incidentRepository;
  private IncidentAccessPolicy accessPolicy;
  private ApplicationEventPublisher events;
  private Clock clock;
  private AttachmentService service;

  private final UUID orgId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID incidentA = UUID.randomUUID();
  private final UUID incidentB = UUID.randomUUID();
  private final UUID attachmentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    attachmentRepository = mock(AttachmentRepository.class);
    incidentRepository = mock(IncidentRepository.class);
    accessPolicy = mock(IncidentAccessPolicy.class);
    events = mock(ApplicationEventPublisher.class);
    clock = Clock.systemUTC();

    service =
        new AttachmentService(
            attachmentRepository, incidentRepository, accessPolicy, events, clock);

    TenantContext.set(
        new TenantContextValue(
            userId, orgId, UUID.randomUUID(), Role.ADMIN, "Admin User"));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void downloadFailsWithNotFoundWhenAttachmentBelongsToAnotherIncident() {
    // Both incidents exist in the active organization
    when(incidentRepository.existsByIdAndOrganizationId(incidentA, orgId)).thenReturn(true);
    when(incidentRepository.existsByIdAndOrganizationId(incidentB, orgId)).thenReturn(true);

    // Attachment exists for incidentA, but not incidentB
    when(attachmentRepository.findByIdAndIncidentIdAndOrganizationId(attachmentId, incidentB, orgId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.download(incidentB, attachmentId))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(404));
  }

  @Test
  void downloadSucceedsWhenAttachmentMatchesIncidentAndOrganization() {
    when(incidentRepository.existsByIdAndOrganizationId(incidentA, orgId)).thenReturn(true);

    Attachment attachment = new Attachment();
    attachment.setFilename("crash.log");
    attachment.setContentType("text/plain");
    attachment.setContent("error stacktrace".getBytes());

    when(attachmentRepository.findByIdAndIncidentIdAndOrganizationId(attachmentId, incidentA, orgId))
        .thenReturn(Optional.of(attachment));

    var download = service.download(incidentA, attachmentId);
    assertThat(download.filename()).isEqualTo("crash.log");
    assertThat(download.contentType()).isEqualTo("text/plain");
    assertThat(download.content()).isEqualTo("error stacktrace".getBytes());
  }
}
