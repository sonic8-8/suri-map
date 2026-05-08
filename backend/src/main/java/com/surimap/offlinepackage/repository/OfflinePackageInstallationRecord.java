package com.surimap.offlinepackage.repository;

import com.surimap.offlinepackage.dto.OfflinePackageInstallationReportRequest;
import java.time.OffsetDateTime;
import java.util.List;

public record OfflinePackageInstallationRecord(
    String id,
    String manifestId,
    String incidentId,
    String policePhoneId,
    String lastReportedByAccountId,
    String status,
    int totalItemCount,
    int completedItemCount,
    int failedItemCount,
    List<String> failedItemKeys,
    String lastErrorCode,
    OffsetDateTime lastReportedAt,
    long version,
    long sequence,
    boolean readyForOfflineUse,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static OfflinePackageInstallationRecord from(
      String id,
      String incidentId,
      OfflinePackageInstallationReportRequest request,
      OffsetDateTime serverTs) {
    return new OfflinePackageInstallationRecord(
        id,
        request.manifestId(),
        incidentId,
        request.policePhoneId(),
        null,
        request.status(),
        request.totalItems(),
        request.completedItems(),
        request.failedItems(),
        request.resolvedFailedItemKeys(),
        request.lastError(),
        serverTs,
        request.version(),
        request.sequence(),
        request.resolvedReadyForOfflineUse(),
        serverTs,
        serverTs);
  }
}
