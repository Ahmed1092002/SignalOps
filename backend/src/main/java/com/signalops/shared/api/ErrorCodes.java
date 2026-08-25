package com.signalops.shared.api;

public final class ErrorCodes {

  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String FORBIDDEN = "FORBIDDEN";
  public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String RESOURCE_CONFLICT = "RESOURCE_CONFLICT";

  public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
  public static final String ACCESS_TOKEN_EXPIRED = "ACCESS_TOKEN_EXPIRED";
  public static final String REFRESH_REUSE_DETECTED = "REFRESH_REUSE_DETECTED";
  public static final String DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
  public static final String DUPLICATE_SLUG = "DUPLICATE_SLUG";
  public static final String INVITATION_INVALID_OR_EXPIRED = "INVITATION_INVALID_OR_EXPIRED";
  public static final String CANNOT_REMOVE_LAST_OWNER = "CANNOT_REMOVE_LAST_OWNER";
  public static final String ORG_CONTEXT_REQUIRED = "ORG_CONTEXT_REQUIRED";

  public static final String INVALID_INCIDENT_TRANSITION = "INVALID_INCIDENT_TRANSITION";
  public static final String MISSING_RESOLUTION_SUMMARY = "MISSING_RESOLUTION_SUMMARY";
  public static final String REOPEN_REASON_REQUIRED = "REOPEN_REASON_REQUIRED";
  public static final String CRITICAL_REQUIRES_COMMANDER = "CRITICAL_REQUIRES_COMMANDER";
  public static final String STALE_WRITE = "STALE_WRITE";
  public static final String ARCHIVED_INCIDENT_READONLY = "ARCHIVED_INCIDENT_READONLY";
  public static final String INCIDENT_REQUIRES_SERVICE = "INCIDENT_REQUIRES_SERVICE";
  public static final String MEMBER_NOT_ACTIVE = "MEMBER_NOT_ACTIVE";
  public static final String SERVICE_HAS_ACTIVE_INCIDENTS = "SERVICE_HAS_ACTIVE_INCIDENTS";

  private ErrorCodes() {}
}
