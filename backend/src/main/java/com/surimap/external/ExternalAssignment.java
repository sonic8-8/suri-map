package com.surimap.external;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.OffsetDateTime;

/**
 * mock 112 배정 DTO. incident_assignment 테이블 매핑용.
 *
 * <p>externalAssignmentKey: mock 112 측 배정 유일 키 (중복 polling 방지).
 */
public record ExternalAssignment(
    String externalAssignmentKey,
    @JsonAlias("accountId") String accountCode,
    String incidentRole,
    OffsetDateTime assignedAt) {}
