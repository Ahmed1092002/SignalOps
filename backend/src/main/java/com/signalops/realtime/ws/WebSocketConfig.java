package com.signalops.realtime.ws;

import com.signalops.shared.security.AccessTokenVerifier;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Phase 2 realtime backbone. The access token travels on the STOMP CONNECT frame; subscriptions
 * are re-authorized per frame — an authenticated session may only subscribe to its own user
 * queue and to organizations where it holds an ACTIVE membership.
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final ObjectProvider<AccessTokenVerifier> verifierProvider;
  private final SubscriptionAuthorizer authorizer;

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
    registration.interceptors(new ChannelInterceptor() {

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
            log.error("STOMP CONNECT failed: {}", ex.toString());
            throw ex;
          }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
          Principal user = accessor.getUser();
          if (user == null) {
            throw new AccessDeniedException("Authentication required to subscribe.");
          }
          String destination = accessor.getDestination();
          if (destination != null && destination.startsWith("/user/")) {
            if (!destination.startsWith("/user/" + user.getName() + "/")) {
              throw new AccessDeniedException("Cannot subscribe to another user's queue.");
            }
            return message;
          }
          if (destination != null && destination.startsWith("/topic/org.")) {
            String orgId = extractSegment(destination, "/topic/org.", ".incidents", ".services");
            if (orgId == null || !authorizer.isActiveMember(UUID.fromString(user.getName()), UUID.fromString(orgId))) {
              throw new AccessDeniedException("Not a member of this organization.");
            }
            return message;
          }
          if (destination != null && destination.startsWith("/topic/public.")) {
            return message;
          }
          throw new AccessDeniedException("Destination not allowed.");
        }
        return message;
      }
    });
  }

  private void authenticate(StompHeaderAccessor accessor) {
    log.warn("CONNECT native headers: {}", accessor.getMessageHeaders());
    AccessTokenVerifier verifier = verifierProvider.getIfAvailable();
    String authHeader = accessor.getFirstNativeHeader("Authorization");
    if (verifier == null || authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new AccessDeniedException("Missing bearer token.");
    }
    try {
      UUID userId = verifier.verify(authHeader.substring("Bearer ".length()));
      accessor.setUser(new StompPrincipal(userId.toString()));
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
