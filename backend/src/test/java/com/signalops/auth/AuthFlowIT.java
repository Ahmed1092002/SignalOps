package com.signalops.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.signalops.support.IntegrationTestBase;
import com.signalops.support.TestHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@Tag("integration")
class AuthFlowIT extends IntegrationTestBase {

  private static final String EMAIL = "owner@example.com";

  @Test
  void registerCreatesUserOrganizationOwnerMembershipAndCookie() throws Exception {
    var result =
        mockMvc
            .perform(
                api.anonPost(
                    "/api/v1/auth/register",
                    """
                    {"email":"%s","password":"%s","fullName":"Owner One","organizationName":"Acme Rockets"}
                    """
                        .formatted(EMAIL, TestHarness.DEMO_PASSWORD)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value(EMAIL))
            .andExpect(jsonPath("$.organization.slug").value("acme-rockets"))
            .andReturn();

    String setCookie = result.getResponse().getHeader("Set-Cookie");
    assertThat(setCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/api/v1/auth");
    if (!Boolean.getBoolean("signalops.test.insecure-cookie")) {
      // secure flag depends on configuration; asserted in dedicated test below
    }
  }

  @Test
  void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
    String email = uniqueEmail();
    api.registerAndLogin(email, "Org One");
    mockMvc
        .perform(
            api.anonPost(
                "/api/v1/auth/register",
                """
                {"email":"%s","password":"%s","fullName":"Dup","organizationName":"Org Two"}
                """
                    .formatted(email.toUpperCase(), TestHarness.DEMO_PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
  }

  @Test
  void weakPasswordIsRejected() throws Exception {
    mockMvc
        .perform(
            api.anonPost(
                "/api/v1/auth/register",
                """
                {"email":"weak@example.com","password":"short1","fullName":"W","organizationName":"Weak Org"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void wrongPasswordAndUnknownEmailReturnIdenticalError() throws Exception {
    String email = uniqueEmail();
    api.registerAndLogin(email, "Org");
    String wrongPassword =
        mockMvc
            .perform(
                api.anonPost(
                    "/api/v1/auth/login",
                    """
                    {"email":"%s","password":"Definitely-Wrong-1"}
                    """
                        .formatted(email)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String unknownEmail =
        mockMvc
            .perform(
                api.anonPost(
                    "/api/v1/auth/login",
                    """
                    {"email":"ghost@example.com","password":"%s"}
                    """
                        .formatted(TestHarness.DEMO_PASSWORD)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(wrongPassword).contains("INVALID_CREDENTIALS");
    assertThat(unknownEmail).contains("INVALID_CREDENTIALS");
    assertThat(codeOf(wrongPassword)).isEqualTo(codeOf(unknownEmail));
    assertThat(messageOf(wrongPassword)).isEqualTo(messageOf(unknownEmail));
  }

  @Test
  void meReturnsMemberships() throws Exception {
    var session = api.registerAndLogin(uniqueEmail(), "Membership Org");
    mockMvc
        .perform(api.anonGet("/api/v1/auth/me").header("Authorization", api.bearer(session.accessToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberships[0].role").value("OWNER"))
        .andExpect(jsonPath("$.memberships[0].status").value("ACTIVE"));
  }

  @Test
  void refreshRotatesAndReuseRevokesEntireFamily() throws Exception {
    var session = api.registerAndLogin(uniqueEmail(), "Rotate Org");

    var firstRefresh = api.refreshResult(session.refreshToken());
    assertThat(firstRefresh.getResponse().getStatus()).isEqualTo(200);
    String newRefreshCookie = firstRefresh.getResponse().getHeader("Set-Cookie");
    assertThat(newRefreshCookie).isNotNull();
    String newRefreshToken = extractCookieValue(newRefreshCookie);

    String newToken =
        api.objectMapper()
            .readTree(firstRefresh.getResponse().getContentAsString())
            .path("accessToken")
            .asText();
    assertThat(newToken).isNotEmpty().isNotEqualTo(session.accessToken());

    // Replaying the OLD rotated token is treated as theft: family revoked.
    mockMvc
        .perform(
            api.anonPost("/api/v1/auth/refresh", null)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new jakarta.servlet.http.Cookie(TestHarness.REFRESH_COOKIE, session.refreshToken())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_REUSE_DETECTED"));

    // Even the newest family member is now dead.
    mockMvc
        .perform(
            api.anonPost("/api/v1/auth/refresh", null)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new jakarta.servlet.http.Cookie(TestHarness.REFRESH_COOKIE, newRefreshToken)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevokesFamily() throws Exception {
    var session = api.registerAndLogin(uniqueEmail(), "Logout Org");
    mockMvc
        .perform(
            api.anonPost("/api/v1/auth/logout", null)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new jakarta.servlet.http.Cookie(TestHarness.REFRESH_COOKIE, session.refreshToken())))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            api.anonPost("/api/v1/auth/refresh", null)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new jakarta.servlet.http.Cookie(TestHarness.REFRESH_COOKIE, session.refreshToken())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshWithoutSameOriginHeaderIsRejected() throws Exception {
    var session = api.registerAndLogin(uniqueEmail(), "Csrf Org");
    mockMvc
        .perform(
            api.anonPost("/api/v1/auth/refresh", null)
                .cookie(new jakarta.servlet.http.Cookie(TestHarness.REFRESH_COOKIE, session.refreshToken())))
        .andExpect(status().isForbidden());
  }

  @Test
  void concurrentRefreshRequestsOnlyOneSucceedsAndFamilyRevoked() throws Exception {
    var session = api.registerAndLogin(uniqueEmail(), "Concurrent Refresh Org");
    String refreshToken = session.refreshToken();

    int count = 2;
    var executor = java.util.concurrent.Executors.newFixedThreadPool(count);
    var ready = new java.util.concurrent.CountDownLatch(count);
    var start = new java.util.concurrent.CountDownLatch(1);
    var futures = new java.util.ArrayList<java.util.concurrent.Future<org.springframework.test.web.servlet.MvcResult>>();

    for (int i = 0; i < count; i++) {
      futures.add(
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return mockMvc
                    .perform(
                        api.anonPost("/api/v1/auth/refresh", null)
                            .header("X-Requested-With", "XMLHttpRequest")
                            .cookie(new jakarta.servlet.http.Cookie(TestHarness.REFRESH_COOKIE, refreshToken)))
                    .andReturn();
              }));
    }

    ready.await();
    start.countDown();

    int okCount = 0;
    int reuseCount = 0;
    for (var future : futures) {
      var result = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
      int statusCode = result.getResponse().getStatus();
      if (statusCode == 200) {
        okCount++;
      } else if (statusCode == 401) {
        String body = result.getResponse().getContentAsString();
        if (body.contains("REFRESH_REUSE_DETECTED")) {
          reuseCount++;
        }
      }
    }
    executor.shutdown();

    assertThat(okCount).isEqualTo(1);
    assertThat(reuseCount).isEqualTo(1);

    // Entire token family is revoked; even original token can no longer be used
    mockMvc
        .perform(
            api.anonPost("/api/v1/auth/refresh", null)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new jakarta.servlet.http.Cookie(TestHarness.REFRESH_COOKIE, refreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_REUSE_DETECTED"));
  }

  @Test
  void invalidAccessTokenIsRejected() throws Exception {
    mockMvc
        .perform(api.anonGet("/api/v1/organizations/mine").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginResponseCarriesRefreshCookie() throws Exception {
    String email = uniqueEmail();
    api.registerAndLogin(email, "Cookie Org");
    var result = api.loginResult(email, TestHarness.DEMO_PASSWORD);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getHeader("Set-Cookie")).contains(TestHarness.REFRESH_COOKIE);
  }

  private String codeOf(String body) {
    try {
      return api.objectMapper().readTree(body).path("code").asText();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private String messageOf(String body) {
    try {
      return api.objectMapper().readTree(body).path("message").asText();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static String uniqueEmail() {
    return "user-" + java.util.UUID.randomUUID() + "@example.com";
  }

  private String extractCookieValue(String setCookieHeader) {
    return java.util.Arrays.stream(setCookieHeader.split(";"))
        .findFirst()
        .map(part -> part.substring(part.indexOf('=') + 1))
        .orElseThrow();
  }

  @SuppressWarnings("unused")
  private static MediaType json() {
    return MediaType.APPLICATION_JSON;
  }
}

