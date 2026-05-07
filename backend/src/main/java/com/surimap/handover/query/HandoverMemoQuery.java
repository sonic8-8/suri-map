package com.surimap.handover.query;

import java.util.List;
import java.util.UUID;

/**
 * HandoverMemoQuery 소스 계약 포트 (S8.json §service_contracts HandoverMemoQuery.byContext).
 *
 * <p>byContext는 incidentId/opId/targetType/targetId 필터로 메모를 createdAt DESC로 반환한다.
 *
 * <p>소비자: S3-2 handover_memo slot, S3-2 handover_status slot
 */
public interface HandoverMemoQuery {

  /**
   * HandoverMemoQuery.byContext
   *
   * @param incidentId 사건 ID (필수)
   * @param opId OP ID (optional, null이면 사건 전체)
   * @param targetType memo_target_type (optional, null이면 전체)
   * @param targetId memo_target_id (optional, null이면 전체)
   * @return memoId/incidentId/opId/targetType/targetId/content/createdByAccountId/createdAt/version
   */
  List<HandoverMemoRow> byContext(UUID incidentId, UUID opId, String targetType, UUID targetId);
}
