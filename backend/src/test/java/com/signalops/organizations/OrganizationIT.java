package com.signalops.organizations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.signalops.support.IntegrationTestBase;
import com.signalops.support.TestHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@Tag("integration")
class OrganizationIT extends IntegrationTestBase {

  @Test
  void foreignOrganizationHeaderBehavesAsNotFound() throws Exception {
    var orgA = api.registerAndLogin(api.uniqueEmail(), "Org A");
    var orgB = api.registerAndLogin(api.uniqueEmail(), "Org B");

    mockMvc
        .perform(
            api.anonGet("/api/v1/organizations/current")
                .header("Authorization", api.bearer(orgA.accessToken()))
                .header("X-Organization-Id", orgB.organizationId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void missingOrganizationHeaderIsRejected() throws Exception {
    var session = api.registerAndLogin(api.uniqueEmail(), "Org");
    mockMvc
        .perform(
            api.anonGet("/api/v1/organizations/current")
                .header("Authorization", api.bearer(session.accessToken())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ORG_CONTEXT_REQUIRED"));
  }

  @Test
  void viewerCannotUpdateOrganizationButAdminCan() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Perm Org");
    String memberId = inviteAndAccept(owner, "viewer-invite@example.com", "VIEWER", TestHarness.DEMO_PASSWORD);
    var viewer = api.login("viewer-invite@example.com", TestHarness.DEMO_PASSWORD, owner.organizationId());

    mockMvc
        .perform(
            api.patch("/api/v1/organizations/current", viewer, "{\"name\":\"New Name\"}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(api.patch("/api/v1/organizations/current", owner, "{\"name\":\"Renamed Org\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed Org"));
  }

  @Test
  void memberRoleCanBeChangedAndLastOwnerIsProtected() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Guard Org");
    String responderMemberId = inviteAndAccept(owner, "responder@example.com", "RESPONDER", TestHarness.DEMO_PASSWORD);

    mockMvc
        .perform(
            api.patch(
                "/api/v1/members/" + responderMemberId,
                owner,
                "{\"role\":\"INCIDENT_COMMANDER\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("INCIDENT_COMMANDER"));

    // Demoting the only active owner must fail.
    String ownerMemberId = api.memberIdForUser(owner, owner.userId());
    mockMvc
        .perform(
            api.patch("/api/v1/members/" + ownerMemberId, owner, "{\"role\":\"ADMIN\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_LAST_OWNER"));

    mockMvc
        .perform(api.delete("/api/v1/members/" + ownerMemberId, owner))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_LAST_OWNER"));

    // A second owner makes the first removable.
    String secondOwnerId = inviteAndAccept(owner, "owner2@example.com", "ADMIN", TestHarness.DEMO_PASSWORD);
    mockMvc
        .perform(
            api.patch("/api/v1/members/" + secondOwnerId, owner, "{\"role\":\"OWNER\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(api.delete("/api/v1/members/" + api.memberIdForUser(owner, owner.userId()), owner))
        .andExpect(status().isNoContent());
  }

  @Test
  void invitationLifecycleSingleUseAndExpiry() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Invite Org");

    MvcResult createResult =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/invitations",
                    owner,
                    "{\"email\":\"joiner@example.com\",\"role\":\"RESPONDER\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String token =
        api.objectMapper()
            .readTree(createResult.getResponse().getContentAsString())
            .path("inviteToken")
            .asText();
    assertThat(token).isNotBlank();

    // A different user cannot accept someone else's invitation.
    var stranger = api.registerAndLogin(api.uniqueEmail(), "Other Org");
    mockMvc
        .perform(
            api.anonPost("/api/v1/invitations/accept", "{\"token\":\"" + token + "\"}")
                .header("Authorization", api.bearer(stranger.accessToken())))
        .andExpect(status().isForbidden());

    // The invited email must first exist: register the invitee, then accept.
    var invitee = api.registerAndLogin("joiner@example.com", "Invitee Org");
    mockMvc
        .perform(
            api.anonPost("/api/v1/invitations/accept", "{\"token\":\"" + token + "\"}")
                .header("Authorization", api.bearer(invitee.accessToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("RESPONDER"));

    // Single use: replaying the token fails.
    mockMvc
        .perform(
            api.anonPost("/api/v1/invitations/accept", "{\"token\":\"" + token + "\"}")
                .header("Authorization", api.bearer(invitee.accessToken())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVITATION_INVALID_OR_EXPIRED"));
  }

  @Test
  void expiredInvitationIsRejected() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Expiry Org");
    MvcResult createResult =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/invitations",
                    owner,
                    "{\"email\":\"late@example.com\",\"role\":\"VIEWER\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String token =
        api.objectMapper()
            .readTree(createResult.getResponse().getContentAsString())
            .path("inviteToken")
            .asText();

    // Backdate the invitation past its 7-day validity.
    backdateAllInvitations();

    var invitee = api.registerAndLogin("late@example.com", "Late Org");
    mockMvc
        .perform(
            api.anonPost("/api/v1/invitations/accept", "{\"token\":\"" + token + "\"}")
                .header("Authorization", api.bearer(invitee.accessToken())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVITATION_INVALID_OR_EXPIRED"));
  }

  @Test
  void disabledMemberLosesAccess() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Disable Org");
    String memberId = inviteAndAccept(owner, "temp@example.com", "RESPONDER", TestHarness.DEMO_PASSWORD);
    var member = api.login("temp@example.com", TestHarness.DEMO_PASSWORD, owner.organizationId());
    mockMvc.perform(api.get("/api/v1/organizations/current", member)).andExpect(status().isOk());

    mockMvc
        .perform(
            api.patch("/api/v1/members/" + memberId, owner, "{\"status\":\"DISABLED\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(api.get("/api/v1/organizations/current", member))
        .andExpect(status().isNotFound());
  }

  private String inviteAndAccept(
      TestHarness.Session owner, String email, String role, String password) throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/invitations",
                    owner,
                    "{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role)))
            .andExpect(status().isCreated())
            .andReturn();
    String token =
        api.objectMapper()
            .readTree(createResult.getResponse().getContentAsString())
            .path("inviteToken")
            .asText();
    var invitee = api.registerAndLogin(email, password, "Invited User", "Invitee Org " + email);
    mockMvc
        .perform(
            api.anonPost("/api/v1/invitations/accept", "{\"token\":\"" + token + "\"}")
                .header("Authorization", api.bearer(invitee.accessToken())))
        .andExpect(status().isOk());
    return findMemberIdByEmail(owner, email);
  }

  private String findMemberIdByEmail(TestHarness.Session owner, String email) throws Exception {
    String body =
        mockMvc
            .perform(api.get("/api/v1/organizations/current/members", owner))
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (var member : api.objectMapper().readTree(body).path("items")) {
      if (email.equalsIgnoreCase(member.path("email").asText())) {
        return member.path("memberId").asText();
      }
    }
    throw new AssertionError("Member not found: " + email);
  }

  private void backdateAllInvitations() {
    // Direct repository update would need a transaction; use a tiny SQL-free approach:
    // the test harness exposes no repo, so we instead rely on the service-level expiry check
    // via a dedicated endpoint-free path. For integration simplicity we update via JPA through
    // the application context.
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
        applicationContext.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
    jdbcTemplate.update("UPDATE invitations SET expires_at = now() - interval '8 days'");
  }

  @Autowired
  private org.springframework.context.ApplicationContext applicationContext;
}

