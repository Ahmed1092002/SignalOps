package com.signalops.realtime.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.signalops.realtime.ws.WebSocketConfig.StompPrincipal;
import com.signalops.shared.security.AccessTokenVerifier;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

class WebSocketConfigTest {

  private AccessTokenVerifier tokenVerifier;
  private SubscriptionAuthorizer authorizer;
  private WebSocketConfig config;
  private ChannelInterceptor interceptor;
  private MessageChannel channel;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger configLogger;

  @BeforeEach
  void setUp() {
    tokenVerifier = mock(AccessTokenVerifier.class);
    authorizer = mock(SubscriptionAuthorizer.class);
    ObjectProvider<AccessTokenVerifier> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(tokenVerifier);

    config = new WebSocketConfig(provider, authorizer);
    interceptor = config.createInboundInterceptor();
    channel = mock(MessageChannel.class);

    configLogger = (Logger) LoggerFactory.getLogger(WebSocketConfig.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    configLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    configLogger.detachAppender(logAppender);
  }

  @Test
  void connectWithValidTokenAuthenticatesAndDoesNotLogToken() {
    UUID userId = UUID.randomUUID();
    String secretJwt = "super-secret-jwt-token-12345";
    when(tokenVerifier.verify(secretJwt)).thenReturn(userId);

    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setLeaveMutable(true);
    accessor.setNativeHeader("Authorization", "Bearer " + secretJwt);
    Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, channel);
    assertThat(result).isNotNull();

    StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
    assertThat(resultAccessor.getUser()).isNotNull();
    assertThat(resultAccessor.getUser().getName()).isEqualTo(userId.toString());

    // Verify token was NOT logged anywhere
    for (ILoggingEvent event : logAppender.list) {
      assertThat(event.getFormattedMessage()).doesNotContain(secretJwt);
      assertThat(event.getFormattedMessage()).doesNotContain("Authorization");
    }
  }

  @Test
  void connectWithoutBearerHeaderFails() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    assertThatThrownBy(() -> interceptor.preSend(message, channel))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Missing bearer token");
  }

  @Test
  void subscribeUserQueueNotificationsAllowedForAuthenticatedUser() {
    UUID userId = UUID.randomUUID();
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setUser(new StompPrincipal(userId.toString()));
    accessor.setDestination("/user/queue/notifications");
    Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, channel);
    assertThat(result).isNotNull();
  }

  @Test
  void subscribeUserTopicAllowedForAuthenticatedUser() {
    UUID userId = UUID.randomUUID();
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setUser(new StompPrincipal(userId.toString()));
    accessor.setDestination("/user/topic/alerts");
    Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, channel);
    assertThat(result).isNotNull();
  }

  @Test
  void subscribeExplicitOwnUserQueueAllowed() {
    UUID userId = UUID.randomUUID();
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setUser(new StompPrincipal(userId.toString()));
    accessor.setDestination("/user/" + userId + "/queue/notifications");
    Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, channel);
    assertThat(result).isNotNull();
  }

  @Test
  void subscribeAnotherUserQueueIsRejected() {
    UUID currentUserId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();

    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setUser(new StompPrincipal(currentUserId.toString()));
    accessor.setDestination("/user/" + targetUserId + "/queue/notifications");
    Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    assertThatThrownBy(() -> interceptor.preSend(message, channel))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Cannot subscribe to another user's queue.");
  }

  @Test
  void subscribeWithoutAuthenticationIsRejected() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setDestination("/user/queue/notifications");
    Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    assertThatThrownBy(() -> interceptor.preSend(message, channel))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Authentication required to subscribe.");
  }

  @Test
  void subscribeOrganizationTopicEnforcesMembership() {
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();

    when(authorizer.isActiveMember(userId, orgId)).thenReturn(true);

    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setUser(new StompPrincipal(userId.toString()));
    accessor.setDestination("/topic/org." + orgId + ".incidents");
    Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    assertThat(interceptor.preSend(message, channel)).isNotNull();

    // Non-member rejected
    UUID otherOrgId = UUID.randomUUID();
    when(authorizer.isActiveMember(userId, otherOrgId)).thenReturn(false);

    StompHeaderAccessor unauthorizedAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    unauthorizedAccessor.setUser(new StompPrincipal(userId.toString()));
    unauthorizedAccessor.setDestination("/topic/org." + otherOrgId + ".incidents");
    Message<?> unauthorizedMessage =
        MessageBuilder.createMessage(new byte[0], unauthorizedAccessor.getMessageHeaders());

    assertThatThrownBy(() -> interceptor.preSend(unauthorizedMessage, channel))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Not a member of this organization.");
  }
}
