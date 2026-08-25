package com.signalops.shared.api;

import java.util.List;
import java.util.Map;

public class ApiException extends RuntimeException {

  private final String code;
  private final int status;
  private final transient List<Map<String, Object>> details;

  public ApiException(String code, String message, int status) {
    this(code, message, status, List.of());
  }

  public ApiException(String code, String message, int status, List<Map<String, Object>> details) {
    super(message);
    this.code = code;
    this.status = status;
    this.details = details;
  }

  public String getCode() {
    return code;
  }

  public int getStatus() {
    return status;
  }

  public List<Map<String, Object>> getDetails() {
    return details;
  }

  public static ApiException notFound(String message) {
    return new ApiException(ErrorCodes.NOT_FOUND, message, 404);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(ErrorCodes.FORBIDDEN, message, 403);
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(code, message, 400);
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(code, message, 409);
  }
}
