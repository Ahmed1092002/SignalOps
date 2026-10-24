package com.signalops.shared.api;

import com.signalops.shared.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Autowired;
/**
 * Minimal fixed-window rate limit for credential endpoints (login/register). Per-instance
 * only — a Redis-backed distributed limiter remains documented backlog for multi-instance
 * deployments. Returns 429 with the standard error envelope shape.
 */
@Component
@Order(1)
public class CredentialRateLimitFilter extends OncePerRequestFilter {

  public static final int DEFAULT_MAX_ENTRIES = 10_000;
  public static final long DEFAULT_WINDOW_MS = 60_000;

  private final AppProperties properties;
  private final Clock clock;
  private final int maxEntries;
  private final long windowMs;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final Object windowsLock = new Object();

  @Autowired

  public CredentialRateLimitFilter(AppProperties properties) {
    this(properties, Clock.systemUTC(), DEFAULT_MAX_ENTRIES, DEFAULT_WINDOW_MS);
  }

  public CredentialRateLimitFilter(
      AppProperties properties, Clock clock, int maxEntries, long windowMs) {
    this.properties = properties;
    this.clock = clock;
    this.maxEntries = maxEntries;
    this.windowMs = windowMs;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!"POST".equals(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    return !path.endsWith("/auth/login") && !path.endsWith("/auth/register");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var rateLimit =
        properties.rateLimit() == null
            ? AppProperties.RateLimit.defaults()
            : properties.rateLimit();
    if (!rateLimit.enabled()) {
      chain.doFilter(request, response);
      return;
    }

    Window window;
    long now = clock.millis();
    String key = clientKey(request);
    synchronized (windowsLock) {
      if (windows.size() >= maxEntries) {
        cleanExpired(now);
        if (windows.size() >= maxEntries) {
          evictOldest(Math.max(1, maxEntries / 10));
        }
      }

      window =
          windows.compute(
              key,
              (ignored, existing) -> {
                if (existing == null || now - existing.start >= windowMs) {
                  return new Window(now, new AtomicLong(1));
                }
                existing.count.incrementAndGet();
                return existing;
              });
    }

    if (window.count.get() > rateLimit.limitPerMinute()) {
      response.setStatus(429);
      response.setContentType("application/json");
      response.getWriter()
          .write(
              "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many attempts. Try again in a minute.\","
                  + "\"timestamp\":\""
                  + Instant.now(clock)
                  + "\",\"path\":\""
                  + request.getRequestURI()
                  + "\",\"details\":[]}");
      return;
    }
    chain.doFilter(request, response);
  }

  public void cleanExpired(long now) {
    synchronized (windowsLock) {
      windows.entrySet().removeIf(entry -> now - entry.getValue().start >= windowMs);
    }
  }

  private void evictOldest(int countToEvict) {
    windows.entrySet().stream()
        .sorted(Comparator.comparingLong(e -> e.getValue().start))
        .limit(countToEvict)
        .map(Map.Entry::getKey)
        .toList()
        .forEach(windows::remove);
  }

  String clientKey(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    if (remoteAddr == null || remoteAddr.isBlank()) {
      return "unknown";
    }
    return remoteAddr.trim();
  }

  int entryCount() {
    return windows.size();
  }

  boolean containsKey(String key) {
    return windows.containsKey(key);
  }

  private static final class Window {
    private final long start;
    private final AtomicLong count;

    private Window(long start, AtomicLong count) {
      this.start = start;
      this.count = count;
    }
  }
}
