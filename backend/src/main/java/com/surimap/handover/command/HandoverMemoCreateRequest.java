package com.surimap.handover.command;

import java.time.Instant;
import java.util.UUID;

/**
 * HandoverMemo 생성 서비스 요청 DTO (S8.json §api_contracts POST /handover-memos request_schema).
 *
 * <p>필수: incidentId, opId, memoTargetType, content, clientTs
 * <p>선택: memoTargetId (targetType=OPERATIONAL_PERIOD일 때 opId와 동일)
 */
public record HandoverMemoCreateRequest(
    UUID incidentId,
    UUID opId,
    String memoTargetType,
    UUID memoTargetId,
    String content,
    UUID createdByAccountId,
    String channel,
    Instant clientTs) {}
