package com.surimap.operationalperiod.command;

/**
 * S8 OP2+ 수동 전환 command port (S8.json §api_contracts POST /operational-periods).
 *
 * <p>web commander가 reason과 함께 OP2+를 열고 이전 active OP를 닫는다. 구현체는 S8 production adapter가 담당한다.
 */
public interface ManualOpTransitionCommand {

  /**
   * @param request 전환 요청 (incidentId, reason, reasonMemo, handoverMemo, idempotencyKey)
   * @return 전환 결과 (새 ACTIVE OP row, ENDED 이전 OP row, 발행된 OP_TRANSITIONED event)
   */
  ManualOpTransitionResult transition(ManualOpTransitionRequest request);
}
