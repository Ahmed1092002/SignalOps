package com.signalops.shared.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "signalops")
public record AppProperties(Security security, Seed seed, RateLimit rateLimit) {

  public record Security(
      @NotBlank String jwtSecret,
      Duration accessTtl,
      Duration refreshTtl,
      boolean cookieSecure,
      List<String> corsAllowedOrigins) {

    public Duration accessTtlOrDefault() {
      return accessTtl == null ? Duration.ofMinutes(15) : accessTtl;
    }

    public Duration refreshTtlOrDefault() {
      return refreshTtl == null ? Duration.ofDays(7) : refreshTtl;
    }
  }

  public record Seed(boolean enabled) {
    public static Seed defaults() {
      return new Seed(false);
    }
  }

  public record RateLimit(boolean enabled, int limitPerMinute) {
    public static RateLimit defaults() {
      return new RateLimit(true, 10);
    }
  }
}
