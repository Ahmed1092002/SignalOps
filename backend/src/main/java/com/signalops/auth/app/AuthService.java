package com.signalops.auth.app;

import com.signalops.organizations.app.OrganizationService;
import com.signalops.organizations.domain.Membership;
import com.signalops.organizations.domain.MembershipRepository;
import com.signalops.organizations.domain.Organization;
import com.signalops.organizations.domain.OrganizationRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.config.AppProperties;
import com.signalops.users.domain.User;
import com.signalops.users.domain.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  public static final String REFRESH_COOKIE_NAME = "signalops_refresh";

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final MembershipRepository membershipRepository;
  private final OrganizationService organizationService;
  private final RefreshTokenService refreshTokenService;
  private final JwtService jwtService;
  private final PasswordEncoder passwordEncoder;
  private final AppProperties properties;
  private final Clock clock;

  @Transactional
  public AuthResult register(RegisterRequest request, String userAgent) {
    if (userRepository.existsByEmailIgnoreCase(request.email())) {
      throw ApiException.conflict(ErrorCodes.DUPLICATE_EMAIL, "An account with this email already exists.");
    }
    User user = new User();
    user.setEmail(request.email().trim());
    user.setFullName(request.fullName().trim());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setActive(true);
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      throw ApiException.conflict(ErrorCodes.DUPLICATE_EMAIL, "An account with this email already exists.");
    }

    Organization organization = organizationService.createWithOwner(request.organizationName().trim(), user.getId());
    String refreshToken = refreshTokenService.issueFamily(user.getId(), userAgent).rawToken();
    return new AuthResult(
        jwtService.issueAccessToken(user.getId()),
        refreshToken,
        toUserDto(user),
        new OrganizationDto(organization.getId(), organization.getName(), organization.getSlug()));
  }

  @Transactional
  public AuthResult login(LoginRequest request, String userAgent) {
    User user =
        userRepository
            .findByEmailIgnoreCase(request.email())
            .orElseThrow(() -> invalidCredentials());
    if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw invalidCredentials();
    }
    Organization organization = firstOrganization(user.getId());
    String refreshToken = refreshTokenService.issueFamily(user.getId(), userAgent).rawToken();
    return new AuthResult(
        jwtService.issueAccessToken(user.getId()),
        refreshToken,
        toUserDto(user),
        toOrganizationDto(organization));
  }

  @Transactional
  public AuthResult rotate(String rawRefreshToken, String userAgent) {
    RefreshTokenService.IssuedToken rotated;
    try {
      rotated = refreshTokenService.rotate(rawRefreshToken, userAgent, this::assertCanRefresh);
    } catch (RefreshTokenService.RefreshTokenReuseException ex) {
      // The locked rotation transaction has ended, so this independent transaction can revoke
      // every member of the family without blocking on the presented token's row lock.
      refreshTokenService.revokeFamilySurvivingRollback(ex.familyId());
      throw new ApiException(
          ErrorCodes.REFRESH_REUSE_DETECTED,
          "Refresh token was already used; the session has been revoked.",
          401);
    }
    User user =
        userRepository
            .findById(rotated.session().getUserId())
            .orElseThrow(() -> new ApiException(ErrorCodes.UNAUTHENTICATED, "Account no longer exists.", 401));
    if (!user.isActive()) {
      throw new ApiException(ErrorCodes.UNAUTHENTICATED, "Account is disabled.", 401);
    }
    Organization organization = firstOrganization(user.getId());
    return new AuthResult(
        jwtService.issueAccessToken(user.getId()),
        rotated.rawToken(),
        toUserDto(user),
        toOrganizationDto(organization));
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    refreshTokenService.revokeFamilyOf(rawRefreshToken);
  }

  @Transactional(readOnly = true)
  public MeResponse currentUser(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCodes.UNAUTHENTICATED, "Unknown account.", 401));
    List<MembershipDto> memberships =
        membershipRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
            .map(this::toMembershipDto)
            .toList();
    return new MeResponse(toUserDto(user), memberships);
  }

  private ApiException invalidCredentials() {
    return new ApiException(ErrorCodes.INVALID_CREDENTIALS, "Invalid email or password.", 401);
  }

  private void assertCanRefresh(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCodes.UNAUTHENTICATED, "Account no longer exists.", 401));
    if (!user.isActive()) {
      throw new ApiException(ErrorCodes.UNAUTHENTICATED, "Account is disabled.", 401);
    }
    firstOrganization(userId);
  }

  private Organization firstOrganization(UUID userId) {
    Membership membership =
        membershipRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
            .filter(Membership::isActive)
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, "No organization membership.", 404));
    return organizationRepository
        .findById(membership.getOrganizationId())
        .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, "Organization not found.", 404));
  }

  private UserDto toUserDto(User user) {
    return new UserDto(user.getId(), user.getEmail(), user.getFullName());
  }

  private OrganizationDto toOrganizationDto(Organization organization) {
    return new OrganizationDto(organization.getId(), organization.getName(), organization.getSlug());
  }

  private MembershipDto toMembershipDto(Membership membership) {
    Organization organization =
        organizationRepository
            .findById(membership.getOrganizationId())
            .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, "Organization not found.", 404));
    return new MembershipDto(
        organization.getId(),
        organization.getName(),
        organization.getSlug(),
        membership.getRole(),
        membership.getStatus().name());
  }

  public ResponseCookie buildRefreshCookie(String rawToken) {
    return ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
        .httpOnly(true)
        .secure(properties.security().cookieSecure())
        .sameSite("Lax")
        .path("/api/v1/auth")
        .maxAge(properties.security().refreshTtlOrDefault())
        .build();
  }

  public ResponseCookie clearedRefreshCookie() {
    return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(properties.security().cookieSecure())
        .sameSite("Lax")
        .path("/api/v1/auth")
        .maxAge(0)
        .build();
  }
}

