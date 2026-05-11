package com.surimap.api.controller.handover.response;

import com.surimap.handover.HandoverMemo;
import java.util.UUID;

public record HandoverMemoResponse(
    UUID id, UUID opId, long version, String memoTargetType, UUID memoTargetId) {

  public static HandoverMemoResponse from(HandoverMemo memo) {
    return new HandoverMemoResponse(
        memo.getId(),
        memo.getOpId(),
        memo.getVersion(),
        memo.getMemoTargetType(),
        memo.getMemoTargetId());
  }
}
