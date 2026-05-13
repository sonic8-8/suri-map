package com.surimap.harness.sc10.mock;

import com.surimap.handover.command.HandoverMemoCreateCommand;
import com.surimap.handover.command.HandoverMemoCreateRequest;
import com.surimap.handover.command.HandoverMemoCreateResult;
import com.surimap.handover.query.HandoverMemoQuery;
import com.surimap.handover.query.HandoverMemoRow;
import com.surimap.harness.sc10.fixture.Sc10Fixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SC-10 harness handover memo mock port.
 *
 * <p>HandoverMemoCreateCommand + HandoverMemoQuery 를 모두 구현하여 write + query board convergence 검증.
 *
 * <p>기준 문서: docs/spec/specs/S8.json §service_contracts HandoverMemoQuery.byContext.
 */
public class MockHandoverMemoPort implements HandoverMemoCreateCommand, HandoverMemoQuery {

  private final List<HandoverMemoCreateRequest> capturedRequests = new ArrayList<>();
  private final List<HandoverMemoRow> committedMemos = new ArrayList<>();

  // ── HandoverMemoCreateCommand ────────────────────────────────────────────

  @Override
  public HandoverMemoCreateResult create(HandoverMemoCreateRequest request) {
    capturedRequests.add(request);

    HandoverMemoRow row = new HandoverMemoRow(
        Sc10Fixtures.MEMO_ID,
        request.incidentId(),
        request.opId(),
        request.memoTargetType(),
        request.memoTargetId() != null ? request.memoTargetId() : request.opId(),
        request.content(),
        request.createdByAccountId(),
        request.clientTs(),
        Sc10Fixtures.MEMO_VERSION);

    committedMemos.add(row);

    return new HandoverMemoCreateResult(
        Sc10Fixtures.MEMO_ID,
        request.opId(),
        Sc10Fixtures.MEMO_VERSION,
        request.memoTargetType(),
        row.targetId());
  }

  // ── HandoverMemoQuery ────────────────────────────────────────────────────

  @Override
  public List<HandoverMemoRow> byContext(
      UUID incidentId, UUID opId, String targetType, UUID targetId) {
    return committedMemos.stream()
        .filter(m -> incidentId == null || incidentId.equals(m.incidentId()))
        .filter(m -> opId == null || opId.equals(m.opId()))
        .filter(m -> targetType == null || targetType.equals(m.targetType()))
        .filter(m -> targetId == null || targetId.equals(m.targetId()))
        .toList();
  }

  // ── inspection helpers ───────────────────────────────────────────────────

  public List<HandoverMemoCreateRequest> capturedRequests() {
    return List.copyOf(capturedRequests);
  }

  public List<HandoverMemoRow> committedMemos() {
    return List.copyOf(committedMemos);
  }
}
