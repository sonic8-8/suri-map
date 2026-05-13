package com.surimap.offlinepackage.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OfflinePackageManifestRecord(
    UUID id,
    UUID incidentId,
    int manifestVersion,
    UUID overallSearchAreaId,
    long overallSearchAreaVersion,
    String manifestHash,
    OffsetDateTime expiresAt) {}
