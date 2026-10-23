package com.signalops.support;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
public abstract class IntegrationTestBase {

  protected static final String DEMO_PASSWORD = "Test-Passphrase-1";

  @Autowired
  protected MockMvc mockMvc;

  @Autowired
  protected TestHarness api;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> TestPostgres.instance().getJdbcUrl());
    registry.add("spring.datasource.username", () -> TestPostgres.instance().getUsername());
    registry.add("spring.datasource.password", () -> TestPostgres.instance().getPassword());
    byte[] secret = new byte[64];
    new SecureRandom().nextBytes(secret);
    String jwtSecret = Base64.getEncoder().encodeToString(secret);
    registry.add("signalops.security.jwt-secret", () -> jwtSecret);
    registry.add("signalops.security.access-ttl", () -> "PT15M");
    registry.add("signalops.security.refresh-ttl", () -> "P7D");
    registry.add("signalops.rate-limit.limit-per-minute", () -> "100000");
  }
}
