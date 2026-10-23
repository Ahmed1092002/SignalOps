package com.signalops.incidents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.signalops.support.IntegrationTestBase;
import com.signalops.support.TestHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

@Tag("integration")
class IncidentLifecycleIT extends IntegrationTestBase {

  private record Ctx(TestHarness.Session session, String serviceId) {}

  private Ctx orgWithService() throws Exception {
    var session = api.registerAndLogin(api.uniqueEmail(), "Incident Org");
    MvcResult created =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/services", session, "{\"name\":\"Payment API\",\"owningTeam\":\"Payments\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String serviceId =
        api.objectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText();
    return new Ctx(session, serviceId);
  }

  @Test
  void createWithoutServiceIsRejected() throws Exception {
    var session = api.registerAndLogin(api.uniqueEmail(), "No Service Org");
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents",
                session,
                """
                {"title":"Orphan incident","description":"x","severity":"LOW","serviceIds":[],"commanderUserId":null,"responderUserIds":[]}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INCIDENT_REQUIRES_SERVICE"));
  }

  @Test
  void criticalWithoutCommanderIsRejected() throws Exception {
    Ctx ctx = orgWithService();
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents",
                ctx.session(),
                """
                {"title":"Total outage","description":"x","severity":"CRITICAL","serviceIds":["%s"],"commanderUserId":null,"responderUserIds":[]}
                """
                    .formatted(ctx.serviceId())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CRITICAL_REQUIRES_COMMANDER"));
  }

  @Test
  void fullLifecycleWithGuardsAndVersionConflicts() throws Exception {
    Ctx ctx = orgWithService();
    String commanderUserId = ctx.session().userId();
    MvcResult created = new MvcResult[] {null}[0];
    var createResult =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents",
                    ctx.session(),
                    """
                    {"title":"Payment transactions are failing","description":"500s on charges",
                     "severity":"CRITICAL","serviceIds":["%s"],"commanderUserId":"%s","responderUserIds":[]}
                    """
                        .formatted(ctx.serviceId(), commanderUserId)))
            .andExpect(status().isCreated())
            .andReturn();
    var incidentJson = api.objectMapper().readTree(createResult.getResponse().getContentAsString());
    String incidentId = incidentJson.path("id").asText();
    assertThat(incidentJson.path("status").asText()).isEqualTo("DETECTED");
    assertThat(incidentJson.path("key").asText()).isEqualTo("INC-1");
    long version = incidentJson.path("version").asLong();

    // DETECTED -> INVESTIGATING succeeds with the fresh version.
    var inInvestigating =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents/" + incidentId + "/transitions",
                    ctx.session(),
                    "{\"to\":\"INVESTIGATING\",\"version\":" + version + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INVESTIGATING"))
            .andExpect(jsonPath("$.acknowledgedAt").isNotEmpty())
            .andReturn();
    version =
        api.objectMapper().readTree(investigatingResponse(inInvestigating)).path("version").asLong();

    // Replaying the pre-transition version is a stale write (R12) and reports the current version.
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/transitions",
                ctx.session(),
                "{\"to\":\"IDENTIFIED\",\"version\":" + (version - 1) + "}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STALE_WRITE"))
        .andExpect(jsonPath("$.details[0].currentVersion").value(version));

    // INVESTIGATING -> RESOLVED is illegal (R7)
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/transitions",
                ctx.session(),
                "{\"to\":\"RESOLVED\",\"resolutionSummary\":\"premature\",\"version\":" + version + "}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_INCIDENT_TRANSITION"));

    version = transition(ctx.session(), incidentId, "IDENTIFIED", version, null);
    version = transition(ctx.session(), incidentId, "MONITORING", version, null);

    // MONITORING -> RESOLVED without summary is rejected (R2)
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/transitions",
                ctx.session(),
                "{\"to\":\"RESOLVED\",\"version\":" + version + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MISSING_RESOLUTION_SUMMARY"));

    // Resolve with summary: duration frozen (R8)
    var resolved =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents/" + incidentId + "/transitions",
                    ctx.session(),
                    """
                    {"to":"RESOLVED","resolutionSummary":"Rolled back deploy 482; error rate normal.","version":%d}
                    """
                        .formatted(version)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
            .andExpect(jsonPath("$.durationSeconds").isNumber())
            .andReturn();
    version = api.objectMapper().readTree(resolved.getResponse().getContentAsString()).path("version").asLong();

    // Reopen requires a reason (R13)
    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents/" + incidentId + "/transitions",
                ctx.session(),
                "{\"to\":\"INVESTIGATING\",\"version\":" + version + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REOPEN_REASON_REQUIRED"));

    var reopened =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents/" + incidentId + "/transitions",
                    ctx.session(),
                    "{\"to\":\"INVESTIGATING\",\"reason\":\"Regression spotted in monitoring.\",\"version\":" + version + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INVESTIGATING"))
            .andExpect(jsonPath("$.durationSeconds").doesNotExist())
            .andReturn();

    // Timeline holds the full story (R9), including the reopen.
    var timeline =
        mockMvc
            .perform(api.get("/api/v1/incidents/" + incidentId, ctx.session()))
            .andExpect(status().isOk())
            .andReturn();
    String timelineBody = timeline.getResponse().getContentAsString();
    assertThat(timelineBody).contains("INCIDENT_CREATED");
    assertThat(timelineBody).contains("STATUS_CHANGED");
    assertThat(timelineBody).contains("INCIDENT_RESOLVED");
    assertThat(timelineBody).contains("INCIDENT_REOPENED");
  }

  @Test
  void severityChangeToCriticalRequiresCommander() throws Exception {
    Ctx ctx = orgWithService();
    var created =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents",
                    ctx.session(),
                    """
                    {"title":"Latency rising","description":"","severity":"MEDIUM","serviceIds":["%s"],"commanderUserId":null,"responderUserIds":[]}
                    """
                        .formatted(ctx.serviceId())))
            .andExpect(status().isCreated())
            .andReturn();
    var json = api.objectMapper().readTree(created.getResponse().getContentAsString());
    String incidentId = json.path("id").asText();

    mockMvc
        .perform(
            api.put(
                "/api/v1/incidents/" + incidentId + "/severity",
                ctx.session(),
                "{\"severity\":\"CRITICAL\",\"version\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CRITICAL_REQUIRES_COMMANDER"));
  }

  @Test
  void commanderMustHoldEligibleRole() throws Exception {
    Ctx ctx = orgWithService();
    String responderMemberId = inviteMember(ctx.session(), "responder-cmd@example.com", "RESPONDER");
    String responderUserId = userIdOfMember(ctx.session(), responderMemberId);

    mockMvc
        .perform(
            api.post(
                "/api/v1/incidents",
                ctx.session(),
                """
                {"title":"Needs a real commander","description":"","severity":"HIGH","serviceIds":["%s"],"commanderUserId":"%s","responderUserIds":[]}
                """
                    .formatted(ctx.serviceId(), responderUserId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void archivedIncidentsAreReadOnlyAndFiltered() throws Exception {
    Ctx ctx = orgWithService();
    var created = createLowIncident(ctx);
    var json = api.objectMapper().readTree(created.getResponse().getContentAsString());
    String incidentId = json.path("id").asText();
    long version = json.path("version").asLong();

    mockMvc
        .perform(
            api.post("/api/v1/incidents/" + incidentId + "/archive", ctx.session(), "{\"version\":" + version + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").isNotEmpty());

    // Mutations rejected (R6/R23)
    mockMvc
        .perform(
            api.patch(
                "/api/v1/incidents/" + incidentId,
                ctx.session(),
                "{\"title\":\"Zombie edit\",\"version\":" + (version + 1) + "}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ARCHIVED_INCIDENT_READONLY"));

    // Excluded from the default list, present with archived=true (R23)
    mockMvc
        .perform(api.get("/api/v1/incidents", ctx.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mockMvc
        .perform(api.get("/api/v1/incidents?archived=true", ctx.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void crossOrganizationIncidentBehavesAsNotFound() throws Exception {
    Ctx ctxA = orgWithService();
    Ctx ctxB = orgWithService();
    var created = createLowIncident(ctxA);
    String incidentId =
        api.objectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText();

    mockMvc
        .perform(api.get("/api/v1/incidents/" + incidentId, ctxB.session()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    // And org B's incident list never contains org A's incident.
    var listB =
        mockMvc
            .perform(api.get("/api/v1/incidents", ctxB.session()))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(listB.getResponse().getContentAsString()).doesNotContain(incidentId);
  }

  @Test
  void viewerCannotMutateIncidents() throws Exception {
    Ctx ctx = orgWithService();
    var created = createLowIncident(ctx);
    var json = api.objectMapper().readTree(created.getResponse().getContentAsString());
    String incidentId = json.path("id").asText();

    String viewerMemberId = inviteMember(ctx.session(), "viewer-it@example.com", "VIEWER");
    var viewer = loginAsMember(ctx, "viewer-it@example.com", viewerMemberId);

    mockMvc
        .perform(
            api.patch(
                "/api/v1/incidents/" + incidentId,
                viewer,
                "{\"title\":\"Viewer was here\",\"version\":1}"))
        .andExpect(status().isForbidden());
  }

  private String investigatingResponse(MvcResult result)
      throws java.io.UnsupportedEncodingException {
    return result.getResponse().getContentAsString();
  }

  private long transition(TestHarness.Session session, String incidentId, String to, long version, String summary)
      throws Exception {
    String summaryJson = summary == null ? "" : ",\"resolutionSummary\":\"" + summary + "\"";
    MvcResult result =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents/" + incidentId + "/transitions",
                    session,
                    "{\"to\":\"%s\"%s,\"version\":%d}".formatted(to, summaryJson, version)))
            .andExpect(status().isOk())
            .andReturn();
    return api.objectMapper().readTree(result.getResponse().getContentAsString()).path("version").asLong();
  }

  private MvcResult createLowIncident(Ctx ctx) throws Exception {
    return mockMvc
        .perform(
            api.post(
                "/api/v1/incidents",
                ctx.session(),
                """
                {"title":"Minor flakiness","description":"Retries succeed","severity":"LOW","serviceIds":["%s"],"commanderUserId":null,"responderUserIds":[]}
                """
                    .formatted(ctx.serviceId())))
        .andExpect(status().isCreated())
        .andReturn();
  }

  @Test
  void removeCommanderReturnsFreshVersionForSubsequentMutation() throws Exception {
    var ctx = orgWithService();
    String commanderUserId = ctx.session().userId();
    var created =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/incidents",
                    ctx.session(),
                    """
                    {"title":"Commander removal test","description":"x","severity":"MEDIUM","serviceIds":["%s"],"commanderUserId":"%s","responderUserIds":[]}
                    """
                        .formatted(ctx.serviceId(), commanderUserId)))
            .andExpect(status().isCreated())
            .andReturn();
    long version = api.objectMapper().readTree(created.getResponse().getContentAsString()).path("version").asLong();
    String incidentId = api.objectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText();

    // Remove commander
    var removed =
        mockMvc
            .perform(
                api.put(
                    "/api/v1/incidents/" + incidentId + "/commander",
                    ctx.session(),
                    "{\"commanderUserId\":null,\"version\":" + version + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commander").doesNotExist())
            .andReturn();

    long versionAfterRemove =
        api.objectMapper().readTree(removed.getResponse().getContentAsString()).path("version").asLong();
    assertThat(versionAfterRemove).isGreaterThan(version);

    // Subsequent mutation using the returned version succeeds without optimistic lock conflict
    mockMvc
        .perform(
            api.put(
                "/api/v1/incidents/" + incidentId + "/severity",
                ctx.session(),
                "{\"severity\":\"HIGH\",\"version\":" + versionAfterRemove + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.severity").value("HIGH"))
        .andExpect(jsonPath("$.version").value(versionAfterRemove + 1));
  }

  @Test
  void concurrentIncidentCreationsGenerateUniqueSequentialKeys() throws Exception {
    var ctx = orgWithService();
    int count = 6;
    java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(count);
    java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(count);
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    java.util.List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();

    for (int i = 0; i < count; i++) {
      final int index = i;
      futures.add(
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                var result =
                    mockMvc
                        .perform(
                            api.post(
                                "/api/v1/incidents",
                                ctx.session(),
                                """
                                {"title":"Concurrent Inc %d","description":"x","severity":"LOW","serviceIds":["%s"],"commanderUserId":null,"responderUserIds":[]}
                                """
                                    .formatted(index, ctx.serviceId())))
                        .andExpect(status().isCreated())
                        .andReturn();
                return api.objectMapper()
                    .readTree(result.getResponse().getContentAsString())
                    .path("key")
                    .asText();
              }));
    }

    ready.await();
    start.countDown();

    java.util.Set<String> keys = new java.util.HashSet<>();
    for (var future : futures) {
      String key = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(key).startsWith("INC-");
      keys.add(key);
    }
    assertThat(keys).hasSize(count);
    executor.shutdown();
  }

  private String inviteMember(TestHarness.Session owner, String email, String role) throws Exception {
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
    return "member";
  }

  private TestHarness.Session loginAsMember(Ctx ctx, String email, String ignoredMemberId)
      throws Exception {
    return api.login(email, TestHarness.DEMO_PASSWORD, ctx.session().organizationId());
  }

  private String userIdOfMember(TestHarness.Session owner, String ignoredMemberId) throws Exception {
    String body =
        mockMvc
            .perform(api.get("/api/v1/organizations/current/members", owner))
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (var member : api.objectMapper().readTree(body).path("items")) {
      if ("responder-cmd@example.com".equalsIgnoreCase(member.path("email").asText())) {
        return member.path("userId").asText();
      }
    }
    throw new AssertionError("Member not found");
  }
}
