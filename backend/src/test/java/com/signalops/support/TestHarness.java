package com.signalops.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * HTTP test harness: registers users/organizations and performs authenticated calls against
 * the MockMvc endpoint. Mirrors the real contract (bearer token + X-Organization-Id header).
 */
@Component
public class TestHarness {

  public static final String REFRESH_COOKIE = "signalops_refresh";
  public static final String DEMO_PASSWORD = "Test-Passphrase-1";

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  public TestHarness(MockMvc mockMvc, ObjectMapper objectMapper) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
  }

  public static String uniqueEmail() {
    return "user-" + java.util.UUID.randomUUID() + "@example.com";
  }

  public record Session(
      String accessToken, String refreshToken, String userId, String organizationId) {

    public Session withOrg(String orgId) {
      return new Session(accessToken, refreshToken, userId, orgId);
    }
  }

  public Session registerAndLogin(String email, String orgName) throws Exception {
    return registerAndLogin(email, DEMO_PASSWORD, "Test User", orgName);
  }

  public Session registerAndLogin(String email, String password, String fullName, String orgName)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                httpPost("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            java.util.Map.of(
                                "email", email,
                                "password", password,
                                "fullName", fullName,
                                "organizationName", orgName))))
            .andReturn();
    assertThat(result.getResponse().getStatus()).as("register %s", email).isEqualTo(201);
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return new Session(
        json.get("accessToken").asText(),
        refreshCookieFrom(result),
        json.path("user").path("id").asText(),
        json.path("organization").path("id").asText());
  }

  public Session login(String email, String password, String organizationId) throws Exception {
    MvcResult result = loginResult(email, password);
    assertThat(result.getResponse().getStatus()).as("login %s", email).isEqualTo(200);
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return new Session(
        json.get("accessToken").asText(),
        refreshCookieFrom(result),
        json.path("user").path("id").asText(),
        organizationId);
  }

  /** Resolves the membership id of a user inside the session's active organization. */
  public String memberIdForUser(Session session, String targetUserId) throws Exception {
    String body =
        mockMvc
            .perform(withAuth(httpGet("/api/v1/organizations/current/members"), session))
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (var member : objectMapper.readTree(body).path("items")) {
      if (member.path("userId").asText().equals(targetUserId)) {
        return member.path("memberId").asText();
      }
    }
    throw new AssertionError("Member not found for user " + targetUserId);
  }

  public Session loginDefaultOrg(String email, String password) throws Exception {
    MvcResult result = loginResult(email, password);
    assertThat(result.getResponse().getStatus()).as("login %s", email).isEqualTo(200);
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    String token = json.get("accessToken").asText();
    String me =
        mockMvc
            .perform(httpGet("/api/v1/auth/me").header("Authorization", bearer(token)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String orgId =
        objectMapper.readTree(me).path("memberships").get(0).path("organizationId").asText();
    return new Session(token, refreshCookieFrom(result), json.path("user").path("id").asText(), orgId);
  }

  public MvcResult loginResult(String email, String password) throws Exception {
    return mockMvc
        .perform(
            httpPost("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password))))
        .andReturn();
  }

  public MvcResult refreshResult(String refreshToken) throws Exception {
    return mockMvc
        .perform(
            httpPost("/api/v1/auth/refresh")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new Cookie(REFRESH_COOKIE, refreshToken)))
        .andReturn();
  }

  public MvcResult perform(MockHttpServletRequestBuilder builder) throws Exception {
    return mockMvc.perform(builder).andReturn();
  }

  public MockHttpServletRequestBuilder get(String path, Session session) {
    MockHttpServletRequestBuilder builder = httpGet(path);
    return withAuth(builder, session);
  }

  public MockHttpServletRequestBuilder post(String path, Session session, String jsonBody) {
    MockHttpServletRequestBuilder builder = httpPost(path);
    if (jsonBody != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
    }
    return withAuth(builder, session);
  }

  public MockHttpServletRequestBuilder post(String path, Session session) {
    return post(path, session, null);
  }

  public MockHttpServletRequestBuilder patch(String path, Session session, String jsonBody) {
    MockHttpServletRequestBuilder builder = httpPatch(path);
    if (jsonBody != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
    }
    return withAuth(builder, session);
  }

  public MockHttpServletRequestBuilder put(String path, Session session, String jsonBody) {
    MockHttpServletRequestBuilder builder = httpPut(path);
    if (jsonBody != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
    }
    return withAuth(builder, session);
  }

  public MockHttpServletRequestBuilder delete(String path, Session session) {
    return withAuth(httpDelete(path), session);
  }

  public MockHttpServletRequestBuilder anonGet(String path) {
    return httpGet(path);
  }

  public MockHttpServletRequestBuilder anonPost(String path, String jsonBody) {
    MockHttpServletRequestBuilder builder = httpPost(path);
    if (jsonBody != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
    }
    return builder;
  }

  private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, Session session) {
    builder.header("Authorization", bearer(session.accessToken()));
    if (session.organizationId() != null) {
      builder.header("X-Organization-Id", session.organizationId());
    }
    return builder;
  }

  public String bearer(String accessToken) {
    return "Bearer " + accessToken;
  }

  private String refreshCookieFrom(MvcResult result) {
    for (Cookie cookie : result.getResponse().getCookies()) {
      if (REFRESH_COOKIE.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    throw new AssertionError("Expected refresh cookie on response");
  }

  public ObjectMapper objectMapper() {
    return objectMapper;
  }

  public MockMvc mvc() {
    return mockMvc;
  }

  private static MockHttpServletRequestBuilder httpGet(String path) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path);
  }

  private static MockHttpServletRequestBuilder httpPost(String path) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path);
  }

  private static MockHttpServletRequestBuilder httpPatch(String path) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path);
  }

  private static MockHttpServletRequestBuilder httpPut(String path) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path);
  }

  private static MockHttpServletRequestBuilder httpDelete(String path) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path);
  }
}

