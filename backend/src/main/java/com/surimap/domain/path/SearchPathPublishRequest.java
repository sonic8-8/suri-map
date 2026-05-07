package com.surimap.domain.path;

import java.util.UUID;

public record SearchPathPublishRequest(
    SearchPathEventType eventType,
    UUID id,
    UUID opId,
    UUID policePhoneId,
    SearchPathStatus status,
    long version) {}
