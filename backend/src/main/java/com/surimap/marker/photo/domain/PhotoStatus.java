package com.surimap.marker.photo.domain;

import java.util.Set;

public enum PhotoStatus {
  PENDING_UPLOAD,
  ATTACHED,
  FAILED,
  DELETED;

  public static Set<PhotoStatus> countedStatuses() {
    return Set.of(PENDING_UPLOAD, ATTACHED);
  }
}
