package com.surimap.feature.handover

import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
import com.surimap.feature.handover.data.DutyShiftLocalRecorder
import com.surimap.feature.handover.data.DutyShiftWriteContext
import com.surimap.feature.handover.data.DutyShiftWriteResult
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DutyShiftLocalRecorderTest {

    @Test
    fun startAndEndDutyShiftEnqueueCanonicalOutboxOperations() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder =
            DutyShiftLocalRecorder(
                syncClient = syncClient,
                now = { CLIENT_TS },
                clockOffsetMs = { 75L },
                clockSyncedAt = { CLOCK_SYNCED_AT },
                sequenceSource = sequenceSource(40),
                idFactory = idFactory()
            )

        val start = recorder.start(CONTEXT) as DutyShiftWriteResult.Enqueued
        val end = recorder.end(CONTEXT, memo = "다음 근무자 인수인계") as DutyShiftWriteResult.Enqueued

        val startOperation = syncClient.operations[0]
        val endOperation = syncClient.operations[1]
        assertEquals(OP_START_ID, start.operationId)
        assertEquals(OP_END_ID, end.operationId)
        assertTrue(syncClient.operations.all { it.dependencyGroup == DependencyGroup.DUTY_SHIFT })
        assertEquals(listOf(40L, 41L), syncClient.operations.map { it.sequence })
        assertEquals(75L, startOperation.clockOffsetMs)
        assertEquals(CLOCK_SYNCED_AT, startOperation.clockSyncedAt)
        assertEquals("POST", startOperation.method)
        assertEquals("/api/duty-shifts", startOperation.endpoint)
        assertEquals(
            """{"incidentId":"$INCIDENT_ID","opId":"$OP_ID","policePhoneId":"$POLICE_PHONE_ID","clientTs":"2026-05-11T06:00:00Z"}""",
            startOperation.payload
        )
        assertEquals("PATCH", endOperation.method)
        assertEquals("/api/duty-shifts/$DUTY_SHIFT_ID", endOperation.endpoint)
        assertEquals(DUTY_SHIFT_ID, endOperation.entityId)
        assertEquals(
            """{"incidentId":"$INCIDENT_ID","opId":"$OP_ID","action":"END","clientTs":"2026-05-11T06:00:00Z","memo":"다음 근무자 인수인계"}""",
            endOperation.payload
        )
    }

    @Test
    fun missingContextBlocksDutyShiftStartAndEnd() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder = DutyShiftLocalRecorder(syncClient = syncClient)

        val missingOp = recorder.start(CONTEXT.copy(opId = null))
        val missingDutyShift = recorder.end(CONTEXT.copy(dutyShiftId = null))

        assertEquals(DutyShiftWriteResult.Blocked, missingOp)
        assertEquals(DutyShiftWriteResult.Blocked, missingDutyShift)
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

    private fun sequenceSource(first: Long): () -> Long {
        var next = first
        return { next++ }
    }

    private fun idFactory(): (String) -> String {
        val ids = ArrayDeque(listOf(OP_START_ID, OP_END_ID))
        return { ids.removeFirst() }
    }

    private companion object {
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
        const val OP_ID = "88888888-8888-8888-8888-888888880001"
        const val DUTY_SHIFT_ID = "77777777-7777-7777-7777-777777770001"
        const val POLICE_PHONE_ID = "50000000-0000-0000-0000-000000000001"
        const val OP_START_ID = "33333333-3333-4333-8333-333333333010"
        const val OP_END_ID = "33333333-3333-4333-8333-333333333011"
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
        val CLOCK_SYNCED_AT: Instant = Instant.parse("2026-05-11T06:00:00.075Z")
        val CONTEXT =
            DutyShiftWriteContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                dutyShiftId = DUTY_SHIFT_ID,
                policePhoneId = POLICE_PHONE_ID
            )
    }
}
