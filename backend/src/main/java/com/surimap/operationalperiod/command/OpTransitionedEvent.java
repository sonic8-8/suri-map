package com.surimap.operationalperiod.command;

import java.util.UUID;

/**
 * OP_TRANSITIONED publish request payload (S8.json §api_contracts.events_published OP_TRANSITIONED).
 *
 * <p>S4 EventHub.publish 호출 전 S8이 생성하는 payload 구조. fromOpId는 OP1 최초 전환 시 null.
 */
public record OpTransitionedEvent(
    String type,
    UUID incidentId,
    UUID opId,
    String status,
    long version,
    int sequenceNumber,
    UUID fromOpId,
    UUID toOpId) {

  public static final String TYPE = "OP_TRANSITIONED";
}
