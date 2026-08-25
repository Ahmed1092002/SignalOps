package com.signalops.shared.security;

import org.springframework.security.authentication.BadCredentialsException;

/** Thrown when a bearer token is syntactically valid but expired. */
public class ExpiredAccessTokenException extends BadCredentialsException {

  public ExpiredAccessTokenException(String message) {
    super(message);
  }
}
