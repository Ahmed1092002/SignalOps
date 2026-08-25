package com.signalops.incidents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.signalops.support.IntegrationTestBase;
import com.signalops.support.TestHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

@Tag("integration")
class CollaborationIT extends IntegrationTestBase {

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.context.ApplicationContext applicationContext;

  @Test
  void responderCommentsOnlyOnAssignedIncidents() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Collab Org");
    String serviceId = createService(owner);
    String incidentId = createIncident(owner, serviceId, "HIGH", null);

    // Invite responder, assign them, they can comment.
    inviteAndAccept(owner, "responder-a@example.com", "RESPONDER");
    var responder = api.login("responder-a@example.com", TestHarness.DEMO_PASSWORD, owner.organizationId());
    String responderUserId = userIdByEmail(owner, "responder-a@example.com");

    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/responders",
                owner,
                "{\"responderUserId\":\"%s\",\"version\":0}".formatted(responderUserId)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/comments",
                responder,
                "{\"body\":\"Error rate correlates with deploy 482.\"}"))
        .andExpect(status().isCreated());

    String comments =
        mockMvc
            .perform(api.get("/api/v1/incidents/" + incidentId + "/comments", owner))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(comments).contains("Error rate correlates with deploy 482.");

    // A different responder, NOT assigned, is rejected (R4).
    inviteAndAccept(owner, "responder-b@example.com", "RESPONDER");
    var outsider = api.login("responder-b@example.com", TestHarness.DEMO_PASSWORD, owner.organizationId());
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/comments",
                outsider,
                "{\"body\":\"I should not be able to post this.\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void viewerCannotComment() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Viewer Collab Org");
    String serviceId = createService(owner);
    String incidentId = createIncident(owner, serviceId, "LOW", null);
    inviteAndAccept(owner, "spectator@example.com", "VIEWER");
    var viewer = api.login("spectator@example.com", TestHarness.DEMO_PASSWORD, owner.organizationId());

    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/comments",
                viewer,
                "{\"body\":\"Viewer comment\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void timelineIsAtomicWithTheTransaction() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Atomic Org");
    String serviceId = createService(owner);
    String incidentId = createIncident(owner, serviceId, "MEDIUM", null);
    JdbcTemplate jdbc = applicationContext.getBean(JdbcTemplate.class);

    long before = countTimeline(jdbc, incidentId);
    assertThat(before).isEqualTo(1);

    // Failed transition (illegal) must NOT add a timeline row (R9 rollback semantics).
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/transitions",
                owner,
                "{\"to\":\"RESOLVED\",\"resolutionSummary\":\"too fast\",\"version\":0}"))
        .andExpect(status().isConflict());

    assertThat(countTimeline(jdbc, incidentId)).isEqualTo(1);

    // Successful transition adds exactly one row in the same transaction.
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/transitions",
                owner,
                "{\"to\":\"INVESTIGATING\",\"version\":0}"))
        .andExpect(status().isOk());

    assertThat(countTimeline(jdbc, incidentId)).isEqualTo(2);
  }

  @Test
  void publicUpdatesAndInternalCommentsAreSeparateModels() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Separation Org");
    String serviceId = createService(owner);
    String incidentId = createIncident(owner, serviceId, "CRITICAL", owner.userId());

    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/comments",
                owner,
                "{\"body\":\"INTERNAL: database credentials rotated.\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/public-updates",
                owner,
                "{\"message\":\"We are investigating elevated payment failures.\"}"))
        .andExpect(status().isCreated());

    // The internal comment lives in its own model and never appears in public payloads (R5/R14).
    String updates =
        mockMvc
            .perform(api.get("/api/v1/incidents/" + incidentId + "/public-updates", owner))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(updates).doesNotContain("INTERNAL");
    assertThat(updates).contains("We are investigating elevated payment failures.");

    // Published updates are immutable: no update/delete endpoints exist; a correction is a new row.
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/public-updates",
                owner,
                "{\"message\":\"Fix deployed; monitoring recovery.\"}"))
        .andExpect(status().isCreated());
    String updatesAfter =
        mockMvc
            .perform(api.get("/api/v1/incidents/" + incidentId + "/public-updates", owner))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(updatesAfter).contains("We are investigating elevated payment failures.");
    assertThat(updatesAfter).contains("Fix deployed; monitoring recovery.");
  }

  @Test
  void respondersMustBeActiveMembersOfTheOrganization() throws Exception {
    var orgA = api.registerAndLogin(api.uniqueEmail(), "Org A");
    var orgB = api.registerAndLogin(api.uniqueEmail(), "Org B");
    String serviceId = createService(orgA);
    String incidentId = createIncident(orgA, serviceId, "LOW", null);

    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/responders",
                orgA,
                "{\"responderUserId\":\"%s\",\"version\":0}".formatted(orgB.userId())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_ACTIVE"));
  }

  private long countTimeline(JdbcTemplate jdbc, String incidentId) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM timeline_events WHERE incident_id = CAST(? AS uuid)",
            Long.class,
            incidentId);
    return count == null ? 0 : count;
  }

  private String createService(TestHarness.Session owner) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                api.post("/api/v1/services", owner, "{\"name\":\"Checkout Web\",\"owningTeam\":\"Frontend\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return api.objectMapper().readTree(result.getResponse().getContentAsString()).path("id").asText();
  }

  private String createIncident(TestHarness.Session owner, String serviceId, String severity, String commanderUserId)
      throws Exception {
    String commanderJson = commanderUserId == null ? "null" : "\"" + commanderUserId + "\"";
    MvcResult result =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents",
                    owner,
                    """
                    {"title":"Checkout degradation","description":"Errors on checkout","severity":"%s","serviceIds":["%s"],"commanderUserId":%s,"responderUserIds":[]}
                    """
                        .formatted(severity, serviceId, commanderJson)))
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

  private String userIdByEmail(TestHarness.Session owner, String email) throws Exception {
    String body =
        mockMvc
            .perform(api.get("/api/v1/organizations/current/members", owner))
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (var member : api.objectMapper().readTree(body).path("items")) {
      if (email.equalsIgnoreCase(member.path("email").asText())) {
        return member.path("userId").asText();
      }
    }
    throw new AssertionError("Member not found: " + email);
  }
}
