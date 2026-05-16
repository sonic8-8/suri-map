package com.surimap.marker.notification.adapter;

import java.util.List;

public record FirebaseFcmBatchResult(
    int successCount, int failureCount, List<String> failedRecipients) {

  public FirebaseFcmBatchResult {
    failedRecipients = List.copyOf(failedRecipients);
  }
}
