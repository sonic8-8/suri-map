package com.surimap.incident.service;

import java.util.UUID;
import org.springframework.security.core.Authentication;

/** 사건 종료 use case 입력 — controller가 HTTP 요청을 service 시그니처로 변환한 형태. */
public record IncidentCloseCommand(
    UUID incidentId,
    String idempotencyKey,
    String closeReason,
    Boolean confirmPersonalDataRemoval,
    Authentication authentication) {}
