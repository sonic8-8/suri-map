package com.surimap.board.fixture;

import com.surimap.board.BoardAssemblyRequest;
import com.surimap.board.BoardRefetchSignal;
import com.surimap.board.BoardSourceRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * L6-T02C fixture: docs/spec/specs/S3-2.json api_assembly_failure_fixtures.delayed_refetch_trigger.
 */
public final class BoardAssemblyLagObservationFixtures {

  public static final String INCIDENT_ID = "inc-precinct-first-001";
  public static final String BOARD_RESPONSE_ID = "bs-inc-precinct-first-001";
  public static final OffsetDateTime SERVER_TS = OffsetDateTime.parse("2026-04-28T10:30:00+09:00");
  public static final String ACTIVE_OP_ID = "op-precinct-001-op2";
  public static final String EVENT_ID = "evt-s2-area-state-001";
  public static final String SLOT = "area";
  public static final String SOURCE_SPEC = "S2";
  public static final String ENTITY_ID = "area-precinct-a1";
  public static final long SOURCE_RESPONSE_VERSION = 3L;
  public static final long SOURCE_RESPONSE_SEQUENCE = 403L;
  public static final long STALE_RESPONSE_VERSION = 2L;
  public static final long STALE_RESPONSE_SEQUENCE = 402L;
  public static final String EXPECTED_UI_STATE = "STALE_REFETCH";
  public static final String EXPECTED_RELOAD_ASSERTION =
      "BoardDTO row version >= 3 and sequence >= 403";

  private BoardAssemblyLagObservationFixtures() {}

  public static BoardAssemblyRequest staleBoardRequest() {
    return new BoardAssemblyRequest(
        INCIDENT_ID,
        BOARD_RESPONSE_ID,
        STALE_RESPONSE_VERSION,
        SERVER_TS,
        ACTIVE_OP_ID,
        List.of("op-precinct-001-op1", ACTIVE_OP_ID),
        "hash-board-geometry-current",
        List.of(staleAreaRow()));
  }

  public static BoardRefetchSignal delayedAreaStateSignal() {
    return new BoardRefetchSignal(
        EVENT_ID,
        INCIDENT_ID,
        "SEARCH_AREA_CHANGED",
        SERVER_TS,
        SOURCE_RESPONSE_SEQUENCE,
        SLOT,
        SOURCE_SPEC,
        ENTITY_ID,
        "ASSIGNED",
        SOURCE_RESPONSE_VERSION,
        SOURCE_RESPONSE_SEQUENCE,
        "hash-s2-area-a1-v3",
        areaPayload());
  }

  public static BoardAssemblyRequest terminalBoardRequest() {
    return new BoardAssemblyRequest(
        INCIDENT_ID,
        BOARD_RESPONSE_ID,
        10,
        SERVER_TS,
        ACTIVE_OP_ID,
        List.of("op-precinct-001-op1", ACTIVE_OP_ID),
        "hash-board-geometry-current",
        List.of(terminalIncidentRow(), stalePolicePhoneFreshnessRow()));
  }

  public static BoardRefetchSignal policePhoneFreshnessSignal() {
    return new BoardRefetchSignal(
        "evt-s1-2-heartbeat-closed-001",
        INCIDENT_ID,
        "POLICE_PHONE_HEARTBEAT_UPDATED",
        SERVER_TS,
        901,
        "police_phone_freshness",
        "S1-2",
        "dev-precinct-car-01",
        "LOST",
        11,
        901,
        "hash-s1-2-phone-closed",
        Map.of(
            "policePhoneId",
            "dev-precinct-car-01",
            "freshnessStatus",
            "LOST",
            "lastHeartbeatAt",
            "2026-04-28T10:29:00+09:00"));
  }

  public static BoardSourceRow convergedAreaRow() {
    return areaRow(
        SOURCE_RESPONSE_VERSION, SOURCE_RESPONSE_SEQUENCE, EVENT_ID, "hash-s2-area-a1-v3");
  }

  private static BoardSourceRow staleAreaRow() {
    return areaRow(
        STALE_RESPONSE_VERSION,
        STALE_RESPONSE_SEQUENCE,
        "evt-s2-area-created-001",
        "hash-s2-area-a1-current");
  }

  private static BoardSourceRow terminalIncidentRow() {
    return new BoardSourceRow(
        "incident_terminal",
        "S1-1",
        INCIDENT_ID,
        "board-incident-terminal-inc-precinct-first-001",
        "CLOSED",
        10,
        900,
        "evt-s1-1-incident-closed-001",
        "hash-s1-1-terminal-closed",
        Map.of(
            "incidentId",
            INCIDENT_ID,
            "terminalStatus",
            "CLOSED",
            "closedStatus",
            "closed",
            "closedAt",
            "2026-04-28T10:30:00+09:00",
            "writeDisabledReason",
            "incident_closed",
            "localPurgeState",
            "queued"));
  }

  private static BoardSourceRow stalePolicePhoneFreshnessRow() {
    return new BoardSourceRow(
        "police_phone_freshness",
        "S1-2",
        "dev-precinct-car-01",
        "board-PolicePhone-freshness-dev-precinct-car-01",
        "STALE",
        10,
        900,
        "evt-s1-2-heartbeat-before-close-001",
        "hash-s1-2-phone-before-close",
        Map.of(
            "policePhoneId",
            "dev-precinct-car-01",
            "freshnessStatus",
            "STALE",
            "lastHeartbeatAt",
            "2026-04-28T10:28:00+09:00"));
  }

  private static BoardSourceRow areaRow(
      long version, long sequence, String latestEventId, String sourceHash) {
    return new BoardSourceRow(
        SLOT,
        SOURCE_SPEC,
        ENTITY_ID,
        "board-area-precinct-a1",
        "ASSIGNED",
        version,
        sequence,
        latestEventId,
        sourceHash,
        areaPayload());
  }

  private static Map<String, Object> areaPayload() {
    return Map.of(
        "opId",
        ACTIVE_OP_ID,
        "geometryHash",
        "hash-geometry-area-a1",
        "geometry",
        Map.of("type", "Polygon"));
  }
}
