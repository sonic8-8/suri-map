package com.surimap.account.service;

import java.util.UUID;

public record AuthLoginResult(
    UUID sessionId, String accessToken, SecurityContextSnapshot securityContext) {}
