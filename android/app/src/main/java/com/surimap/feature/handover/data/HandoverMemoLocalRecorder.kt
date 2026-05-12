package com.surimap.feature.handover.data

import com.surimap.core.operationalperiod.CreateHandoverMemoCommand
import com.surimap.core.operationalperiod.HandoverMemoRepository
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.SyncClient
import java.time.Instant
import java.util.UUID

data class HandoverWriteContext(
    val incidentId: String?,
    val opId: String?,
    val dutyShiftId: String?,
    val policePhoneId: String?
)

data class HandoverMemoInput(
    val memoTargetType: String?,
    val memoTargetId: String?,
    val content: String?
)

sealed interface HandoverWriteResult {
    data object Blocked : HandoverWriteResult

    data class Enqueued(
        val operationId: String,
        val outboxId: String
    ) : HandoverWriteResult
}

class HandoverMemoLocalRecorder(
    syncClient: SyncClient,
    private val now: () -> Instant = { Instant.now() },
    private val clockOffsetMs: () -> Long? = { 0L },
    private val clockSyncedAt: () -> Instant? = { now() },
    private val sequenceSource: () -> Long = { System.currentTimeMillis() },
    private val idFactory: (String) -> String = { prefix -> "$prefix-${UUID.randomUUID()}" }
) {
    private val repository = HandoverMemoRepository(syncClient = syncClient)

    suspend fun createMemo(
        context: HandoverWriteContext,
        input: HandoverMemoInput
    ): HandoverWriteResult {
        val valid = context.valid() ?: return HandoverWriteResult.Blocked
        val targetType = input.memoTargetType?.takeIf(String::isNotBlank) ?: return HandoverWriteResult.Blocked
        if (targetType !in SUPPORTED_TARGET_TYPES) {
            return HandoverWriteResult.Blocked
        }
        val content = input.content?.trim()?.takeIf(String::isNotBlank) ?: return HandoverWriteResult.Blocked
        val operationId = idFactory("op-handover-memo")
        val clientTs = now()
        val result =
            repository.createHandoverMemo(
                CreateHandoverMemoCommand(
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    memoTargetType = targetType,
                    memoTargetId = input.memoTargetId?.takeIf(String::isNotBlank),
                    content = content,
                    clientTs = clientTs,
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )
        return result.enqueued()
    }

    private fun HandoverWriteContext.valid(): RequiredHandoverWriteContext? {
        val incidentId = incidentId?.takeIf(String::isNotBlank) ?: return null
        val opId = opId?.takeIf(String::isNotBlank) ?: return null
        val policePhoneId = policePhoneId?.takeIf(String::isNotBlank) ?: return null
        return RequiredHandoverWriteContext(
            incidentId = incidentId,
            opId = opId,
            policePhoneId = policePhoneId
        )
    }

    private fun EnqueueResult.enqueued(): HandoverWriteResult.Enqueued =
        HandoverWriteResult.Enqueued(
            operationId = operationId,
            outboxId = outboxId
        )

    private data class RequiredHandoverWriteContext(
        val incidentId: String,
        val opId: String,
        val policePhoneId: String
    )

    private companion object {
        val SUPPORTED_TARGET_TYPES =
            setOf(
                "OPERATIONAL_PERIOD",
                "DUTY_SHIFT",
                "SEARCH_PATH",
                "SEARCH_AREA",
                "MARKER"
            )
    }
}
