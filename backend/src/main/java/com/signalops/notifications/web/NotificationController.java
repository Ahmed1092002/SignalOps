package com.signalops.notifications.web;

import com.signalops.notifications.app.NotificationService;

import com.signalops.notifications.app.NotificationService.NotificationView;
import com.signalops.shared.api.PageResponse;
import com.signalops.shared.security.UserPrincipal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;



  @GetMapping
  public PageResponse<NotificationView> list(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "15") int size) {
    return notificationService.listForUser(principal.userId(), page, Math.min(size, 50));
  }

  @GetMapping("/unread-count")
  public long unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
    return notificationService.unreadCount(principal.userId());
  }

  @PostMapping("/{notificationId}/read")
  public ResponseEntity<Void> markRead(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID notificationId) {
    notificationService.markRead(principal.userId(), notificationId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/read-all")
  public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
    notificationService.markAllRead(principal.userId());
    return ResponseEntity.noContent().build();
  }
}
