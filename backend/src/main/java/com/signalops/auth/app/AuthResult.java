package com.signalops.auth.app;

public record AuthResult(
    String accessToken, String refreshToken, UserDto user, OrganizationDto organization) {}
