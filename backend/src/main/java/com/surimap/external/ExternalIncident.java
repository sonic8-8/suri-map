package com.surimap.external;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * mock 112 사건 전체 DTO. POST /api/incidents/import 호출 시 이 데이터로 incident, missing_person,
 * incident_assignment를 저장하고 seed marker 입력을 S5 port로 넘긴다.
 */
public record ExternalIncident(
    String sourceIncidentId,
    String title,
    OffsetDateTime openedAt,
    String status,
    ExternalMissingPerson missingPerson,
    List<ExternalAssignment> assignments,
    List<ExternalSeedMarker> seedMarkers) {}
