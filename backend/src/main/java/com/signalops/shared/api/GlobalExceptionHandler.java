package com.signalops.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final RequestIdAccessor requestIds;

  public GlobalExceptionHandler(RequestIdAccessor requestIds) {
    this.requestIds = requestIds;
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
    return build(HttpStatus.resolve(ex.getStatus()), ex.getCode(), ex.getMessage(), ex.getDetails(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<Map<String, Object>> details =
        ex.getBindingResult().getAllErrors().stream()
            .map(
                error -> {
                  if (error instanceof FieldError fieldError) {
                    return Map.<String, Object>of(
                        "field", fieldError.getField(),
                        "code", fieldError.getCode() == null ? "INVALID" : fieldError.getCode(),
                        "message", fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage());
                  }
                  return Map.<String, Object>of(
                      "object", error.getObjectName(),
                      "code", error.getCode() == null ? "INVALID" : error.getCode(),
                      "message", error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage());
                })
            .toList();
    return build(
        HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed.", details, request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraint(
      ConstraintViolationException ex, HttpServletRequest request) {
    List<Map<String, Object>> details =
        ex.getConstraintViolations().stream()
            .map(
                violation ->
                    Map.<String, Object>of(
                        "field", violation.getPropertyPath().toString(),
                        "code", violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        "message", violation.getMessage()))
            .toList();
    return build(
        HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed.", details, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Malformed request body.", List.of(), request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    return build(
        HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Invalid parameter type.", List.of(), request);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(
      AuthenticationException ex, HttpServletRequest request) {
    return build(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, "Authentication required.", List.of(), request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    return build(HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, "You do not have permission to perform this action.", List.of(), request);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ErrorResponse> handleOptimisticLock(
      ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
    return build(
        HttpStatus.CONFLICT,
        ErrorCodes.STALE_WRITE,
        "This resource was modified by someone else. Reload and try again.",
        List.of(),
        request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
    return build(
        HttpStatus.CONFLICT,
        ErrorCodes.RESOURCE_CONFLICT,
        "The request conflicts with existing data.",
        List.of(),
        request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResource(
      NoResourceFoundException ex, HttpServletRequest request) {
    return build(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "Resource not found.", List.of(), request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "An unexpected error occurred.", List.of(), request);
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status,
      String code,
      String message,
      List<Map<String, Object>> details,
      HttpServletRequest request) {
    HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    ErrorResponse body =
        ErrorResponse.of(code, message, request.getRequestURI(), details, requestIds.current());
    return ResponseEntity.status(resolved).body(body);
  }
}
