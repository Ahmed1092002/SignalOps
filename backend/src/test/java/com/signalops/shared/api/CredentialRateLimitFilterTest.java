package com.signalops.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalops.shared.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CredentialRateLimitFilterTest {

  private TestClock clock;
  private AppProperties properties;
  private FilterChain chain;
  private HttpServletResponse response;
  private StringWriter responseWriter;

  static class TestClock extends Clock {
    private final AtomicLong millis = new AtomicLong(1_000_000L);

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis.get());
    }

    @Override
    public long millis() {
      return millis.get();
    }

    void advanceMillis(long delta) {
      millis.addAndGet(delta);
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    clock = new TestClock();
    properties =
        new AppProperties(
            null,
            null,
            new AppProperties.RateLimit(true, 3));
    chain = mock(FilterChain.class);
    response = mock(HttpServletResponse.class);
    responseWriter = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
  }

  @Test
  void rateLimitsWhenRequestsExceedLimit() throws Exception {
    CredentialRateLimitFilter filter =
        new CredentialRateLimitFilter(properties, clock, 100, 60_000L);

    HttpServletRequest request = mockRequest("192.168.1.100", "/api/v1/auth/login");

    // Attempts 1, 2, 3 pass
    for (int i = 0; i < 3; i++) {
      filter.doFilter(request, response, chain);
    }
    verify(chain, org.mockito.Mockito.times(3)).doFilter(request, response);

    // 4th attempt is rate-limited
    filter.doFilter(request, response, chain);
    verify(response).setStatus(429);
    assertThat(responseWriter.toString()).contains("RATE_LIMITED");

    // Advance clock past 60s window: attempts allowed again
    clock.advanceMillis(60_001L);
    filter.doFilter(request, response, chain);
    verify(chain, org.mockito.Mockito.times(4)).doFilter(request, response);
  }

  @Test
  void cleansUpExpiredEntries() throws Exception {
    CredentialRateLimitFilter filter =
        new CredentialRateLimitFilter(properties, clock, 100, 60_000L);

    HttpServletRequest req1 = mockRequest("10.0.0.1", "/api/v1/auth/login");
    HttpServletRequest req2 = mockRequest("10.0.0.2", "/api/v1/auth/login");

    filter.doFilter(req1, response, chain);
    filter.doFilter(req2, response, chain);
    assertThat(filter.entryCount()).isEqualTo(2);

    // Before window expiration, cleanup keeps entries
    filter.cleanExpired(clock.millis());
    assertThat(filter.entryCount()).isEqualTo(2);

    // Advance past window duration
    clock.advanceMillis(60_001L);
    filter.cleanExpired(clock.millis());
    assertThat(filter.entryCount()).isEqualTo(0);
  }

  @Test
  void enforcesMaximumBoundedCapacity() throws Exception {
    int maxEntries = 5;
    CredentialRateLimitFilter filter =
        new CredentialRateLimitFilter(properties, clock, maxEntries, 60_000L);

    // Add 10 distinct client IPs
    for (int i = 0; i < 10; i++) {
      HttpServletRequest req = mockRequest("10.0.0." + i, "/api/v1/auth/login");
      filter.doFilter(req, response, chain);
    }

    // Size must never grow unbounded beyond capacity
    assertThat(filter.entryCount()).isLessThanOrEqualTo(maxEntries);
  }

  @Test
  void keepsCapacityBoundedForConcurrentDistinctClients() throws Exception {
    int maxEntries = 5;
    int requests = 50;
    CredentialRateLimitFilter filter =
        new CredentialRateLimitFilter(properties, clock, maxEntries, 60_000L);
    ExecutorService executor = Executors.newFixedThreadPool(requests);
    CountDownLatch ready = new CountDownLatch(requests);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> tasks = new ArrayList<>();

    try {
      for (int i = 0; i < requests; i++) {
        String ip = "10.0.1." + i;
        tasks.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  filter.doFilter(mockRequest(ip, "/api/v1/auth/login"), mock(HttpServletResponse.class), chain);
                  return null;
                }));
      }
      ready.await();
      start.countDown();
      for (Future<?> task : tasks) {
        task.get();
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(filter.entryCount()).isLessThanOrEqualTo(maxEntries);
  }

  @Test
  void clientKeyDoesNotTrustAttemptEmailHeader() throws Exception {
    CredentialRateLimitFilter filter =
        new CredentialRateLimitFilter(properties, clock, 100, 60_000L);

    HttpServletRequest req1 = mockRequest("10.0.0.1", "/api/v1/auth/login");
    when(req1.getHeader("X-Attempt-Email")).thenReturn("victim1@example.com");

    HttpServletRequest req2 = mockRequest("10.0.0.1", "/api/v1/auth/login");
    when(req2.getHeader("X-Attempt-Email")).thenReturn("victim2@example.com");

    filter.doFilter(req1, response, chain);
    filter.doFilter(req2, response, chain);

    // Only one key stored for 10.0.0.1, not separate entries per header
    assertThat(filter.entryCount()).isEqualTo(1);
    assertThat(filter.containsKey("10.0.0.1")).isTrue();
  }

  private HttpServletRequest mockRequest(String ip, String path) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn(path);
    when(request.getRemoteAddr()).thenReturn(ip);
    return request;
  }
}
