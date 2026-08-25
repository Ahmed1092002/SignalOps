package com.signalops.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final ObjectProvider<AccessTokenVerifier> verifierProvider;
  private final AuthenticationEntryPoint entryPoint;

  public JwtAuthenticationFilter(
      ObjectProvider<AccessTokenVerifier> verifierProvider, JsonAuthHandlers authHandlers) {
    this.verifierProvider = verifierProvider;
    this.entryPoint = authHandlers.entryPoint();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }
    AccessTokenVerifier verifier = verifierProvider.getIfAvailable();
    if (verifier == null) {
      chain.doFilter(request, response);
      return;
    }
    try {
      UUID userId = verifier.verify(header.substring(BEARER_PREFIX.length()));
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(new UserPrincipal(userId), null, List.of());
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (AuthenticationException ex) {
      SecurityContextHolder.clearContext();
      entryPoint.commence(request, response, ex);
      return;
    }
    chain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return false;
  }
}
