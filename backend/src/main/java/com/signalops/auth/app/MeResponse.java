package com.signalops.auth.app;

import java.util.List;

public record MeResponse(UserDto user, List<MembershipDto> memberships) {}
