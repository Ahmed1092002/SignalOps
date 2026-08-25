package com.signalops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.signalops.support.IntegrationTestBase;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class ApplicationSmokeTest extends IntegrationTestBase {

  @Test
  void healthEndpointIsPublicAndUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void protectedPathsReturnTypedJsonEnvelope() throws Exception {
    mockMvc
        .perform(get("/api/v1/incidents"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty())
        .andExpect(jsonPath("$.path").value("/api/v1/incidents"));
  }

  @Test
  void requestIdHeaderIsEchoed() throws Exception {
    mockMvc
        .perform(get("/actuator/health").header("X-Request-Id", "test-req-1"))
        .andExpect(status().isOk())
        .andExpect(result -> Matchers.notNullValue());
  }
}
