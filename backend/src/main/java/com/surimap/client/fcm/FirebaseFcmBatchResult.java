package com.surimap.client.fcm;

import java.util.List;

public record FirebaseFcmBatchResult(
    int successCount, int failureCount, List<String> failedRecipients) {

  public FirebaseFcmBatchResult {
    failedRecipients = List.copyOf(failedRecipients);
  }
}
