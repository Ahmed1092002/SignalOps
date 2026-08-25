package com.signalops.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Minimal fixed-window rate limit for credential endpoints (login/register). Per-instance
 * only — a Redis-backed distributed limiter remains documented backlog for multi-instance
 * deployments. Returns 429 with the standard error envelope shape.
 */
@Component
@Order(1)
public class CredentialRateLimitFilter extends OncePerRequestFilter {

  private final com.signalops.shared.config.AppProperties properties;

  public CredentialRateLimitFilter(com.signalops.shared.config.AppProperties properties) {
    this.properties = properties;
  }

  private static final int LIMIT_PER_MINUTE = 10;
  private static final long WINDOW_MS = 60_000;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

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
    var rateLimit = properties.rateLimit() == null ? com.signalops.shared.config.AppProperties.RateLimit.defaults() : properties.rateLimit();
    if (!rateLimit.enabled()) {
      chain.doFilter(request, response);
      return;
    }
    int limit = rateLimit.limitPerMinute();
    String key = clientKey(request);
    long now = System.currentTimeMillis();
    Window window = windows.compute(key, (ignored, existing) -> {
      if (existing == null || now - existing.start >= WINDOW_MS) {
        return new Window(now, new AtomicLong(1));
      }
      existing.count.incrementAndGet();
      return existing;
    });
    if (window.count.get() > limit) {
      response.setStatus(429);
      response.setContentType("application/json");
      response.getWriter().write(
          "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many attempts. Try again in a minute.\","
              + "\"timestamp\":\"" + java.time.Instant.now() + "\",\"path\":\""
              + request.getRequestURI() + "\",\"details\":[]}");
      return;
    }
    chain.doFilter(request, response);
  }

  private String clientKey(HttpServletRequest request) {
    String email = request.getHeader("X-Attempt-Email");
    return request.getRemoteAddr() + "|" + (email == null ? "" : email.toLowerCase());
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
