package com.signalops.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.api.ErrorResponse;
import com.signalops.shared.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class JsonAuthHandlers {

  private final ObjectMapper objectMapper;

  public JsonAuthHandlers(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public AuthenticationEntryPoint entryPoint() {
    return (request, response, ex) -> {
      String code =
          ex instanceof ExpiredAccessTokenException
              ? "ACCESS_TOKEN_EXPIRED"
              : ErrorCodes.UNAUTHENTICATED;
      writeError(response, HttpStatus.UNAUTHORIZED, code, "Authentication required.", request);
    };
  }

  public AccessDeniedHandler accessDeniedHandler() {
    return (request, response, ex) ->
        writeError(
            response,
            HttpStatus.FORBIDDEN,
            ErrorCodes.FORBIDDEN,
            "You do not have permission to perform this action.",
            request);
  }

  private void writeError(
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String message,
      HttpServletRequest request)
      throws java.io.IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ErrorResponse body =
        new ErrorResponse(
            code,
            message,
            Instant.now(),
            request.getRequestURI(),
            List.of(),
            (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
    objectMapper.writeValue(response.getWriter(), body);
  }
}
