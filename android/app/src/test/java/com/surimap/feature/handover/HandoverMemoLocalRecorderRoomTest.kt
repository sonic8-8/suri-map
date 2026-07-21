package com.surimap.feature.handover

import androidx.room.Room
import com.surimap.core.database.SuriMapDatabase
import com.surimap.core.sync.RoomSyncClient
import com.surimap.feature.handover.data.HandoverMemoInput
import com.surimap.feature.handover.data.HandoverMemoLocalRecorder
import com.surimap.feature.handover.data.HandoverWriteContext
import com.surimap.feature.handover.data.HandoverWriteResult
import com.surimap.testing.dutyShiftIdFixture
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.operationIdFactory
import com.surimap.testing.operationIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.policePhoneIdFixture
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HandoverMemoLocalRecorderRoomTest {
    private lateinit var database: SuriMapDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            SuriMapDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createHandoverMemoPersistsPendingOutboxRowBeforeNetworkReplay() = runBlocking {
        val recorder =
            HandoverMemoLocalRecorder(
                syncClient = RoomSyncClient(database),
                now = { CLIENT_TS },
                sequenceSource = { 80L },
                idFactory = operationIdFactory("handover-memo-001")
            )

        val result =
            recorder.createMemo(
                context = CONTEXT,
                input =
                HandoverMemoInput(
                    memoTargetType = "DUTY_SHIFT",
                    memoTargetId = DUTY_SHIFT_ID,
                    content = "다음 근무자 인수인계"
                )
            ) as HandoverWriteResult.Enqueued

        val row = database.outboxDao().findByIncidentId(INCIDENT_ID).single()
        val draft = database.localWriteDraftDao().findById(result.outboxId)

        assertEquals(operationIdFixture("handover-memo-001"), row.operationId)
        assertEquals(OP_ID, row.opId)
        assertEquals(POLICE_PHONE_ID, row.policePhoneId)
        assertEquals("HANDOVER_MEMO", row.dependencyGroup)
        assertEquals("POST", row.requestMethod)
        assertEquals("/api/handover-memos", row.requestPath)
        assertEquals("PENDING", row.idempotencyStatus)
        assertEquals("PENDING_SEND", row.localMirrorStatus)
        assertEquals(80L, row.sequence)
        assertEquals("handover_memo", draft!!.entityType)
        assertEquals(result.operationId, draft.operationId)
        assertTrue(draft.payload.contains("\"memoTargetType\":\"DUTY_SHIFT\""))
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-first-001")
        val DUTY_SHIFT_ID = dutyShiftIdFixture("precinct-first-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
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
