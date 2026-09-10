package com.surimap.global.auth;

import java.util.UUID;

public record SuriMapAuthentication(UUID accountId, String channel, UUID policePhoneId) {}
