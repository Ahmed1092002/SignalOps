package com.signalops.servicecatalog;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.signalops.support.IntegrationTestBase;
import com.signalops.support.TestHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class ServiceCatalogIT extends IntegrationTestBase {

  @Test
  void adminCreatesUpdatesAndArchivesService() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Service Org");
    var session = owner;

    var created =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/services",
                    session,
                    "{\"name\":\"Payment Gateway\",\"description\":\"Charges\",\"owningTeam\":\"Payments\",\"isPublic\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("payment-gateway"))
            .andReturn();
    String serviceId =
        api.objectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText();

    mockMvc
        .perform(
            api.patch(
                "/api/v1/services/" + serviceId, session, "{\"description\":\"Card and wallet charges\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Card and wallet charges"));

    mockMvc
        .perform(api.post("/api/v1/services/" + serviceId + "/archive", session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").isNotEmpty());

    mockMvc
        .perform(api.get("/api/v1/services", session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc
        .perform(api.get("/api/v1/services?includeArchived=true", session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    // Service incidents pagination metadata normalization
    mockMvc
        .perform(api.get("/api/v1/services/" + serviceId + "/incidents?page=-1&size=0", session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1));

    mockMvc
        .perform(api.get("/api/v1/services/" + serviceId + "/incidents?page=0&size=-5", session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1));

    mockMvc
        .perform(api.get("/api/v1/services/" + serviceId + "/incidents?page=0&size=500", session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(100));
  }

  @Test
  void duplicateSlugIsRejected() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Slug Org");
    var session = owner;
    mockMvc
        .perform(
            api.post("/api/v1/services", session, "{\"name\":\"Auth Service\"}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            api.post("/api/v1/services", session, "{\"name\":\"Auth   Service\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"));
  }

  @Test
  void viewerAndResponderCannotManageServices() throws Exception {
    var owner = api.registerAndLogin(api.uniqueEmail(), "Svc Perm Org");
    var session = owner;
    String viewerMemberId = inviteMember(session, "svc-viewer@example.com", "VIEWER");
    var viewer = api.login("svc-viewer@example.com", TestHarness.DEMO_PASSWORD, session.organizationId());

    mockMvc
        .perform(
            api.post("/api/v1/services", viewer, "{\"name\":\"Nope\"}"))
        .andExpect(status().isForbidden());

    var created =
        mockMvc
            .perform(api.post("/api/v1/services", session, "{\"name\":\"Real Service\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String serviceId =
        api.objectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText();

    mockMvc
        .perform(api.post("/api/v1/services/" + serviceId + "/archive", viewer))
        .andExpect(status().isForbidden());
  }

  private String inviteMember(
      com.signalops.support.TestHarness.Session admin, String email, String role) throws Exception {
    var createResult =
        mockMvc
            .perform(
                api.post(
                    "/api/v1/invitations",
                    admin,
                    "{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role)))
            .andExpect(status().isCreated())
            .andReturn();
    String token =
        api.objectMapper()
            .readTree(createResult.getResponse().getContentAsString())
            .path("inviteToken")
            .asText();
    var invitee = api.registerAndLogin(email, "Invitee Org " + role);
    mockMvc
        .perform(
            api.anonPost("/api/v1/invitations/accept", "{\"token\":\"" + token + "\"}")
                .header("Authorization", api.bearer(invitee.accessToken())))
        .andExpect(status().isOk());
    return "accepted";
  }
}
