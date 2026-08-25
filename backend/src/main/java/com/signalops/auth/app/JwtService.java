package com.signalops.auth.app;

import com.signalops.shared.config.AppProperties;
import com.signalops.shared.security.AccessTokenVerifier;
import com.signalops.shared.security.ExpiredAccessTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class JwtService implements AccessTokenVerifier {

  private static final String ISSUER = "signalops";
  private static final int MIN_SECRET_BYTES = 32;

  private final SecretKey key;
  private final Duration accessTtl;
  private final Clock clock;

  public JwtService(AppProperties properties, Clock clock) {
    this.key = buildKey(properties.security().jwtSecret());
    this.accessTtl = properties.security().accessTtlOrDefault();
    this.clock = clock;
  }

  public String issueAccessToken(UUID userId) {
    Instant now = Instant.now(clock);
    return Jwts.builder()
        .issuer(ISSUER)
        .subject(userId.toString())
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(accessTtl)))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  @Override
  public UUID verify(String token) {
    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(key)
              .requireIssuer(ISSUER)
              .build()
              .parseSignedClaims(token)
              .getPayload();
      return UUID.fromString(claims.getSubject());
    } catch (ExpiredJwtException ex) {
      throw new ExpiredAccessTokenException("Access token expired.");
    } catch (JwtException | IllegalArgumentException ex) {
      throw new BadCredentialsException("Invalid access token.");
    }
  }

  public Duration accessTtl() {
    return accessTtl;
  }

  private SecretKey buildKey(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "JWT_SECRET is required. Provide a base64-encoded secret of at least 32 bytes.");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(secret);
    } catch (IllegalArgumentException ex) {
      bytes = secret.getBytes(StandardCharsets.UTF_8);
    }
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "JWT_SECRET must contain at least 256 bits of entropy (32 bytes).");
    }
    return Keys.hmacShaKeyFor(bytes);
  }
}
