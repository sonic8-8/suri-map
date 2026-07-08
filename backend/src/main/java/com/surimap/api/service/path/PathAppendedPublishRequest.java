package com.surimap.api.service.path;

import com.surimap.domain.path.SearchPathStatus;
import java.util.UUID;

public record PathAppendedPublishRequest(
    UUID id,
    UUID incidentId,
    SearchPathStatus status,
    long version,
    UUID opId,
    UUID policePhoneId,
    UUID accountId) {}
