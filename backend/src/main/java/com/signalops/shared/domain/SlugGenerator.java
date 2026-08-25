package com.signalops.shared.domain;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Deterministic, collision-safe slug generation for organization and service names. */
@Component
public class SlugGenerator {

  public String slugify(String name) {
    String slug =
        name.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "")
            .replaceAll("-{2,}", "-");
    if (slug.isEmpty()) {
      return "org";
    }
    if (slug.length() > 40) {
      slug = slug.substring(0, 40);
    }
    if (!slug.matches("^[a-z0-9]([a-z0-9-]{0,38}[a-z0-9])?$")) {
      slug = "org-" + Integer.toHexString(UUID.randomUUID().hashCode());
    }
    return slug;
  }

  public String uniqueSlug(String desired, java.util.function.Predicate<String> exists) {
    if (!exists.test(desired)) {
      return desired;
    }
    for (int attempt = 2; attempt < 100; attempt++) {
      String candidate = desired + "-" + attempt;
      if (!exists.test(candidate)) {
        return candidate;
      }
    }
    throw ApiExceptionHolder.conflict();
  }

  private static final class ApiExceptionHolder {
    private static com.signalops.shared.api.ApiException conflict() {
      return new com.signalops.shared.api.ApiException(
          com.signalops.shared.api.ErrorCodes.DUPLICATE_SLUG, "Could not derive a unique slug.", 409);
    }
  }
}
