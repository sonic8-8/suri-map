package com.surimap.policephone.query;

import com.surimap.policephone.FcmTokenStatus;
import java.util.UUID;

public record FcmTokenRow(
    UUID id,
    UUID policePhoneId,
    String appInstanceId,
    String tokenCiphertext,
    String tokenHash,
    FcmTokenStatus status,
    long version) {

  public FcmTokenRow(
      UUID id,
      UUID policePhoneId,
      String appInstanceId,
      String tokenCiphertext,
      String tokenHash,
      FcmTokenStatus status,
      Long version) {
    this(id, policePhoneId, appInstanceId, tokenCiphertext, tokenHash, status, version == null ? 0L : version);
  }
}
