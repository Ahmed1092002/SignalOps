package com.signalops.shared.config;

import com.signalops.shared.api.RequestIdFilter;
import com.signalops.shared.tenancy.TenantContextInterceptor;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class WebConfig implements WebMvcConfigurer {

  private final AppProperties properties;
  private final TenantContextInterceptor tenantContextInterceptor;

  public WebConfig(AppProperties properties, TenantContextInterceptor tenantContextInterceptor) {
    this.properties = properties;
    this.tenantContextInterceptor = tenantContextInterceptor;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    List<String> origins = properties.security().corsAllowedOrigins();
    registry
        .addMapping("/api/**")
        .allowedOrigins(origins == null ? new String[0] : origins.toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders(RequestIdFilter.REQUEST_ID_HEADER)
        .allowCredentials(true)
        .maxAge(3600);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(tenantContextInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns(
            "/api/v1/auth/**",
            "/api/v1/public/**",
            "/api/v1/organizations/mine",
            "/api/v1/invitations/accept");
  }
}
