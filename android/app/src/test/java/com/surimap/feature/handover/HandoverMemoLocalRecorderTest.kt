package com.surimap.feature.handover

import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
import com.surimap.feature.handover.data.HandoverMemoInput
import com.surimap.feature.handover.data.HandoverMemoLocalRecorder
import com.surimap.feature.handover.data.HandoverWriteContext
import com.surimap.feature.handover.data.HandoverWriteResult
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoverMemoLocalRecorderTest {

    @Test
    fun createHandoverMemoEnqueuesCanonicalOutboxOperation() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder =
            HandoverMemoLocalRecorder(
                syncClient = syncClient,
                now = { CLIENT_TS },
                sequenceSource = { 70L },
                idFactory = { OP_MEMO_CREATE_ID }
            )

        val result =
            recorder.createMemo(
                context = CONTEXT,
                input =
                HandoverMemoInput(
                    memoTargetType = "OPERATIONAL_PERIOD",
                    memoTargetId = OP_ID,
                    content = "OP 인수인계"
                )
            ) as HandoverWriteResult.Enqueued

        val operation = syncClient.operations.single()
        assertEquals(OP_MEMO_CREATE_ID, result.operationId)
        assertEquals(DependencyGroup.HANDOVER_MEMO, operation.dependencyGroup)
        assertEquals("POST", operation.method)
        assertEquals("/api/handover-memos", operation.endpoint)
        assertEquals("handover_memo", operation.entityType)
        assertEquals("idem-$OP_MEMO_CREATE_ID", operation.idempotencyKey)
        assertEquals(70L, operation.sequence)
        assertEquals(0L, operation.clockOffsetMs)
        assertEquals(CLIENT_TS, operation.clockSyncedAt)
        assertEquals(
            """{"incidentId":"$INCIDENT_ID","opId":"$OP_ID","memoTargetType":"OPERATIONAL_PERIOD","memoTargetId":"$OP_ID","content":"OP 인수인계","clientTs":"2026-05-11T06:00:00Z"}""",
            operation.payload
        )
    }

    @Test
    fun missingContextOrBlankContentDoesNotCreateOutboxOperation() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder = HandoverMemoLocalRecorder(syncClient = syncClient)

        val missingContext =
            recorder.createMemo(
                context = CONTEXT.copy(policePhoneId = null),
                input = HandoverMemoInput(memoTargetType = "OPERATIONAL_PERIOD", memoTargetId = OP_ID, content = "memo")
            )
        val blankContent =
            recorder.createMemo(
                context = CONTEXT,
                input = HandoverMemoInput(memoTargetType = "OPERATIONAL_PERIOD", memoTargetId = OP_ID, content = " ")
            )

        assertEquals(HandoverWriteResult.Blocked, missingContext)
        assertEquals(HandoverWriteResult.Blocked, blankContent)
        assertTrue(syncClient.operations.isEmpty())
    }

    @Test
    fun unsupportedTargetTypeIsBlockedLocally() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder = HandoverMemoLocalRecorder(syncClient = syncClient)

        val result =
            recorder.createMemo(
                context = CONTEXT,
                input = HandoverMemoInput(memoTargetType = "UNKNOWN", memoTargetId = null, content = "memo")
            )

        assertEquals(HandoverWriteResult.Blocked, result)
        assertTrue(syncClient.operations.isEmpty())
    }

    private class CapturingSyncClient : SyncClient {
        val operations = mutableListOf<LocalWriteOperation>()

        override suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult {
            operations += writeOperation
            return EnqueueResult(
                outboxId = "outbox-${operations.size}",
                operationId = writeOperation.operationId,
                status = OutboxStatus.PENDING,
                harnessStatus = HarnessSyncStatus.PENDING_SEND
            )
        }
    }

    private companion object {
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
        const val OP_ID = "88888888-8888-8888-8888-888888880001"
        const val DUTY_SHIFT_ID = "77777777-7777-7777-7777-777777770001"
        const val POLICE_PHONE_ID = "50000000-0000-0000-0000-000000000001"
        const val OP_MEMO_CREATE_ID = "33333333-3333-4333-8333-333333333001"
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
        val CONTEXT =
            HandoverWriteContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                dutyShiftId = DUTY_SHIFT_ID,
                policePhoneId = POLICE_PHONE_ID
            )
    }
}
