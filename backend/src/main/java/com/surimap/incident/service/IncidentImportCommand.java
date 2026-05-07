package com.surimap.incident.service;

import org.springframework.security.core.Authentication;

/** 사건 가져오기 use case 입력 — controller가 HTTP 요청을 service 시그니처로 변환한 형태. */
public record IncidentImportCommand(
    String sourceIncidentId,
    String idempotencyKey,
    String clientChannel,
    Authentication authentication) {}
