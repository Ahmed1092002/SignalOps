package com.signalops.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
    String code,
    String message,
    Instant timestamp,
    String path,
    List<Map<String, Object>> details,
    String requestId) {

  public static ErrorResponse of(
      String code, String message, String path, List<Map<String, Object>> details, String requestId) {
    return new ErrorResponse(code, message, Instant.now(), path, details, requestId);
  }
}
