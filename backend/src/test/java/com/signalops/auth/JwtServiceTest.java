package com.signalops.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.signalops.auth.app.JwtService;
import com.signalops.shared.config.AppProperties;
import com.signalops.shared.security.ExpiredAccessTokenException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class JwtServiceTest {

  private static final String SECRET =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  private JwtService service(Clock clock, Duration ttl) {
    return new JwtService(
        new AppProperties(
            new AppProperties.Security(SECRET, ttl, Duration.ofDays(7), false, java.util.List.of()),
            AppProperties.Seed.defaults(),
            AppProperties.RateLimit.defaults()),
        clock);
  }

  @Test
  void issuesAndVerifiesTokenForUser() {
    JwtService service = service(Clock.systemUTC(), Duration.ofMinutes(15));
    UUID userId = UUID.randomUUID();
    String token = service.issueAccessToken(userId);
    assertThat(service.verify(token)).isEqualTo(userId);
  }

  @Test
  void expiredTokenIsRejectedWithSpecificException() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    JwtService service = service(clock, Duration.ofSeconds(30));
    String token = service.issueAccessToken(UUID.randomUUID());
    clock.advanceBy(Duration.ofMinutes(1));
    assertThatThrownBy(() -> service.verify(token))
        .isInstanceOf(ExpiredAccessTokenException.class);
  }

  @Test
  void tokenSignedWithDifferentKeyIsRejected() {
    JwtService issuer =
        service(
            Clock.systemUTC(), Duration.ofMinutes(15));
    String otherSecret =
        Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());
    JwtService verifier =
        new JwtService(
            new AppProperties(
                new AppProperties.Security(
                    otherSecret, Duration.ofMinutes(15), Duration.ofDays(7), false, java.util.List.of()),
                AppProperties.Seed.defaults(),
            AppProperties.RateLimit.defaults()),
            Clock.systemUTC());
    String token = issuer.issueAccessToken(UUID.randomUUID());
    assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void rejectsShortSecrets() {
    assertThatThrownBy(
            () ->
                new JwtService(
                    new AppProperties(
                        new AppProperties.Security(
                            Base64.getEncoder().encodeToString("short".getBytes()),
                            Duration.ofMinutes(15),
                            Duration.ofDays(7),
                            false,
                            java.util.List.of()),
                        AppProperties.Seed.defaults(),
            AppProperties.RateLimit.defaults()),
                    Clock.systemUTC()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("256 bits");
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advanceBy(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
