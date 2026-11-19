package com.surimap.global.auth;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** 업무폰 ID가 표준 36자 UUID(8-4-4-4-12) 형식인지 확인한다. */
public final class PolicePhoneIdFormat {

  // 1-1-1-1-1처럼 구획 자릿수가 모자란 값도 UUID.fromString에서는 파싱되므로, 형식을 먼저 확인하게 했다.
  private static final Pattern CANONICAL_UUID =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private PolicePhoneIdFormat() {}

  public static Optional<UUID> parse(String policePhoneId) {
    if (policePhoneId == null || !CANONICAL_UUID.matcher(policePhoneId).matches()) {
      return Optional.empty();
    }
    return Optional.of(UUID.fromString(policePhoneId));
  }
}
