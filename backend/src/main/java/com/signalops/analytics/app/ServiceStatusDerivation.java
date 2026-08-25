package com.signalops.analytics.app;

import com.signalops.incidents.api.Severity;

/**
 * Pure derivation of the public service status from active incident severities (R10).
 * Severity mapping is the documented Phase 0 policy; the worst active incident wins.
 */
public final class ServiceStatusDerivation {

  public static final String OPERATIONAL = "OPERATIONAL";
  public static final String DEGRADED_PERFORMANCE = "DEGRADED_PERFORMANCE";
  public static final String PARTIAL_OUTAGE = "PARTIAL_OUTAGE";
  public static final String MAJOR_OUTAGE = "MAJOR_OUTAGE";
  public static final String MAINTENANCE = "MAINTENANCE";

  private ServiceStatusDerivation() {}

  /** LOW incidents do not degrade the public status page (noise reduction policy). */
  public static String fromSeverity(Severity severity) {
    return switch (severity) {
      case CRITICAL -> MAJOR_OUTAGE;
      case HIGH -> PARTIAL_OUTAGE;
      case MEDIUM -> DEGRADED_PERFORMANCE;
      case LOW -> OPERATIONAL;
    };
  }

  public static int rank(String status) {
    return switch (status) {
      case OPERATIONAL -> 0;
      case MAINTENANCE -> 1;
      case DEGRADED_PERFORMANCE -> 2;
      case PARTIAL_OUTAGE -> 3;
      case MAJOR_OUTAGE -> 4;
      default -> -1;
    };
  }

  public static String worst(String a, String b) {
    return rank(a) >= rank(b) ? a : b;
  }

  public static String derive(Iterable<Severity> activeSeverities) {
    String status = OPERATIONAL;
    for (Severity severity : activeSeverities) {
      status = worst(status, fromSeverity(severity));
    }
    return status;
  }
}
