package com.surimap.retention.purge;

public enum IncidentDataPurgeStatus {
  PENDING,
  RUNNING,
  WAITING_FOR_SYNC,
  FAILED_RETRYABLE,
  COMPLETED
}
