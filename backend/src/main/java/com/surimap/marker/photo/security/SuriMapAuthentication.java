package com.surimap.marker.photo.security;

import java.util.UUID;

public record SuriMapAuthentication(UUID accountId, String channel, UUID policePhoneId) {}
