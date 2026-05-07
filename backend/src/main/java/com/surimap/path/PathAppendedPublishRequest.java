package com.surimap.path;

import java.util.UUID;

public record PathAppendedPublishRequest(
    UUID id, SearchPathStatus status, long version, UUID opId, UUID policePhoneId) {}
