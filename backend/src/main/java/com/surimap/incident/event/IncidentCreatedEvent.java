package com.surimap.incident.event;

import java.util.List;
import java.util.UUID;

/** INCIDENT_CREATED payload에 필요한 L1 소유 필드. */
public record IncidentCreatedEvent(
    UUID id, String status, long version, UUID sourceIncidentId, List<String> memberAccountIds) {}
