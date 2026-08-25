package com.signalops.analytics.web;

import com.signalops.analytics.app.AnalyticsService;

import com.signalops.analytics.app.AnalyticsService.Overview;
import com.signalops.shared.security.UserPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  public record DayPoint(String date, long created, long resolved) {}

  public record SeverityCount(String severity, long count) {}

  public record ServiceCount(String serviceName, long count) {}



  @GetMapping("/overview")
  public Overview overview(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "30") int days) {
    int window = Math.min(Math.max(days, 7), 90);
    return analyticsService.overview(window);
  }
}
