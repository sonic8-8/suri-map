package com.surimap.app.controller.policephone.response;

import com.surimap.policephone.FcmTokenStatus;
import com.surimap.policephone.query.FcmTokenRow;
import java.util.UUID;

public class FcmTokenResponse {

  private final UUID id;
  private final FcmTokenStatus status;
  private final long version;

  private FcmTokenResponse(UUID id, FcmTokenStatus status, long version) {
    this.id = id;
    this.status = status;
    this.version = version;
  }

  public static FcmTokenResponse from(FcmTokenRow row) {
    return new FcmTokenResponse(row.id(), row.status(), row.version());
  }

  public UUID getId() {
    return id;
  }

  public FcmTokenStatus getStatus() {
    return status;
  }

  public long getVersion() {
    return version;
  }
}
