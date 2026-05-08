package com.surimap.offlinepackage.repository;

import java.time.OffsetDateTime;

public record OfflinePackageManifestRecord(
    String id,
    String incidentId,
    int manifestVersion,
    String overallSearchAreaId,
    long overallSearchAreaVersion,
    String manifestHash,
    OffsetDateTime expiresAt) {}
