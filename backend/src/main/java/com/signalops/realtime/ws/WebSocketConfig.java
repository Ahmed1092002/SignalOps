package com.signalops.realtime.ws;

import com.signalops.organizations.api.OrganizationEvents;
import com.signalops.shared.security.AccessTokenVerifier;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Phase 2 realtime backbone. The access token travels on the STOMP CONNECT frame; subscriptions
 * are re-authorized per frame — an authenticated session may only subscribe to its own user
 * queue and to organizations where it holds an ACTIVE membership.
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  public record ActiveSubscription(
      UUID userId, UUID organizationId, String sessionId, String subscriptionId, String destination) {
    public String key() {
      return sessionId + ":" + subscriptionId;
    }
  }

  private final ObjectProvider<AccessTokenVerifier> verifierProvider;
  private final SubscriptionAuthorizer authorizer;
  private final ObjectProvider<SubscribableChannel> clientInboundChannelProvider;
  private final ConcurrentHashMap<String, ActiveSubscription> subscriptionsByKey = new ConcurrentHashMap<>();

  @Autowired
  public WebSocketConfig(
      ObjectProvider<AccessTokenVerifier> verifierProvider,
      SubscriptionAuthorizer authorizer,
      @Qualifier("clientInboundChannel") ObjectProvider<SubscribableChannel> clientInboundChannelProvider) {
    this.verifierProvider = verifierProvider;
    this.authorizer = authorizer;
    this.clientInboundChannelProvider = clientInboundChannelProvider;
  }

  public WebSocketConfig(
      ObjectProvider<AccessTokenVerifier> verifierProvider,
      SubscriptionAuthorizer authorizer) {
    this(verifierProvider, authorizer, null);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(createInboundInterceptor());
  }

  ChannelInterceptor createInboundInterceptor() {
    return new ChannelInterceptor() {

      @Override
      public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
          return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
          try {
            authenticate(accessor);
          } catch (Exception ex) {
            log.error("STOMP CONNECT failed: {}", ex.getMessage());
            throw ex;
          }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
          Principal user = accessor.getUser();
          if (user == null) {
            throw new AccessDeniedException("Authentication required to subscribe.");
          }
          String destination = accessor.getDestination();
          if (destination != null && destination.startsWith("/user/")) {
            boolean isStandardUserQueue =
                destination.startsWith("/user/queue/")
                    || destination.startsWith("/user/topic/")
                    || destination.equals("/user/queue")
                    || destination.equals("/user/topic");
            boolean isExplicitOwnQueue =
                destination.startsWith("/user/" + user.getName() + "/")
                    || destination.equals("/user/" + user.getName());
            if (!isStandardUserQueue && !isExplicitOwnQueue) {
              throw new AccessDeniedException("Cannot subscribe to another user's queue.");
            }
            return message;
          }
          if (destination != null && destination.startsWith("/topic/org.")) {
            String orgIdStr = extractSegment(destination, "/topic/org.", ".incidents", ".services");
            if (orgIdStr == null || !authorizer.isActiveMember(UUID.fromString(user.getName()), UUID.fromString(orgIdStr))) {
              throw new AccessDeniedException("Not a member of this organization.");
            }
            UUID orgId = UUID.fromString(orgIdStr);
            UUID userId = UUID.fromString(user.getName());
            registerSubscription(userId, orgId, accessor.getSessionId(), accessor.getSubscriptionId(), destination);
            return message;
          }
          if (destination != null && destination.startsWith("/topic/public.")) {
            return message;
          }
          throw new AccessDeniedException("Destination not allowed.");
        } else if (StompCommand.UNSUBSCRIBE.equals(accessor.getCommand())) {
          unregisterSubscription(accessor.getSessionId(), accessor.getSubscriptionId());
          return message;
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
          unregisterSession(accessor.getSessionId());
          return message;
        }
        return message;
      }
    };
  }

  public void registerSubscription(
      UUID userId, UUID organizationId, String sessionId, String subscriptionId, String destination) {
    if (sessionId != null && subscriptionId != null) {
      subscriptionsByKey.put(
          sessionId + ":" + subscriptionId,
          new ActiveSubscription(userId, organizationId, sessionId, subscriptionId, destination));
    }
  }

  public void unregisterSubscription(String sessionId, String subscriptionId) {
    if (sessionId != null && subscriptionId != null) {
      subscriptionsByKey.remove(sessionId + ":" + subscriptionId);
    }
  }

  public void unregisterSession(String sessionId) {
    if (sessionId != null) {
      subscriptionsByKey.entrySet().removeIf(entry -> entry.getValue().sessionId().equals(sessionId));
    }
  }

  public List<ActiveSubscription> getActiveSubscriptions(UUID userId, UUID organizationId) {
    return subscriptionsByKey.values().stream()
        .filter(sub -> sub.userId().equals(userId) && sub.organizationId().equals(organizationId))
        .toList();
  }

  @EventListener
  public void onSessionDisconnect(SessionDisconnectEvent event) {
    unregisterSession(event.getSessionId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onMemberDisabled(OrganizationEvents.MemberDisabled event) {
    if (event != null && event.userId() != null) {
      revokeOrganizationSubscriptions(event.userId(), event.organizationId());
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onMemberRemoved(OrganizationEvents.MemberRemoved event) {
    if (event != null && event.userId() != null) {
      revokeOrganizationSubscriptions(event.userId(), event.organizationId());
    }
  }

  public void revokeOrganizationSubscriptions(UUID userId, UUID organizationId) {
    List<ActiveSubscription> toRevoke = getActiveSubscriptions(userId, organizationId);
    if (toRevoke.isEmpty()) {
      return;
    }
    SubscribableChannel inboundChannel =
        clientInboundChannelProvider != null ? clientInboundChannelProvider.getIfAvailable() : null;
    for (ActiveSubscription sub : toRevoke) {
      subscriptionsByKey.remove(sub.key());
      if (inboundChannel != null) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        accessor.setSessionId(sub.sessionId());
        accessor.setSubscriptionId(sub.subscriptionId());
        accessor.setUser(new StompPrincipal(userId.toString()));
        accessor.setLeaveMutable(true);
        inboundChannel.send(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
      }
    }
  }

  private void authenticate(StompHeaderAccessor accessor) {
    AccessTokenVerifier verifier = verifierProvider.getIfAvailable();
    String authHeader = accessor.getFirstNativeHeader("Authorization");
    if (verifier == null || authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new AccessDeniedException("Missing bearer token.");
    }
    try {
      UUID userId = verifier.verify(authHeader.substring("Bearer ".length()));
      accessor.setUser(new StompPrincipal(userId.toString()));
      log.debug("STOMP CONNECT authenticated successfully");
    } catch (Exception ex) {
      throw new AccessDeniedException("Invalid access token.");
    }
  }

  private String extractSegment(String destination, String prefix, String... suffixes) {
    String rest = destination.substring(prefix.length());
    for (String suffix : suffixes) {
      if (rest.endsWith(suffix)) {
        return rest.substring(0, rest.length() - suffix.length());
      }
    }
    return rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : null;
  }

  public record StompPrincipal(String name) implements Principal {
    @Override
    public String getName() {
      return name;
    }
  }
}
