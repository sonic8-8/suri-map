package com.surimap.maparea.fixture;

import java.util.List;
import java.util.UUID;

/**
 * S2 map_boundary/search_area fixture IDs, 상태, 이벤트 모음.
 *
 * <p>기준 문서: docs/spec/specs/S2.json harness constraints.</p>
 */
public final class BoundaryAreaFixtures {

    // TODO: S2 production enum이 생기면 상태, 이력 이벤트 타입, 발행 이벤트 타입 문자열을 enum 또는 wireValue() 기준으로 교체한다.

    /** 문서에 적힌 사람이 읽기 쉬운 SC-04 incident alias. 실제 DB ID는 UUID를 사용한다. */
    public static final String INCIDENT_ALIAS = "inc-precinct-first-001";

    /** SC-04 시나리오에서 사용하는 고정 incident UUID. */
    public static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");

    /* --- SC-04 지도 경계/수색 구역/이력 고정 ID (S2.json line 1958) --- */
    public static final String BOUNDARY_ALIAS = "mb-precinct-001-v1";
    public static final UUID BOUNDARY_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001");
    public static final UUID SUPERSEDED_BOUNDARY_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0000");
    public static final String AREA_ALIAS = "area-precinct-a1";
    public static final UUID AREA_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccc0001");
    public static final String OP1_ALIAS = "op-precinct-001-op1";
    public static final UUID OP1_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
    public static final String OP2_ALIAS = "op-precinct-001-op2";
    public static final UUID OP2_ID = UUID.fromString("88888888-8888-8888-8888-888888880002");
    public static final List<UUID> SPLIT_CHILD_AREA_IDS = List.of(
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccc0011"),
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccc0012")
    );
    public static final UUID HISTORY_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddd0001");

    /* --- SC-04 이벤트/스냅샷 고정 ID와 version (S2.json line 1959) --- */
    public static final String BOUNDARY_EVENT_ID = "evt-s2-boundary-001";
    public static final long BOUNDARY_EVENT_SEQUENCE = 401L;
    public static final long BOUNDARY_VERSION = 2L;

    public static final String AREA_CREATED_EVENT_ID = "evt-s2-area-created-001";
    public static final long AREA_CREATED_EVENT_SEQUENCE = 402L;
    public static final long AREA_CREATED_VERSION = 1L;

    public static final String AREA_STATE_EVENT_ID = "evt-s2-area-state-001";
    public static final long AREA_STATE_EVENT_SEQUENCE = 403L;
    public static final long AREA_STATE_VERSION = 3L;

    public static final String BOARD_MAP_BOUNDARY_ROW_ID = "board-map-boundary-inc-precinct-first-001";
    public static final String BOARD_AREA_ROW_ID = "board-area-precinct-a1";

    /** 지도 경계(map_boundary)의 저장 상태. */
    public static final List<String> MAP_BOUNDARY_STATUSES = List.of("ACTIVE", "SUPERSEDED");

    /** 수색 구역(search_area.state)에 실제 저장되는 상태 enum. */
    public static final List<String> SEARCH_AREA_PERSISTED_STATES = List.of(
            "ASSIGNED",
            "SEARCHING",
            "FIRST_SEARCH_COMPLETED",
            "RECHECK_REQUIRED",
            "RECHECKING",
            "RECHECK_COMPLETED",
            "ARCHIVED"
    );

    /** 수색 구역 이력(search_area_history.event_type)에 남기는 이벤트 종류. */
    public static final List<String> SEARCH_AREA_HISTORY_EVENT_TYPES = List.of(
            "CREATED",
            "GEOMETRY_UPDATED",
            "SPLIT_FROM_PARENT",
            "STATE_CHANGED"
    );

    /* --- DTO alias policy (S2.json line 2140-2160) --- */

    /** UI/harness에서만 쓰는 완료 상태 alias. DB에는 저장하지 않는다. */
    public static final String COMPLETED_ALIAS = "COMPLETED";
    public static final List<String> COMPLETED_ALIAS_MAPS_TO = List.of(
            "FIRST_SEARCH_COMPLETED",
            "RECHECK_COMPLETED"
    );

    /* --- state_api_status_convergence (S2.json line 2127-2138) --- */

    /** DB 저장 상태 필드와 API 공개 alias 필드가 분리되어 있는지 확인하는 기준. */
    public static final String PERSISTED_STATE_FIELD = "search_area.state";
    public static final String PUBLIC_ALIAS_FIELD = "status";

    /* --- split_lifecycle (S2.json line 2115-2126) --- */

    /** split 후 원본 구역(parent)은 보관 상태가 되고, 새 구역(child)은 ASSIGNED로 시작한다. */
    public static final String SPLIT_PARENT_NEXT_STATE = "ARCHIVED";
    public static final String SPLIT_CHILD_INITIAL_STATE = "ASSIGNED";
    public static final long SPLIT_CHILD_INITIAL_SEARCH_COUNT = 0L;
    public static final long SPLIT_CHILD_INITIAL_VERSION = 1L;

    private BoundaryAreaFixtures() {}

    /* --- Factory methods (record types for grouped data) --- */

    /** 지도 경계가 ACTIVE 버전으로 교체됐음을 알리는 SC-04 이벤트 envelope. */
    public static ExpectedEventEnvelope mapBoundaryChangedEvent() {
        return new ExpectedEventEnvelope(
                BOUNDARY_EVENT_ID,
                "MAP_BOUNDARY_CHANGED",
                BOUNDARY_EVENT_SEQUENCE,
                BOUNDARY_ID,
                "ACTIVE",
                BOUNDARY_VERSION,
                null
        );
    }

    /** 수색 구역이 ASSIGNED 상태로 생성됐음을 알리는 SC-04 이벤트 envelope. */
    public static ExpectedEventEnvelope areaCreatedEvent() {
        return new ExpectedEventEnvelope(
                AREA_CREATED_EVENT_ID,
                "AREA_CREATED",
                AREA_CREATED_EVENT_SEQUENCE,
                AREA_ID,
                "ASSIGNED",
                AREA_CREATED_VERSION,
                OP1_ID
        );
    }

    /** split 후 원본 수색 구역이 ARCHIVED로 닫혔음을 알리는 SC-04 이벤트 envelope. */
    public static ExpectedEventEnvelope areaArchivedAfterSplitEvent() {
        return new ExpectedEventEnvelope(
                AREA_STATE_EVENT_ID,
                "AREA_STATE_CHANGED",
                AREA_STATE_EVENT_SEQUENCE,
                AREA_ID,
                "ARCHIVED",
                AREA_STATE_VERSION,
                OP1_ID
        );
    }

    /**
     * S2 이벤트 발행 결과를 테스트에서 비교하기 위한 읽기 모델.
     *
     * <p>S4 EventHub.publish PublishRequest의 id/status/version 수렴 비교 기준.</p>
     */
    public record ExpectedEventEnvelope(
            String eventId,
            String type,
            long sequence,
            UUID payloadId,
            String payloadStatus,
            long payloadVersion,
            UUID opId
    ) {}
}
