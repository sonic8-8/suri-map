package com.surimap.incident.event;

import java.time.Instant;
import java.util.UUID;

/** INCIDENT_CLOSED payload에 필요한 L1 소유 terminal 필드. */
public record IncidentClosedEvent(
    UUID id, String status, long version, Instant closedAt, String writeDisabledReason) {}
