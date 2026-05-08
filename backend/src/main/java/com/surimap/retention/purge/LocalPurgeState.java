package com.surimap.retention.purge;

public enum LocalPurgeState {
  NOT_STARTED,
  PURGE_PENDING,
  WAITING_FOR_SYNC,
  LOCAL_PURGED,
  FAILED_RETRYABLE
}
