package com.signalops.realtime.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.signalops.organizations.api.OrganizationEvents;
import com.signalops.realtime.ws.WebSocketConfig.StompPrincipal;
import com.signalops.shared.security.AccessTokenVerifier;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
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
  private SubscribableChannel inboundChannel;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger configLogger;

  @BeforeEach
  void setUp() {
    tokenVerifier = mock(AccessTokenVerifier.class);
    authorizer = mock(SubscriptionAuthorizer.class);
    ObjectProvider<AccessTokenVerifier> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(tokenVerifier);

    inboundChannel = mock(SubscribableChannel.class);
    ObjectProvider<SubscribableChannel> inboundProvider = mock(ObjectProvider.class);
    when(inboundProvider.getIfAvailable()).thenReturn(inboundChannel);

    config = new WebSocketConfig(provider, authorizer, inboundProvider);
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

  @Test
  void memberRemovedRevokesOnlyTargetOrgSubscriptionsForUser() {
    UUID userId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    UUID orgA = UUID.randomUUID();
    UUID orgB = UUID.randomUUID();

    when(authorizer.isActiveMember(userId, orgA)).thenReturn(true);
    when(authorizer.isActiveMember(userId, orgB)).thenReturn(true);
    when(authorizer.isActiveMember(otherUserId, orgA)).thenReturn(true);

    // User subscribes to orgA and orgB
    StompHeaderAccessor subOrgA = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    subOrgA.setUser(new StompPrincipal(userId.toString()));
    subOrgA.setSessionId("session-1");
    subOrgA.setSubscriptionId("sub-a");
    subOrgA.setDestination("/topic/org." + orgA + ".incidents");
    interceptor.preSend(MessageBuilder.createMessage(new byte[0], subOrgA.getMessageHeaders()), channel);

    StompHeaderAccessor subOrgB = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    subOrgB.setUser(new StompPrincipal(userId.toString()));
    subOrgB.setSessionId("session-1");
    subOrgB.setSubscriptionId("sub-b");
    subOrgB.setDestination("/topic/org." + orgB + ".incidents");
    interceptor.preSend(MessageBuilder.createMessage(new byte[0], subOrgB.getMessageHeaders()), channel);

    // Other user subscribes to orgA
    StompHeaderAccessor otherSubOrgA = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    otherSubOrgA.setUser(new StompPrincipal(otherUserId.toString()));
    otherSubOrgA.setSessionId("session-2");
    otherSubOrgA.setSubscriptionId("sub-other-a");
    otherSubOrgA.setDestination("/topic/org." + orgA + ".incidents");
    interceptor.preSend(MessageBuilder.createMessage(new byte[0], otherSubOrgA.getMessageHeaders()), channel);

    assertThat(config.getActiveSubscriptions(userId, orgA)).hasSize(1);
    assertThat(config.getActiveSubscriptions(userId, orgB)).hasSize(1);
    assertThat(config.getActiveSubscriptions(otherUserId, orgA)).hasSize(1);

    // Member removed from orgA
    config.onMemberRemoved(
        new OrganizationEvents.MemberRemoved(
            orgA, UUID.randomUUID(), UUID.randomUUID(), userId, "user@example.com", Instant.now()));

    // User's orgA subscription is revoked
    assertThat(config.getActiveSubscriptions(userId, orgA)).isEmpty();
    // User's orgB subscription remains intact
    assertThat(config.getActiveSubscriptions(userId, orgB)).hasSize(1);
    // Other user's orgA subscription remains intact
    assertThat(config.getActiveSubscriptions(otherUserId, orgA)).hasSize(1);

    // Inbound channel received an UNSUBSCRIBE message for session-1, sub-a
    ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(inboundChannel).send(messageCaptor.capture());
    StompHeaderAccessor sentAccessor = StompHeaderAccessor.wrap(messageCaptor.getValue());
    assertThat(sentAccessor.getCommand()).isEqualTo(StompCommand.UNSUBSCRIBE);
    assertThat(sentAccessor.getSessionId()).isEqualTo("session-1");
    assertThat(sentAccessor.getSubscriptionId()).isEqualTo("sub-a");
  }

  @Test
  void memberDisabledRevokesTargetOrgSubscriptionsForUser() {
    UUID userId = UUID.randomUUID();
    UUID orgA = UUID.randomUUID();

    when(authorizer.isActiveMember(userId, orgA)).thenReturn(true);

    StompHeaderAccessor subOrgA = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    subOrgA.setUser(new StompPrincipal(userId.toString()));
    subOrgA.setSessionId("session-1");
    subOrgA.setSubscriptionId("sub-a");
    subOrgA.setDestination("/topic/org." + orgA + ".services");
    interceptor.preSend(MessageBuilder.createMessage(new byte[0], subOrgA.getMessageHeaders()), channel);

    assertThat(config.getActiveSubscriptions(userId, orgA)).hasSize(1);

    config.onMemberDisabled(
        new OrganizationEvents.MemberDisabled(
            orgA, UUID.randomUUID(), UUID.randomUUID(), userId, "user@example.com", Instant.now()));

    assertThat(config.getActiveSubscriptions(userId, orgA)).isEmpty();
    verify(inboundChannel).send(any(Message.class));
  }

  @Test
  void explicitUnsubscribeAndDisconnectCleanUpTracking() {
    UUID userId = UUID.randomUUID();
    UUID orgA = UUID.randomUUID();
    when(authorizer.isActiveMember(userId, orgA)).thenReturn(true);

    StompHeaderAccessor sub1 = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    sub1.setUser(new StompPrincipal(userId.toString()));
    sub1.setSessionId("session-1");
    sub1.setSubscriptionId("sub-1");
    sub1.setDestination("/topic/org." + orgA + ".incidents");
    interceptor.preSend(MessageBuilder.createMessage(new byte[0], sub1.getMessageHeaders()), channel);

    StompHeaderAccessor sub2 = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    sub2.setUser(new StompPrincipal(userId.toString()));
    sub2.setSessionId("session-1");
    sub2.setSubscriptionId("sub-2");
    sub2.setDestination("/topic/org." + orgA + ".services");
    interceptor.preSend(MessageBuilder.createMessage(new byte[0], sub2.getMessageHeaders()), channel);

    assertThat(config.getActiveSubscriptions(userId, orgA)).hasSize(2);

    // Unsubscribe sub-1
    StompHeaderAccessor unsub = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
    unsub.setSessionId("session-1");
    unsub.setSubscriptionId("sub-1");
    interceptor.preSend(MessageBuilder.createMessage(new byte[0], unsub.getMessageHeaders()), channel);

    assertThat(config.getActiveSubscriptions(userId, orgA)).hasSize(1);

    // Disconnect session-1
    StompHeaderAccessor disconnect = StompHeaderAccessor.create(StompCommand.DISCONNECT);
    disconnect.setSessionId("session-1");
    interceptor.preSend(MessageBuilder.createMessage(new byte[0], disconnect.getMessageHeaders()), channel);

    assertThat(config.getActiveSubscriptions(userId, orgA)).isEmpty();
  }
}
