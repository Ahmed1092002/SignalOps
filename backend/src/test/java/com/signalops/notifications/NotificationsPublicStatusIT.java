package com.signalops.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.signalops.support.IntegrationTestBase;
import com.signalops.support.TestHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

@Tag("integration")
class NotificationsPublicStatusIT extends IntegrationTestBase {

  @Test
  void responderAssignmentCriticalAndPublicUpdateGenerateNotifications() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Notify Org");
    String serviceId = createService(owner);
    String responderEmail = "notify-responder@example.com";
    inviteAndAccept(owner, responderEmail, "RESPONDER");
    var responder = api.login(responderEmail, TestHarness.DEMO_PASSWORD, owner.organizationId());

    // Responder assignment notifies the responder.
    String incidentId = createIncident(owner, serviceId, "HIGH", owner.userId(), responderEmail);
    mockMvc
        .perform(api.get("/api/v1/notifications", responder))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].type").value("RESPONDER_ASSIGNED"));

    // Severity raised to CRITICAL notifies participants (responder here; owner is the actor).
    long version = currentVersion(owner, incidentId);
    mockMvc
        .perform(
            api.put(
                "/api/v1/incidents/" + incidentId + "/severity",
                owner,
                "{\"severity\":\"CRITICAL\",\"version\":" + version + "}"))
        .andExpect(status().isOk());

    MvcResult responderNotifications =
        mockMvc.perform(api.get("/api/v1/notifications", responder)).andExpect(status().isOk()).andReturn();
    String body = responderNotifications.getResponse().getContentAsString();
    assertThat(body).contains("SEVERITY_CRITICAL");

    // Public update notifies participants; actor gets none of their own actions.
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/public-updates",
                owner,
                "{\"message\":\"Customer-safe status message.\"}"))
        .andExpect(status().isCreated());

    MvcResult after = mockMvc.perform(api.get("/api/v1/notifications", responder)).andReturn();
    assertThat(after.getResponse().getContentAsString()).contains("PUBLIC_UPDATE_PUBLISHED");

    // Unread count + mark-all-read.
    MvcResult count = mockMvc.perform(api.get("/api/v1/notifications/unread-count", responder)).andReturn();
    long unread = Long.parseLong(count.getResponse().getContentAsString().replaceAll("[^0-9]", ""));
    assertThat(unread).isGreaterThanOrEqualTo(3);

    mockMvc
        .perform(api.post("/api/v1/notifications/read-all", responder))
        .andExpect(status().isNoContent());
    MvcResult afterRead = mockMvc.perform(api.get("/api/v1/notifications/unread-count", responder)).andReturn();
    assertThat(afterRead.getResponse().getContentAsString()).contains("0");
  }

  @Test
  void publicStatusPageExposesOnlySafeFields() throws Exception {
    String ownerEmail = api.uniqueEmail();
    var owner = api.registerAndLogin(ownerEmail, "Public Org");
    String slug = owner.organizationId(); // not the slug; fetch current org for slug
    MvcResult orgResult = mockMvc.perform(api.get("/api/v1/organizations/current", owner)).andReturn();
    slug = api.objectMapper().readTree(orgResult.getResponse().getContentAsString()).path("slug").asText();

    String serviceId = createService(owner);
    String incidentId = createIncident(owner, serviceId, "CRITICAL", owner.userId(), null);
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/comments",
                owner,
                "{\"body\":\"INTERNAL-ONLY: secret rotation details.\"}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/public-updates",
                owner,
                "{\"message\":\"PUBLIC: we are investigating a payment outage.\"}"))
        .andExpect(status().isCreated());

    MvcResult publicStatus =
        mockMvc
            .perform(api.anonGet("/api/v1/public/status/" + slug))
            .andExpect(status().isOk())
            .andReturn();
    String payload = publicStatus.getResponse().getContentAsString();

    assertThat(payload).contains("PUBLIC: we are investigating a payment outage.");
    assertThat(payload).contains("MAJOR_OUTAGE");
    assertThat(payload).doesNotContain("INTERNAL-ONLY");
    assertThat(payload).doesNotContain(ownerEmail);
    assertThat(payload).doesNotContain(owner.userId());
    assertThat(payload).doesNotContain("commander");
    assertThat(payload).doesNotContain(incidentId);

    // Unknown slug behaves as not found.
    mockMvc
        .perform(api.anonGet("/api/v1/public/status/does-not-exist"))
        .andExpect(status().isNotFound());
  }

  private String createService(TestHarness.Session owner) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                api.post("/api/v1/services", owner, "{\"name\":\"Public Facing Service\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return api.objectMapper().readTree(result.getResponse().getContentAsString()).path("id").asText();
  }

  private String createIncident(
      TestHarness.Session owner, String serviceId, String severity, String commanderUserId, String responderEmail)
      throws Exception {
    String commanderJson = commanderUserId == null ? "null" : "\"" + commanderUserId + "\"";
    String respondersJson =
        responderEmail == null
            ? "[]"
            : "[\"" + userIdByEmail(owner, responderEmail) + "\"]";
    MvcResult result =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents",
                    owner,
                    """
                    {"title":"Notification flow incident","description":"","severity":"%s","serviceIds":["%s"],"commanderUserId":%s,"responderUserIds":%s}
                    """
                        .formatted(severity, serviceId, commanderJson, respondersJson)))
            .andExpect(status().isCreated())
            .andReturn();
    return api.objectMapper().readTree(result.getResponse().getContentAsString()).path("id").asText();
  }

  private long currentVersion(TestHarness.Session owner, String incidentId) throws Exception {
    MvcResult result = mockMvc.perform(api.get("/api/v1/incidents/" + incidentId, owner)).andReturn();
    return api.objectMapper().readTree(result.getResponse().getContentAsString()).path("incident").path("version").asLong();
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

  private String userIdByEmail(TestHarness.Session owner, String email) throws Exception {
    MvcResult result = mockMvc.perform(api.get("/api/v1/organizations/current/members", owner)).andReturn();
    for (var member : api.objectMapper().readTree(result.getResponse().getContentAsString()).path("items")) {
      if (email.equalsIgnoreCase(member.path("email").asText())) {
        return member.path("userId").asText();
      }
    }
    throw new AssertionError("Member not found");
  }
}
