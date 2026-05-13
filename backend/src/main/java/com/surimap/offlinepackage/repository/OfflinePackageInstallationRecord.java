package com.surimap.offlinepackage.repository;

import com.surimap.offlinepackage.dto.OfflinePackageInstallationReportRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OfflinePackageInstallationRecord(
    UUID id,
    UUID manifestId,
    UUID incidentId,
    UUID policePhoneId,
    UUID lastReportedByAccountId,
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
      String manifestId,
      String policePhoneId,
      OfflinePackageInstallationReportRequest request,
      OffsetDateTime serverTs) {
    return new OfflinePackageInstallationRecord(
        UUID.fromString(id),
        UUID.fromString(manifestId),
        UUID.fromString(incidentId),
        UUID.fromString(policePhoneId),
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
