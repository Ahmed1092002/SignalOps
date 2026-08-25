package com.signalops.users.web;

import com.signalops.organizations.api.MembershipDirectory;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.security.UserPrincipal;
import com.signalops.users.api.UserDirectory;
import com.signalops.users.api.events.PasswordChanged;
import com.signalops.users.app.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final UserDirectory userDirectory;
  private final MembershipDirectory membershipDirectory;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public record UpdateProfileRequest(
      @Size(min = 1, max = 120) String fullName, String currentPassword, String newPassword) {}

  public record ProfileView(UUID userId, String fullName, String email) {}

  @PatchMapping("/me")
  public ResponseEntity<ProfileView> updateMe(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody UpdateProfileRequest request) {
    UUID userId = principal.userId();

    if (request.newPassword() != null && !request.newPassword().isBlank()) {
      var user =
          userDirectory
              .findSummary(userId)
              .orElseThrow(() -> new ApiException(ErrorCodes.UNAUTHENTICATED, "Unknown account.", 401));
      String currentHash = userService.passwordHashOf(userId);
      if (request.currentPassword() == null
          || !passwordEncoder.matches(request.currentPassword(), currentHash)) {
        throw ApiException.badRequest(
            ErrorCodes.VALIDATION_FAILED, "Current password is incorrect.");
      }
      if (request.newPassword().length() < 10 || !request.newPassword().matches("^(?=.*\\p{L})(?=.*\\d).{10,72}$")) {
        throw ApiException.badRequest(
            ErrorCodes.VALIDATION_FAILED,
            "New password must be 10-72 characters and contain at least one letter and one digit.");
      }
      userService.changePassword(userId, passwordEncoder.encode(request.newPassword()));
      List<UUID> orgIds =
          membershipDirectory.membershipOrganizations(userId);
      events.publishEvent(new PasswordChanged(userId, orgIds, Instant.now(clock)));
    }
    if (request.fullName() != null && !request.fullName().isBlank()) {
      userService.changeFullName(userId, request.fullName().trim());
    }
    var user =
        userDirectory
            .findSummary(userId)
            .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, "User not found.", 404));
    return ResponseEntity.ok(new ProfileView(user.userId(), user.fullName(), user.email()));
  }
}
