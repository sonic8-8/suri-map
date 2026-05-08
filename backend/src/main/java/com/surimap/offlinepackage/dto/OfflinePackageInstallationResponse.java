package com.surimap.offlinepackage.dto;

import java.time.OffsetDateTime;

public record OfflinePackageInstallationResponse(
    String id,
    String status,
    long version,
    int manifestVersion,
    boolean readyForOfflineUse,
    OffsetDateTime serverTs) {}
