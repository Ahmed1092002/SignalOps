package com.signalops.auth.web;

import com.signalops.auth.app.AuthResponse;
import com.signalops.auth.app.AuthResult;
import com.signalops.auth.app.AuthService;
import com.signalops.auth.app.LoginRequest;
import com.signalops.auth.app.MeResponse;
import com.signalops.auth.app.RegisterRequest;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(
      @Valid @RequestBody RegisterRequest request,
      @org.springframework.web.bind.annotation.RequestHeader(value = "User-Agent", required = false)
          String userAgent) {
    AuthResult result = authService.register(request, userAgent);
    return withCookies(HttpStatus.CREATED, result);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request,
      @org.springframework.web.bind.annotation.RequestHeader(value = "User-Agent", required = false)
          String userAgent) {
    AuthResult result = authService.login(request, userAgent);
    return withCookies(HttpStatus.OK, result);
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @org.springframework.web.bind.annotation.CookieValue(
              name = AuthService.REFRESH_COOKIE_NAME,
              required = false)
          String refreshToken,
      @org.springframework.web.bind.annotation.RequestHeader(value = "User-Agent", required = false)
          String userAgent,
      HttpServletRequest request) {
    requireSameOriginIndicator(request);
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new ApiException(ErrorCodes.UNAUTHENTICATED, "Missing refresh token.", 401);
    }
    AuthResult result = authService.rotate(refreshToken, userAgent);
    return withCookies(HttpStatus.OK, result);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @org.springframework.web.bind.annotation.CookieValue(
              name = AuthService.REFRESH_COOKIE_NAME,
              required = false)
          String refreshToken,
      HttpServletRequest request) {
    requireSameOriginIndicator(request);
    if (refreshToken != null && !refreshToken.isBlank()) {
      authService.logout(refreshToken);
    }
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, authService.clearedRefreshCookie().toString())
        .build();
  }

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal UserPrincipal principal) {
    return authService.currentUser(principal.userId());
  }

  private ResponseEntity<AuthResponse> withCookies(HttpStatus status, AuthResult result) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, authService.buildRefreshCookie(result.refreshToken()).toString())
        .body(new AuthResponse(result.accessToken(), result.user(), result.organization()));
  }

  private void requireSameOriginIndicator(HttpServletRequest request) {
    String header = request.getHeader("X-Requested-With");
    if (!"XMLHttpRequest".equals(header)) {
      throw ApiException.forbidden(
          "This endpoint requires the X-Requested-With: XMLHttpRequest header.");
    }
  }
}
