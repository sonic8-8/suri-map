package com.surimap.incident.service;

import java.util.UUID;
import org.springframework.security.core.Authentication;

/** 사건 가져오기 use case 입력 — controller가 HTTP 요청을 service 시그니처로 변환한 형태. */
public record IncidentImportCommand(
    UUID sourceIncidentId,
    String idempotencyKey,
    String clientChannel,
    Authentication authentication,
    boolean internal) {

  public IncidentImportCommand(
      UUID sourceIncidentId,
      String idempotencyKey,
      String clientChannel,
      Authentication authentication) {
    this(sourceIncidentId, idempotencyKey, clientChannel, authentication, false);
  }

  public static IncidentImportCommand internal(UUID sourceIncidentId, String idempotencyKey) {
    return new IncidentImportCommand(sourceIncidentId, idempotencyKey, "INTERNAL", null, true);
  }
}
