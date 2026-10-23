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
class Phase3FeaturesIT extends IntegrationTestBase {

  @Test
  void followUpTaskLifecycleWithPermissions() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Tasks Org");
    String serviceId = createService(owner);
    String incidentId = createIncident(owner, serviceId);

    String taskId = null;
    MvcResult created =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents/" + incidentId + "/follow-up-tasks",
                    owner,
                    "{\"title\":\"Add saturation alerting\",\"assigneeUserId\":\""
                        + owner.userId()
                        + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    taskId =
        api.objectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText();

    // Viewer cannot create tasks.
    inviteAndAccept(owner, "task-viewer@example.com", "VIEWER");
    var viewer = api.login("task-viewer@example.com", TestHarness.DEMO_PASSWORD, owner.organizationId());
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/follow-up-tasks",
                viewer,
                "{\"title\":\"Viewer task\"}"))
        .andExpect(status().isForbidden());

    // Assignee completes the task; timeline records it.
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/follow-up-tasks/" + taskId + "/toggle",
                owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"));

    MvcResult detail =
        mockMvc.perform(api.get("/api/v1/incidents/" + incidentId, owner)).andReturn();
    assertThat(detail.getResponse().getContentAsString()).contains("FOLLOW_UP_COMPLETED");
  }

  @Test
  void attachmentsUploadListDownloadAndSizeCap() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Files Org");
    String serviceId = createService(owner);
    String incidentId = createIncident(owner, serviceId);

    byte[] content = "hello signalops attachment".getBytes();
    var upload =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .multipart("/api/v1/incidents/" + incidentId + "/attachments")
                    .file(new org.springframework.mock.web.MockMultipartFile(
                        "file", "notes.txt", "text/plain", content))
                    .header("Authorization", api.bearer(owner.accessToken()))
                    .header("X-Organization-Id", owner.organizationId()))
            .andExpect(status().isCreated())
            .andReturn();
    String attachmentId =
        api.objectMapper().readTree(upload.getResponse().getContentAsString()).path("id").asText();

    MvcResult download =
        mockMvc
            .perform(
                api.get(
                    "/api/v1/incidents/" + incidentId + "/attachments/" + attachmentId + "/download",
                    owner))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(content);

    // Oversized attachment rejected (cap 5 MiB).
    byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/v1/incidents/" + incidentId + "/attachments")
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "file", "big.bin", "application/octet-stream", tooBig))
                .header("Authorization", api.bearer(owner.accessToken()))
                .header("X-Organization-Id", owner.organizationId()))
        .andExpect(status().isBadRequest());

    // Cross-tenant download behaves as not found.
    var stranger = api.registerAndLogin(api.uniqueEmail(), "Files Other Org");
    mockMvc
        .perform(
            api.get(
                "/api/v1/incidents/" + incidentId + "/attachments/" + attachmentId + "/download",
                stranger))
        .andExpect(status().isNotFound());

    // Cross-incident download within same org behaves as not found.
    String otherIncidentId = createIncident(owner, serviceId);
    mockMvc
        .perform(
            api.get(
                "/api/v1/incidents/" + otherIncidentId + "/attachments/" + attachmentId + "/download",
                owner))
        .andExpect(status().isNotFound());
  }

  @Test
  void maintenanceWindowOverridesDerivedStatus() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Maintenance Org");
    String serviceId = createService(owner);

    // Create an active maintenance window (now -> +2h).
    var start = java.time.Instant.now().minusSeconds(60);
    var end = java.time.Instant.now().plusSeconds(7200);
    mockMvc
        .perform(
            api.post(
                "/api/v1/maintenance-windows",
                owner,
                """
                {"serviceId":"%s","title":"Routine patching","startsAt":"%s","endsAt":"%s"}
                """
                    .formatted(serviceId, start, end)))
        .andExpect(status().isCreated());

    // No incidents: derived OPERATIONAL, but window overrides to MAINTENANCE on the dashboard.
    MvcResult summary =
        mockMvc.perform(api.get("/api/v1/dashboard/summary", owner)).andExpect(status().isOk()).andReturn();
    String body = summary.getResponse().getContentAsString();
    assertThat(body).contains("MAINTENANCE");

    // Viewer cannot schedule maintenance.
    inviteAndAccept(owner, "maint-viewer@example.com", "VIEWER");
    var viewer = api.login("maint-viewer@example.com", TestHarness.DEMO_PASSWORD, owner.organizationId());
    mockMvc
        .perform(
            api.post(
                "/api/v1/maintenance-windows",
                viewer,
                """
                {"serviceId":"%s","title":"Nope","startsAt":"%s","endsAt":"%s"}
                """
                    .formatted(serviceId, start, end)))
        .andExpect(status().isForbidden());
  }

  @Test
  void analyticsOverviewReturnsSeriesAndDistributions() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Analytics Org");
    String serviceId = createService(owner);
    createIncident(owner, serviceId);

    mockMvc
        .perform(api.get("/api/v1/analytics/overview?days=14", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days").value(14))
        .andExpect(jsonPath("$.series.length()").value(14))
        .andExpect(jsonPath("$.severityDistribution.length()").value(4))
        .andExpect(jsonPath("$.severityDistribution[?(@.severity=='LOW')].count").value(1))
        .andExpect(jsonPath("$.perService[0].serviceName").value("Analytics Service"))
        .andExpect(jsonPath("$.perService[0].count").value(1));
  }

  private String createService(TestHarness.Session owner) throws Exception {
    MvcResult result =
        mockMvc
            .perform(api.post("/api/v1/services", owner, "{\"name\":\"Analytics Service\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return api.objectMapper().readTree(result.getResponse().getContentAsString()).path("id").asText();
  }

  private String createIncident(TestHarness.Session owner, String serviceId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents",
                    owner,
                    """
                    {"title":"Analytics incident","description":"","severity":"LOW","serviceIds":["%s"],"commanderUserId":null,"responderUserIds":[]}
                    """
                        .formatted(serviceId)))
            .andExpect(status().isCreated())
            .andReturn();
    return api.objectMapper().readTree(result.getResponse().getContentAsString()).path("id").asText();
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
