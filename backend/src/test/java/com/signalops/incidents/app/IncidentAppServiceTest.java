package com.signalops.incidents.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalops.incidents.api.IncidentDtos.CreateIncidentRequest;
import com.signalops.incidents.api.IncidentDtos.IncidentView;
import com.signalops.incidents.api.IncidentStatus;
import com.signalops.incidents.api.Severity;
import com.signalops.incidents.domain.Incident;
import com.signalops.incidents.domain.IncidentCommentRepository;
import com.signalops.incidents.domain.IncidentKeySequence;
import com.signalops.incidents.domain.IncidentPublicUpdateRepository;
import com.signalops.incidents.domain.IncidentRepository;
import com.signalops.incidents.domain.IncidentResponderRepository;
import com.signalops.incidents.domain.IncidentServiceLinkRepository;
import com.signalops.organizations.api.MembershipDirectory;
import com.signalops.servicecatalog.api.ServiceCatalogApi;
import com.signalops.servicecatalog.api.ServiceReader;
import com.signalops.shared.domain.Role;
import com.signalops.shared.security.TenantContext;
import com.signalops.shared.security.TenantContextValue;
import com.signalops.users.api.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class IncidentAppServiceTest {

  private IncidentRepository incidentRepository;
  private IncidentKeySequence incidentKeySequence;
  private IncidentResponderRepository responderRepository;
  private IncidentServiceLinkRepository serviceLinkRepository;
  private IncidentCommentRepository commentRepository;
  private IncidentPublicUpdateRepository publicUpdateRepository;
  private IncidentAccessPolicy accessPolicy;
  private ServiceCatalogApi serviceCatalogApi;
  private ServiceReader serviceReader;
  private UserDirectory userDirectory;
  private ApplicationEventPublisher events;
  private Clock clock;
  private MembershipDirectory membershipDirectory;
  private com.signalops.incidents.api.TimelineReader timelineReader;

  private IncidentAppService service;

  private final UUID orgId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    incidentRepository = mock(IncidentRepository.class);
    incidentKeySequence = mock(IncidentKeySequence.class);
    responderRepository = mock(IncidentResponderRepository.class);
    serviceLinkRepository = mock(IncidentServiceLinkRepository.class);
    commentRepository = mock(IncidentCommentRepository.class);
    publicUpdateRepository = mock(IncidentPublicUpdateRepository.class);
    accessPolicy = mock(IncidentAccessPolicy.class);
    serviceCatalogApi = mock(ServiceCatalogApi.class);
    serviceReader = mock(ServiceReader.class);
    userDirectory = mock(UserDirectory.class);
    events = mock(ApplicationEventPublisher.class);
    clock = Clock.systemUTC();
    membershipDirectory = mock(MembershipDirectory.class);
    timelineReader = mock(com.signalops.incidents.api.TimelineReader.class);

    service =
        new IncidentAppService(
            incidentRepository,
            incidentKeySequence,
            responderRepository,
            serviceLinkRepository,
            commentRepository,
            publicUpdateRepository,
            accessPolicy,
            serviceCatalogApi,
            serviceReader,
            userDirectory,
            events,
            clock,
            membershipDirectory,
            timelineReader);

    TenantContext.set(
        new TenantContextValue(
            userId, orgId, UUID.randomUUID(), Role.ADMIN, "Admin User"));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void setCommanderWithNullSavesAndFlushesAndReturnsUpdatedVersion() {
    UUID incidentId = UUID.randomUUID();
    Incident incident = new Incident();
    ReflectionTestUtils.setField(incident, "id", incidentId);
    incident.setOrganizationId(orgId);
    incident.setKey("INC-1");
    incident.setTitle("Outage");
    incident.setSeverity(Severity.HIGH);
    incident.setStatus(IncidentStatus.INVESTIGATING);
    incident.setCommanderUserId(UUID.randomUUID());
    incident.setVersion(3L);

    when(incidentRepository.findByIdAndOrganizationId(incidentId, orgId))
        .thenReturn(Optional.of(incident));

    // saveAndFlush simulates Hibernate incrementing the version
    when(incidentRepository.saveAndFlush(any(Incident.class)))
        .thenAnswer(
            inv -> {
              Incident i = inv.getArgument(0);
              i.setVersion(i.getVersion() + 1);
              return i;
            });

    IncidentView view = service.setCommander(incidentId, null, 3L);

    assertThat(incident.getCommanderUserId()).isNull();
    assertThat(view.version()).isEqualTo(4L);
    verify(incidentRepository).saveAndFlush(incident);
  }

  @Test
  void createUsesIncidentKeySequence() {
    UUID serviceId = UUID.randomUUID();
    when(incidentKeySequence.nextNumber(orgId)).thenReturn(7);

    when(incidentRepository.save(any(Incident.class)))
        .thenAnswer(
            inv -> {
              Incident i = inv.getArgument(0);
              ReflectionTestUtils.setField(i, "id", UUID.randomUUID());
              return i;
            });

    CreateIncidentRequest request =
        new CreateIncidentRequest(
            "Service degradations",
            "Slow database calls",
            Severity.LOW,
            List.of(serviceId),
            null,
            List.of());

    IncidentView view = service.create(request);

    assertThat(view.key()).isEqualTo("INC-7");
    verify(incidentKeySequence).nextNumber(orgId);
  }
}
