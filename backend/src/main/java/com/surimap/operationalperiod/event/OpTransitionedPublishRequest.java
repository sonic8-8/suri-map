package com.surimap.operationalperiod.event;

import java.util.UUID;

/** S8 OP_TRANSITIONED 이벤트 발행 요청 (S8.json §events_published). */
public record OpTransitionedPublishRequest(
    String type,
    UUID id,
    UUID incidentId,
    UUID opId,
    String status,
    long version,
    int sequenceNumber,
    UUID fromOpId,
    UUID toOpId) {

  public static OpTransitionedPublishRequest op1Bootstrap(
      UUID opId, UUID incidentId, String status, long version, int sequenceNumber) {
    return new OpTransitionedPublishRequest(
        "OP_TRANSITIONED", opId, incidentId, opId, status, version, sequenceNumber, null, opId);
  }
}
