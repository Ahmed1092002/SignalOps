package com.signalops.auth.app;

import com.signalops.shared.domain.Role;

public record MembershipDto(
    java.util.UUID organizationId,
    String organizationName,
    String organizationSlug,
    Role role,
    String status) {}
