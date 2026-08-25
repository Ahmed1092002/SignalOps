package com.signalops.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
public class OpenApiConfig {

  @Bean
  public OpenAPI signalOpsOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("SignalOps API")
                .version("v1")
                .description(
                    "Real-time incident management platform. Authenticated organization-scoped"
                        + " endpoints require the X-Organization-Id header."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Short-lived access token (15 minutes).")));
  }
}
