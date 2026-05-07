package com.surimap.retention.purge;

public enum PurgeHookStatus {
  SUCCEEDED,
  WAITING_FOR_SYNC,
  FAILED_RETRYABLE
}
