package com.surimap.operationalperiod.fixture;

import java.util.List;

/**
 * S8 op_assignment 전용 fixture.
 *
 * <p>OP 자체, bootstrap, transition, guard, idempotency fixture는 각 전용 fixture 클래스를 사용한다.</p>
 */
public final class OpAssignmentFixtures {

    // TODO: S8 production enum이 생기면 assignment 상태/타입 문자열을 enum 또는 wireValue() 기준으로 교체한다.

    /** op_assignment.status에 실제 저장될 수 있는 값. */
    public static final List<String> OP_ASSIGNMENT_STATUSES = List.of(
            "ACTIVE",
            "REPLACED",
            "CANCELLED"
    );

    /** op_assignment.assignment_type에 실제 저장될 수 있는 값. */
    public static final List<String> OP_ASSIGNMENT_TYPES = List.of(
            "TEAM_PHONE",
            "PATROL_CAR_PHONE"
    );

    /** AssignmentQuery.byOp 결과 행이 유지해야 하는 필드 목록. */
    public static final List<String> ASSIGNMENT_QUERY_ROW_SHAPE = List.of(
            "assignmentId",
            "incidentId",
            "opId",
            "areaId",
            "teamId",
            "accountId",
            "deviceId",
            "status",
            "version"
    );

    private OpAssignmentFixtures() {}
}
