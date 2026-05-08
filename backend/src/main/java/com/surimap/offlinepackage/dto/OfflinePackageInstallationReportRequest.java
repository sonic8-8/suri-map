package com.surimap.offlinepackage.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record OfflinePackageInstallationReportRequest(
    String policePhoneId,
    String manifestId,
    int manifestVersion,
    String status,
    int totalItems,
    int completedItems,
    int failedItems,
    long version,
    OffsetDateTime clientTs,
    Boolean readyForOfflineUse,
    List<String> failedItemKeys,
    String lastError,
    long sequence,
    Long clockOffsetMs) {

  public boolean resolvedReadyForOfflineUse() {
    return readyForOfflineUse != null ? readyForOfflineUse : "READY".equals(status);
  }

  public List<String> resolvedFailedItemKeys() {
    return failedItemKeys == null ? List.of() : List.copyOf(failedItemKeys);
  }
}
