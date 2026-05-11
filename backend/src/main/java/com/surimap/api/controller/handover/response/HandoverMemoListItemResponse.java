package com.surimap.api.controller.handover.response;

import com.surimap.handover.query.HandoverMemoRow;
import java.time.Instant;
import java.util.UUID;

public record HandoverMemoListItemResponse(
    UUID id,
    UUID incidentId,
    UUID opId,
    String memoTargetType,
    UUID memoTargetId,
    String content,
    UUID createdByAccountId,
    Instant createdAt,
    long version) {

  public static HandoverMemoListItemResponse from(HandoverMemoRow row) {
    return new HandoverMemoListItemResponse(
        row.memoId(),
        row.incidentId(),
        row.opId(),
        row.targetType(),
        row.targetId(),
        row.content(),
        row.createdByAccountId(),
        row.createdAt(),
        row.version());
  }
}
