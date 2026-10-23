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

    // Disabling the only active owner must fail.
    mockMvc
        .perform(
            api.patch("/api/v1/members/" + ownerMemberId, owner, "{\"status\":\"DISABLED\"}"))
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

  @Test
  void concurrentDemoteBothOwnersPreservesAtLeastOneActiveOwner() throws Exception {
    var owner1 = api.registerAndLogin(api.uniqueEmail(), "Concurrent Org");
    String owner2MemberId = inviteAndAccept(owner1, "owner2@example.com", "ADMIN", TestHarness.DEMO_PASSWORD);
    mockMvc
        .perform(
            api.patch("/api/v1/members/" + owner2MemberId, owner1, "{\"role\":\"OWNER\"}"))
        .andExpect(status().isOk());

    String owner1MemberId = api.memberIdForUser(owner1, owner1.userId());

    int threadCount = 2;
    var executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
    var startLatch = new java.util.concurrent.CountDownLatch(1);
    var endLatch = new java.util.concurrent.CountDownLatch(threadCount);
    var statuses = new java.util.concurrent.atomic.AtomicIntegerArray(threadCount);

    executor.submit(() -> {
      try {
        startLatch.await();
        var result = mockMvc.perform(
            api.patch("/api/v1/members/" + owner1MemberId, owner1, "{\"role\":\"ADMIN\"}")
        ).andReturn();
        statuses.set(0, result.getResponse().getStatus());
      } catch (Exception e) {
        statuses.set(0, 500);
      } finally {
        endLatch.countDown();
      }
    });

    executor.submit(() -> {
      try {
        startLatch.await();
        var result = mockMvc.perform(
            api.patch("/api/v1/members/" + owner2MemberId, owner1, "{\"role\":\"ADMIN\"}")
        ).andReturn();
        statuses.set(1, result.getResponse().getStatus());
      } catch (Exception e) {
        statuses.set(1, 500);
      } finally {
        endLatch.countDown();
      }
    });

    startLatch.countDown();
    endLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
    executor.shutdown();

    int successCount = 0;
    int conflictCount = 0;
    for (int i = 0; i < threadCount; i++) {
      int s = statuses.get(i);
      if (s == 200) {
        successCount++;
      } else if (s == 409) {
        conflictCount++;
      }
    }

    assertThat(successCount).isEqualTo(1);
    assertThat(conflictCount).isEqualTo(1);

    // Verify at least one active owner remains
    String membersJson = mockMvc
        .perform(api.get("/api/v1/organizations/current/members", owner1))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
    long activeOwners = 0;
    for (var m : api.objectMapper().readTree(membersJson).path("items")) {
      if ("OWNER".equals(m.path("role").asText()) && "ACTIVE".equals(m.path("status").asText())) {
        activeOwners++;
      }
    }
    assertThat(activeOwners).isGreaterThanOrEqualTo(1);
  }

  @Test
  void paginationParametersAreSafelyValidatedAndNormalized() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Pagination Org");

    // Negative page and zero size normalized safely
    mockMvc
        .perform(api.get("/api/v1/organizations/current/members?page=-1&size=0", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.items").isArray());

    // Negative size normalized safely
    mockMvc
        .perform(api.get("/api/v1/organizations/current/members?page=0&size=-5", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1));

    // Size capped at 100
    mockMvc
        .perform(api.get("/api/v1/organizations/current/members?page=0&size=500", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(100));

    // Page beyond final page returns empty items
    mockMvc
        .perform(api.get("/api/v1/organizations/current/members?page=1000&size=20", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1000))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.items").isEmpty());

    // Very large page / size avoid integer overflow
    mockMvc
        .perform(api.get("/api/v1/organizations/current/members?page=2147483647&size=100", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(2147483647))
        .andExpect(jsonPath("$.items").isEmpty());
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

