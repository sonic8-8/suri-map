package com.surimap.handover.testdouble;

import com.surimap.handover.fixture.HandoverMemoFixtures;
import com.surimap.handover.query.HandoverMemoQuery;
import com.surimap.handover.query.HandoverMemoRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * L3-T07 HandoverMemoQuery mock test double.
 *
 * <p>real DB query 없이 소비 Lane(S3-2 handover_memo/handover_status slot)이 byContext 계약을 검증한다.
 */
public final class HandoverMemoQueryMock implements HandoverMemoQuery {

  @Override
  public List<HandoverMemoRow> byContext(
      UUID incidentId, UUID opId, String targetType, UUID targetId) {

    if (!HandoverMemoFixtures.INCIDENT_ID.equals(incidentId)) {
      return Collections.emptyList();
    }

    if (opId != null && !HandoverMemoFixtures.OP2_ID.equals(opId)) {
      return Collections.emptyList();
    }

    List<HandoverMemoRow> result = new ArrayList<>();

    // OP context memo
    if (targetType == null || HandoverMemoFixtures.TARGET_TYPE_OP.equals(targetType)) {
      result.add(
          toRow(HandoverMemoFixtures.memoRow(HandoverMemoFixtures.TARGET_TYPE_OP, opId)));
    }

    // PATH context memo
    if (targetType == null || HandoverMemoFixtures.TARGET_TYPE_PATH.equals(targetType)) {
      UUID pathId = UUID.fromString("11111111-1111-1111-1111-111111110001");
      if (targetId == null || pathId.equals(targetId)) {
        result.add(
            toRow(HandoverMemoFixtures.memoRow(HandoverMemoFixtures.TARGET_TYPE_PATH, pathId)));
      }
    }

    // AREA context memo
    if (targetType == null || HandoverMemoFixtures.TARGET_TYPE_AREA.equals(targetType)) {
      UUID areaId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccc0001");
      if (targetId == null || areaId.equals(targetId)) {
        result.add(
            toRow(HandoverMemoFixtures.memoRow(HandoverMemoFixtures.TARGET_TYPE_AREA, areaId)));
      }
    }

    // filter by targetId if provided
    if (targetId != null) {
      result.removeIf(r -> !targetId.equals(r.targetId()));
    }

    return Collections.unmodifiableList(result);
  }

  private HandoverMemoRow toRow(HandoverMemoFixtures.HandoverMemoRow f) {
    return new HandoverMemoRow(
        f.memoId(),
        f.incidentId(),
        f.opId(),
        f.targetType(),
        f.targetId(),
        f.content(),
        f.createdByAccountId(),
        f.createdAt(),
        f.version());
  }
}
