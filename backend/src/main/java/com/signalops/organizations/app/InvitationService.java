package com.signalops.organizations.app;

import com.signalops.organizations.api.OrganizationDtos.InvitationAcceptRequest;
import com.signalops.organizations.api.OrganizationDtos.InvitationAcceptResponse;
import com.signalops.organizations.api.OrganizationDtos.InvitationCreateRequest;
import com.signalops.organizations.api.OrganizationDtos.InvitationView;
import com.signalops.organizations.api.OrganizationEvents.UserInvited;
import com.signalops.organizations.domain.Invitation;
import com.signalops.organizations.domain.InvitationRepository;
import com.signalops.organizations.domain.Membership;
import com.signalops.organizations.domain.MembershipRepository;
import com.signalops.organizations.domain.MembershipStatus;
import com.signalops.organizations.domain.Organization;
import com.signalops.organizations.domain.OrganizationRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.domain.Role;
import com.signalops.shared.security.TenantContext;
import com.signalops.users.api.UserDirectory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvitationService {

  private final InvitationRepository invitationRepository;
  private final MembershipRepository membershipRepository;
  private final OrganizationRepository organizationRepository;
  private final OrgAccessPolicy accessPolicy;
  private final UserDirectory userDirectory;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public InvitationView create(InvitationCreateRequest request) {
    var context = TenantContext.require();
    accessPolicy.requireAdminLike(context, "invite members");
    if (request.role() == Role.OWNER) {
      throw ApiException.badRequest(
          ErrorCodes.VALIDATION_FAILED, "Owners cannot be invited; transfer ownership instead.");
    }
    String email = request.email().trim();
    userDirectory
        .findSummaryByEmail(email)
        .filter(summary -> membershipRepository.findByOrganizationIdAndUserId(context.organizationId(), summary.userId()).isPresent())
        .ifPresent(
            existing -> {
              throw ApiException.conflict(
                  ErrorCodes.RESOURCE_CONFLICT, "This user is already a member of the organization.");
            });

    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    Invitation invitation = new Invitation();
    invitation.setOrganizationId(context.organizationId());
    invitation.setEmail(email);
    invitation.setRole(request.role());
    invitation.setTokenHash(hash(rawToken));
    invitation.setInvitedByUserId(context.userId());
    invitation.setExpiresAt(Instant.now(clock).plusSeconds(7 * 24 * 3600));
    invitationRepository.save(invitation);

    events.publishEvent(
        new UserInvited(context.organizationId(), context.userId(), email, request.role(), Instant.now(clock)));
    return new InvitationView(
        invitation.getId(), email, request.role(), "PENDING", rawToken, Instant.now(clock));
  }

  @Transactional(readOnly = true)
  public List<InvitationView> listPending() {
    var context = TenantContext.require();
    accessPolicy.requireAdminLike(context, "view invitations");
    return invitationRepository
        .findByOrganizationIdAndAcceptedAtIsNullAndRevokedAtIsNull(context.organizationId())
        .stream()
        .map(invitation -> new InvitationView(
            invitation.getId(), invitation.getEmail(), invitation.getRole(), "PENDING", null, invitation.getCreatedAt()))
        .toList();
  }

  @Transactional
  public void revoke(UUID invitationId) {
    var context = TenantContext.require();
    accessPolicy.requireAdminLike(context, "revoke invitations");
    Invitation invitation =
        invitationRepository
            .findById(invitationId)
            .orElseThrow(() -> ApiException.notFound("Invitation not found."));
    accessPolicy.requireSameOrganization(context, invitation.getOrganizationId());
    if (invitation.isPending()) {
      invitation.setRevokedAt(Instant.now(clock));
    }
  }

  @Transactional
  public InvitationAcceptResponse accept(UUID acceptingUserId, InvitationAcceptRequest request) {
    Invitation invitation =
        invitationRepository
            .findByTokenHash(hash(request.token()))
            .orElseThrow(
                () -> ApiException.badRequest(
                    ErrorCodes.INVITATION_INVALID_OR_EXPIRED, "This invitation is not valid."));
    Instant now = Instant.now(clock);
    if (!invitation.isPending() || invitation.isExpired(now)) {
      throw ApiException.badRequest(
          ErrorCodes.INVITATION_INVALID_OR_EXPIRED, "This invitation is no longer valid.");
    }
    var user =
        userDirectory
            .findSummary(acceptingUserId)
            .orElseThrow(() -> new ApiException(ErrorCodes.UNAUTHENTICATED, "Unknown account.", 401));
    if (!user.active()) {
      throw new ApiException(ErrorCodes.UNAUTHENTICATED, "Account is disabled.", 401);
    }
    if (!invitation.getEmail().equalsIgnoreCase(user.email())) {
      throw ApiException.forbidden(
          "This invitation was issued to a different email address.");
    }
    Membership membership =
        membershipRepository
            .findByOrganizationIdAndUserId(invitation.getOrganizationId(), acceptingUserId)
            .orElseGet(
                () -> {
                  Membership created = new Membership();
                  created.setOrganizationId(invitation.getOrganizationId());
                  created.setUserId(acceptingUserId);
                  return created;
                });
    membership.setRole(invitation.getRole());
    membership.setStatus(MembershipStatus.ACTIVE);
    membershipRepository.save(membership);

    invitation.setAcceptedAt(now);
    Organization organization =
        organizationRepository
            .findById(invitation.getOrganizationId())
            .orElseThrow(() -> ApiException.notFound("Organization not found."));
    return new InvitationAcceptResponse(
        organization.getId(), organization.getName(), invitation.getRole());
  }

  public String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
