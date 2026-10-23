package com.signalops.organizations.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalops.organizations.domain.Membership;
import com.signalops.organizations.domain.MembershipRepository;
import com.signalops.organizations.domain.MembershipStatus;
import com.signalops.organizations.domain.Organization;
import com.signalops.organizations.domain.OrganizationRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.domain.Role;
import com.signalops.shared.security.TenantContext;
import com.signalops.shared.security.TenantContextValue;
import com.signalops.users.api.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class MemberServiceTest {

  private OrganizationRepository organizationRepository;
  private MembershipRepository membershipRepository;
  private OrgAccessPolicy accessPolicy;
  private ApplicationEventPublisher events;
  private Clock clock;
  private UserDirectory userDirectory;
  private MemberService memberService;

  private UUID orgId;
  private UUID actorId;

  @BeforeEach
  void setUp() {
    organizationRepository = mock(OrganizationRepository.class);
    membershipRepository = mock(MembershipRepository.class);
    accessPolicy = new OrgAccessPolicy();
    events = mock(ApplicationEventPublisher.class);
    clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);
    userDirectory = mock(UserDirectory.class);

    memberService =
        new MemberService(
            organizationRepository,
            membershipRepository,
            accessPolicy,
            events,
            clock,
            userDirectory);

    orgId = UUID.randomUUID();
    actorId = UUID.randomUUID();
    TenantContext.set(new TenantContextValue(actorId, orgId, UUID.randomUUID(), Role.OWNER, "Actor User"));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private Membership createMembership(UUID memberId, UUID userId, Role role, MembershipStatus status) {
    Membership membership = new Membership();
    ReflectionTestUtils.setField(membership, "id", memberId);
    membership.setOrganizationId(orgId);
    membership.setUserId(userId);
    membership.setRole(role);
    membership.setStatus(status);
    return membership;
  }

  private void mockUser(UUID userId, String email) {
    when(userDirectory.findSummary(userId))
        .thenReturn(Optional.of(new UserDirectory.UserSummary(userId, "Test User", email, true)));
  }

  private Organization mockOrg() {
    Organization org = new Organization();
    ReflectionTestUtils.setField(org, "id", orgId);
    org.setName("Test Org");
    org.setSlug("test-org");
    when(organizationRepository.findByIdWithLock(orgId)).thenReturn(Optional.of(org));
    return org;
  }

  @Test
  void demotingLastActiveOwnerThrowsConflictAndAcquiresLock() {
    UUID memberId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Membership owner = createMembership(memberId, userId, Role.OWNER, MembershipStatus.ACTIVE);
    mockUser(userId, "owner@example.com");
    mockOrg();

    when(membershipRepository.findById(memberId)).thenReturn(Optional.of(owner));
    when(membershipRepository.countActiveOwners(orgId)).thenReturn(1L);

    assertThatThrownBy(() -> memberService.updateMember(memberId, Role.ADMIN, null))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> {
          ApiException apiException = (ApiException) e;
          assertThat(apiException.getStatus()).isEqualTo(409);
          assertThat(apiException.getCode()).isEqualTo(ErrorCodes.CANNOT_REMOVE_LAST_OWNER);
        });

    verify(organizationRepository).findByIdWithLock(orgId);
  }

  @Test
  void demotingOwnerWithMultipleActiveOwnersSucceeds() {
    UUID memberId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Membership owner = createMembership(memberId, userId, Role.OWNER, MembershipStatus.ACTIVE);
    mockUser(userId, "owner@example.com");
    mockOrg();

    when(membershipRepository.findById(memberId)).thenReturn(Optional.of(owner));
    when(membershipRepository.countActiveOwners(orgId)).thenReturn(2L);

    var view = memberService.updateMember(memberId, Role.ADMIN, null);
    assertThat(view.role()).isEqualTo(Role.ADMIN);
    assertThat(owner.getRole()).isEqualTo(Role.ADMIN);
    verify(organizationRepository).findByIdWithLock(orgId);
  }

  @Test
  void disablingLastActiveOwnerThrowsConflict() {
    UUID memberId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Membership owner = createMembership(memberId, userId, Role.OWNER, MembershipStatus.ACTIVE);
    mockUser(userId, "owner@example.com");
    mockOrg();

    when(membershipRepository.findById(memberId)).thenReturn(Optional.of(owner));
    when(membershipRepository.countActiveOwners(orgId)).thenReturn(1L);

    assertThatThrownBy(() -> memberService.updateMember(memberId, null, "DISABLED"))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> {
          ApiException apiException = (ApiException) e;
          assertThat(apiException.getStatus()).isEqualTo(409);
          assertThat(apiException.getCode()).isEqualTo(ErrorCodes.CANNOT_REMOVE_LAST_OWNER);
        });

    verify(organizationRepository).findByIdWithLock(orgId);
  }

  @Test
  void removingLastActiveOwnerThrowsConflict() {
    UUID memberId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Membership owner = createMembership(memberId, userId, Role.OWNER, MembershipStatus.ACTIVE);
    mockUser(userId, "owner@example.com");
    mockOrg();

    when(membershipRepository.findById(memberId)).thenReturn(Optional.of(owner));
    when(membershipRepository.countActiveOwners(orgId)).thenReturn(1L);

    assertThatThrownBy(() -> memberService.removeMember(memberId))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> {
          ApiException apiException = (ApiException) e;
          assertThat(apiException.getStatus()).isEqualTo(409);
          assertThat(apiException.getCode()).isEqualTo(ErrorCodes.CANNOT_REMOVE_LAST_OWNER);
        });

    verify(organizationRepository).findByIdWithLock(orgId);
    verify(membershipRepository, never()).delete(any());
  }

  @Test
  void modifyingNonOwnerDoesNotAcquireLockOrCheckLastOwner() {
    UUID memberId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Membership responder = createMembership(memberId, userId, Role.RESPONDER, MembershipStatus.ACTIVE);
    mockUser(userId, "responder@example.com");

    when(membershipRepository.findById(memberId)).thenReturn(Optional.of(responder));

    var view = memberService.updateMember(memberId, Role.ADMIN, null);
    assertThat(view.role()).isEqualTo(Role.ADMIN);
    verify(organizationRepository, never()).findByIdWithLock(any());
    verify(membershipRepository, never()).countActiveOwners(any());
  }
}
