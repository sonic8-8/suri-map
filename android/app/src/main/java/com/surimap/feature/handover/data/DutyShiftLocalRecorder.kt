package com.surimap.feature.handover.data

import com.surimap.core.operationalperiod.DutyShiftRepository
import com.surimap.core.operationalperiod.EndDutyShiftCommand
import com.surimap.core.operationalperiod.StartDutyShiftCommand
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.SyncClient
import java.time.Instant
import java.util.UUID

data class DutyShiftWriteContext(
    val incidentId: String?,
    val opId: String?,
    val dutyShiftId: String?,
    val policePhoneId: String?
)

sealed interface DutyShiftWriteResult {
    data object Blocked : DutyShiftWriteResult

    data class Enqueued(
        val operationId: String,
        val outboxId: String
    ) : DutyShiftWriteResult
}

class DutyShiftLocalRecorder(
    syncClient: SyncClient,
    private val now: () -> Instant = { Instant.now() },
    private val clockOffsetMs: () -> Long? = { 0L },
    private val clockSyncedAt: () -> Instant? = { now() },
    private val sequenceSource: () -> Long = { System.currentTimeMillis() },
    private val idFactory: (String) -> String = { _ -> UUID.randomUUID().toString() }
) {
    private val repository = DutyShiftRepository(syncClient = syncClient)

    suspend fun start(context: DutyShiftWriteContext): DutyShiftWriteResult {
        val valid = context.validForStart() ?: return DutyShiftWriteResult.Blocked
        val operationId = idFactory("op-duty-shift-start")
        val clientTs = now()
        val result =
            repository.startDutyShift(
                StartDutyShiftCommand(
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    clientTs = clientTs,
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )
        return result.enqueued()
    }

    suspend fun end(context: DutyShiftWriteContext, memo: String? = null): DutyShiftWriteResult {
        val valid = context.validForEnd() ?: return DutyShiftWriteResult.Blocked
        val operationId = idFactory("op-duty-shift-end")
        val clientTs = now()
        val result =
            repository.endDutyShift(
                EndDutyShiftCommand(
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    dutyShiftId = valid.dutyShiftId,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    clientTs = clientTs,
                    memo = memo?.trim()?.takeIf(String::isNotBlank),
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )
        return result.enqueued()
    }

    private fun DutyShiftWriteContext.validForStart(): RequiredDutyShiftStartContext? {
        val incidentId = incidentId?.takeIf(String::isNotBlank) ?: return null
        val opId = opId?.takeIf(String::isNotBlank) ?: return null
        val policePhoneId = policePhoneId?.takeIf(String::isNotBlank) ?: return null
        return RequiredDutyShiftStartContext(
            incidentId = incidentId,
            opId = opId,
            policePhoneId = policePhoneId
        )
    }

    private fun DutyShiftWriteContext.validForEnd(): RequiredDutyShiftEndContext? {
        val incidentId = incidentId?.takeIf(String::isNotBlank) ?: return null
        val opId = opId?.takeIf(String::isNotBlank) ?: return null
        val dutyShiftId = dutyShiftId?.takeIf(String::isNotBlank) ?: return null
        val policePhoneId = policePhoneId?.takeIf(String::isNotBlank) ?: return null
        return RequiredDutyShiftEndContext(
            incidentId = incidentId,
            opId = opId,
            dutyShiftId = dutyShiftId,
            policePhoneId = policePhoneId
        )
    }

    private fun EnqueueResult.enqueued(): DutyShiftWriteResult.Enqueued =
        DutyShiftWriteResult.Enqueued(
            operationId = operationId,
            outboxId = outboxId
        )

    private data class RequiredDutyShiftStartContext(
        val incidentId: String,
        val opId: String,
        val policePhoneId: String
    )

    private data class RequiredDutyShiftEndContext(
        val incidentId: String,
        val opId: String,
        val dutyShiftId: String,
        val policePhoneId: String
    )
}
