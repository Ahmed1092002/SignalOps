package com.signalops.notifications.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalops.notifications.app.NotificationService;
import com.signalops.notifications.app.NotificationService.NotificationView;
import com.signalops.shared.api.PageResponse;
import com.signalops.shared.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationControllerTest {

  private NotificationService notificationService;
  private NotificationController controller;
  private UserPrincipal principal;

  @BeforeEach
  void setUp() {
    notificationService = mock(NotificationService.class);
    controller = new NotificationController(notificationService);
    principal = new UserPrincipal(UUID.randomUUID());
  }

  @Test
  void negativePageAndZeroSizeAreNormalizedSafely() {
    when(notificationService.listForUser(eq(principal.userId()), eq(0), eq(1)))
        .thenReturn(new PageResponse<>(List.of(), 0, 1, 0, 0));

    PageResponse<NotificationView> response = controller.list(principal, -1, 0);

    assertThat(response).isNotNull();
    assertThat(response.page()).isEqualTo(0);
    assertThat(response.size()).isEqualTo(1);
    verify(notificationService).listForUser(principal.userId(), 0, 1);
  }

  @Test
  void negativeSizeIsNormalizedToOne() {
    when(notificationService.listForUser(eq(principal.userId()), eq(0), eq(1)))
        .thenReturn(new PageResponse<>(List.of(), 0, 1, 0, 0));

    PageResponse<NotificationView> response = controller.list(principal, 0, -5);

    assertThat(response).isNotNull();
    assertThat(response.page()).isEqualTo(0);
    assertThat(response.size()).isEqualTo(1);
    verify(notificationService).listForUser(principal.userId(), 0, 1);
  }

  @Test
  void largeSizeIsCappedAtFifty() {
    when(notificationService.listForUser(eq(principal.userId()), eq(0), eq(50)))
        .thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0));

    PageResponse<NotificationView> response = controller.list(principal, 0, 1000);

    assertThat(response).isNotNull();
    assertThat(response.page()).isEqualTo(0);
    assertThat(response.size()).isEqualTo(50);
    verify(notificationService).listForUser(principal.userId(), 0, 50);
  }

  @Test
  void normalPaginationParametersPassedThrough() {
    when(notificationService.listForUser(eq(principal.userId()), eq(2), eq(25)))
        .thenReturn(new PageResponse<>(List.of(), 2, 25, 100, 4));

    PageResponse<NotificationView> response = controller.list(principal, 2, 25);

    assertThat(response).isNotNull();
    assertThat(response.page()).isEqualTo(2);
    assertThat(response.size()).isEqualTo(25);
    assertThat(response.totalElements()).isEqualTo(100);
    verify(notificationService).listForUser(principal.userId(), 2, 25);
  }
}
