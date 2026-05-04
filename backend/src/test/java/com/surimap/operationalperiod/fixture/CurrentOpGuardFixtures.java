package com.surimap.operationalperiod.fixture;

import java.util.UUID;

/**
 * L3-T05A @RequireCurrentOp guard 실패 fixture.
 */
public final class CurrentOpGuardFixtures {

    /** 현재 활성 OP가 없을 때 write를 거부하는 에러 코드. */
    public static final String OP_REQUIRED_ERROR = "op_required";

    /** write 요청 opId가 현재 활성 OP와 다를 때 거부하는 에러 코드. */
    public static final String OP_MISMATCH_ERROR = "op_mismatch";

    private CurrentOpGuardFixtures() {}

    /** op_required guard 실패 응답 기대값. */
    public static GuardFailure opRequiredGuard() {
        return new GuardFailure(OP_REQUIRED_ERROR, 409);
    }

    /** op_mismatch guard 실패 응답 기대값. */
    public static GuardFailure opMismatchGuard() {
        return new GuardFailure(OP_MISMATCH_ERROR, 409);
    }

    /** current OP guard 통과 결과. */
    public static CurrentOpGuardDecision currentOpAllowed(UUID currentOpId) {
        return new CurrentOpGuardDecision(true, currentOpId, null, 200);
    }

    /** current OP guard 실패 결과. */
    public static CurrentOpGuardDecision currentOpRejected(GuardFailure guard) {
        return new CurrentOpGuardDecision(false, null, guard.error(), guard.status());
    }

    /** op_required / op_mismatch guard 실패 에러 응답 비교 모델. */
    public record GuardFailure(String error, int status) {}

    /** @RequireCurrentOp mock 실행 결과 비교 모델. */
    public record CurrentOpGuardDecision(
            boolean allowed,
            UUID currentOpId,
            String error,
            int status
    ) {}
}
