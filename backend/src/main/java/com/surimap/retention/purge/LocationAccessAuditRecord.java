package com.surimap.retention.purge;

import java.time.Instant;
import java.util.UUID;

public record LocationAccessAuditRecord(
    UUID id,
    UUID incidentId,
    String accountId,
    UUID policePhoneId,
    String accessChannel,
    String accessPurpose,
    Instant accessedAt,
    Instant retentionUntil) {}
