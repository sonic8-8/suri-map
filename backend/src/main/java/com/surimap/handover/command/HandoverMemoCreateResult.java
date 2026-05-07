package com.surimap.handover.command;

import java.util.UUID;

/**
 * HandoverMemo 생성 결과 (S8.json §api_contracts POST /handover-memos response_schema 201).
 *
 * <p>id, opId, version, memoTargetType, memoTargetId
 */
public record HandoverMemoCreateResult(
    UUID id,
    UUID opId,
    long version,
    String memoTargetType,
    UUID memoTargetId) {}
