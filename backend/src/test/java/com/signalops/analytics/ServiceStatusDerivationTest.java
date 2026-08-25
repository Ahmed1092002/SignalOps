package com.signalops.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.signalops.analytics.app.ServiceStatusDerivation;
import com.signalops.incidents.api.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceStatusDerivationTest {

  @Test
  void mapsSeveritiesToDocumentedPublicStatuses() {
    assertThat(ServiceStatusDerivation.fromSeverity(Severity.CRITICAL))
        .isEqualTo(ServiceStatusDerivation.MAJOR_OUTAGE);
    assertThat(ServiceStatusDerivation.fromSeverity(Severity.HIGH))
        .isEqualTo(ServiceStatusDerivation.PARTIAL_OUTAGE);
    assertThat(ServiceStatusDerivation.fromSeverity(Severity.MEDIUM))
        .isEqualTo(ServiceStatusDerivation.DEGRADED_PERFORMANCE);
    assertThat(ServiceStatusDerivation.fromSeverity(Severity.LOW))
        .isEqualTo(ServiceStatusDerivation.OPERATIONAL);
  }

  @Test
  void worstActiveIncidentWins() {
    assertThat(ServiceStatusDerivation.derive(List.of(Severity.MEDIUM, Severity.CRITICAL)))
        .isEqualTo(ServiceStatusDerivation.MAJOR_OUTAGE);
    assertThat(ServiceStatusDerivation.derive(List.of(Severity.LOW, Severity.MEDIUM)))
        .isEqualTo(ServiceStatusDerivation.DEGRADED_PERFORMANCE);
  }

  @Test
  void lowSeverityAloneKeepsServiceOperational() {
    assertThat(ServiceStatusDerivation.derive(List.of(Severity.LOW)))
        .isEqualTo(ServiceStatusDerivation.OPERATIONAL);
    assertThat(ServiceStatusDerivation.derive(List.of()))
        .isEqualTo(ServiceStatusDerivation.OPERATIONAL);
  }
}
