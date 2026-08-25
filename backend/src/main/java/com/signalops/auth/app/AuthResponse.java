package com.signalops.auth.app;

public record AuthResponse(String accessToken, UserDto user, OrganizationDto organization) {}
