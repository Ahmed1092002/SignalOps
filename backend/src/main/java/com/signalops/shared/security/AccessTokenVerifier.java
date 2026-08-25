package com.signalops.shared.security;

public interface AccessTokenVerifier {

  /**
   * Validates the signature and expiry of an access token.
   *
   * @return the authenticated user id encoded in the token
   * @throws org.springframework.security.core.AuthenticationException when invalid or expired
   */
  java.util.UUID verify(String token);
}
