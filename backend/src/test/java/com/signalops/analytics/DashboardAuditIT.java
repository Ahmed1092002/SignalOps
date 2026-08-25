package com.signalops.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.signalops.support.IntegrationTestBase;
import com.signalops.support.TestHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

@Tag("integration")
class DashboardAuditIT extends IntegrationTestBase {

  @Test
  void summaryCountsMeanResolutionAndWorstServiceStatus() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Dash Org");
    String paymentsId = createService(owner, "Payments");
    String searchId = createService(owner, "Search");

    // Resolved CRITICAL on Payments (duration ~ frozen by resolve flow).
    String resolvedIncident =
        createIncident(owner, paymentsId, "CRITICAL", owner.userId());
    transition(owner, resolvedIncident, "INVESTIGATING", 0);
    long v = transition(owner, resolvedIncident, "IDENTIFIED", 1);
    v = transition(owner, resolvedIncident, "MONITORING", v);
    transitionWithSummary(owner, resolvedIncident, v, "Fixed and verified.");

    // Active MEDIUM on Payments + active LOW on Search.
    createIncident(owner, paymentsId, "MEDIUM", null);
    createIncident(owner, searchId, "LOW", null);

    var summary =
        mockMvc
            .perform(api.get("/api/v1/dashboard/summary", owner))
            .andExpect(status().isOk())
            .andReturn();
    var json = api.objectMapper().readTree(summary.getResponse().getContentAsString());

    assertThat(json.path("activeIncidents").asLong()).isEqualTo(2);
    assertThat(json.path("criticalIncidents").asLong()).isEqualTo(0);
    assertThat(json.path("incidentsThisMonth").asLong()).isEqualTo(3);
    assertThat(json.path("meanResolutionSeconds").asDouble()).isGreaterThanOrEqualTo(0);

    // R10: Payments has an active MEDIUM -> DEGRADED_PERFORMANCE; Search has LOW -> OPERATIONAL.
    for (var service : json.path("serviceHealth")) {
      if ("Payments".equals(service.path("name").asText())) {
        assertThat(service.path("status").asText()).isEqualTo("DEGRADED_PERFORMANCE");
        assertThat(service.path("activeIncidents").asLong()).isEqualTo(1);
      }
      if ("Search".equals(service.path("name").asText())) {
        assertThat(service.path("status").asText()).isEqualTo("OPERATIONAL");
      }
    }
    assertThat(json.path("recentActivity").size()).isGreaterThan(0);
  }

  @Test
  void serviceWithActiveIncidentCannotBeArchived() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "R18 Org");
    String serviceId = createService(owner, "Gateway");
    createIncident(owner, serviceId, "HIGH", null);

    mockMvc
        .perform(api.post("/api/v1/services/" + serviceId + "/archive", owner))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SERVICE_HAS_ACTIVE_INCIDENTS"));
  }

  @Test
  void auditLogIsOwnerAdminOnlyAndFilterable() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Audit Org");
    String serviceId = createService(owner, "Audit Service");

    // Owner/Admin can read; audit rows exist for service create.
    MvcResult result =
        mockMvc
            .perform(
                api.get("/api/v1/audit-events?action=SERVICE_CREATED", owner))
            .andExpect(status().isOk())
            .andReturn();
    var json = api.objectMapper().readTree(result.getResponse().getContentAsString());
    assertThat(json.path("totalElements").asLong()).isGreaterThanOrEqualTo(1);
    assertThat(json.path("items").get(0).path("action").asText()).isEqualTo("SERVICE_CREATED");

    // Non-admin roles are rejected.
    inviteAndAccept(owner, "aud-viewer@example.com", "VIEWER");
    var viewer = api.login("aud-viewer@example.com", TestHarness.DEMO_PASSWORD, owner.organizationId());
    mockMvc
        .perform(api.get("/api/v1/audit-events", viewer))
        .andExpect(status().isForbidden());

    // Filters narrow results to zero for a nonsense action.
    mockMvc
        .perform(api.get("/api/v1/audit-events?action=NONEXISTENT", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  private String createService(TestHarness.Session owner, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                api.post("/api/v1/services", owner, "{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
    return api.objectMapper().readTree(result.getResponse().getContentAsString()).path("id").asText();
  }

  private String createIncident(TestHarness.Session owner, String serviceId, String severity, String commander)
      throws Exception {
    String commanderJson = commander == null ? "null" : "\"" + commander + "\"";
    MvcResult result =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents",
                    owner,
                    """
                    {"title":"Dash incident","description":"","severity":"%s","serviceIds":["%s"],"commanderUserId":%s,"responderUserIds":[]}
                    """
                        .formatted(severity, serviceId, commanderJson)))
            .andExpect(status().isCreated())
            .andReturn();
    return api.objectMapper().readTree(result.getResponse().getContentAsString()).path("id").asText();
  }

  private long transition(TestHarness.Session owner, String incidentId, String to, long version) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents/" + incidentId + "/transitions",
                    owner,
                    "{\"to\":\"%s\",\"version\":%d}".formatted(to, version)))
            .andExpect(status().isOk())
            .andReturn();
    return api.objectMapper().readTree(result.getResponse().getContentAsString()).path("version").asLong();
  }

  private long transitionWithSummary(TestHarness.Session owner, String incidentId, long version, String summary)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents/" + incidentId + "/transitions",
                    owner,
                    "{\"to\":\"RESOLVED\",\"resolutionSummary\":\"%s\",\"version\":%d}".formatted(summary, version)))
            .andExpect(status().isOk())
            .andReturn();
    return api.objectMapper().readTree(result.getResponse().getContentAsString()).path("version").asLong();
  }

  private void inviteAndAccept(TestHarness.Session owner, String email, String role) throws Exception {
    var createResult =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/invitations", owner, "{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role)))
            .andExpect(status().isCreated())
            .andReturn();
    String token =
        api.objectMapper()
            .readTree(createResult.getResponse().getContentAsString())
            .path("inviteToken")
            .asText();
    var invitee = api.registerAndLogin(email, "Invitee " + role);
    mockMvc
        .perform(
            api.anonPost("/api/v1/invitations/accept", "{\"token\":\"" + token + "\"}")
                .header("Authorization", api.bearer(invitee.accessToken())))
        .andExpect(status().isOk());
  }
}
