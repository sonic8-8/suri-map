package com.surimap.feature.search

import androidx.room.Room
import com.surimap.core.database.SuriMapDatabase
import com.surimap.core.sync.RoomSyncClient
import com.surimap.feature.search.data.SearchPathLocalRecorder
import com.surimap.feature.search.data.SearchPathWriteContext
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.operationIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.pathIdFixture
import com.surimap.testing.policePhoneIdFixture
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SearchPathLocalRecorderRoomTest {
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
    fun startSearchPathPersistsOutboxAndActiveRecoveryState() = runBlocking {
        val recorder =
            SearchPathLocalRecorder(
                syncClient = RoomSyncClient(database),
                now = { CLIENT_TS },
                sequenceSource = { 42L },
                idFactory = { prefix ->
                    when (prefix) {
                        "op-path-start" -> operationIdFixture("path-start-001")
                        "path" -> PATH_ID
                        else -> error("Unexpected id prefix: $prefix")
                    }
                }
            )

        recorder.start(
            SearchPathWriteContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                accountId = ACCOUNT_ID,
                policePhoneId = POLICE_PHONE_ID
            )
        )

        val row = database.outboxDao().findByIncidentId(INCIDENT_ID).single()
        val recoveryState = database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID)

        assertEquals(operationIdFixture("path-start-001"), row.operationId)
        assertEquals("/api/search-paths", row.requestPath)
        assertEquals("PATH", row.dependencyGroup)
        assertEquals("PENDING", row.idempotencyStatus)
        assertEquals("PENDING_SEND", row.localMirrorStatus)
        assertEquals(POLICE_PHONE_ID, row.policePhoneId)
        assertEquals(OP_ID, row.opId)
        assertEquals(
            """{"searchPathId":"$PATH_ID","incidentId":"$INCIDENT_ID","opId":"$OP_ID","clientTs":"2026-05-11T06:00:00Z","clockOffsetMs":0}""",
            row.payloadJson
        )
        checkNotNull(recoveryState)
        assertEquals(OP_ID, recoveryState.opId)
        assertEquals(PATH_ID, recoveryState.searchPathId)
        assertEquals("ACTIVE", recoveryState.lifecycleStatus)
        assertEquals(CLIENT_TS.toEpochMilli(), recoveryState.activeStartedAt)
        assertEquals(0L, recoveryState.accumulatedElapsed)
    }

    @Test
    fun pauseResumeAndEndUpdateRecoveryState() = runBlocking {
        var now = CLIENT_TS
        var sequence = 1L
        val operationIds = mapOf(
            "op-path-start" to operationIdFixture("path-start-002"),
            "op-path-pause" to operationIdFixture("path-pause-002"),
            "op-path-resume" to operationIdFixture("path-resume-002"),
            "op-path-end" to operationIdFixture("path-end-002"),
            "path" to PATH_ID
        )
        val recorder =
            SearchPathLocalRecorder(
                syncClient = RoomSyncClient(database),
                now = { now },
                sequenceSource = { sequence++ },
                idFactory = { prefix -> checkNotNull(operationIds[prefix]) }
            )
        val context =
            SearchPathWriteContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                accountId = ACCOUNT_ID,
                policePhoneId = POLICE_PHONE_ID
            )

        recorder.start(context)
        now = CLIENT_TS.plusSeconds(10)
        recorder.pause(context, PATH_ID)

        val paused = checkNotNull(database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID))
        assertEquals("PAUSED", paused.lifecycleStatus)
        assertEquals(null, paused.activeStartedAt)
        assertEquals(10_000L, paused.accumulatedElapsed)

        now = CLIENT_TS.plusSeconds(20)
        recorder.resume(context, PATH_ID)

        val active = checkNotNull(database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID))
        assertEquals("ACTIVE", active.lifecycleStatus)
        assertEquals(now.toEpochMilli(), active.activeStartedAt)
        assertEquals(10_000L, active.accumulatedElapsed)

        now = CLIENT_TS.plusSeconds(50)
        recorder.end(context, PATH_ID)

        val stopped = checkNotNull(database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID))
        assertEquals("STOPPED", stopped.lifecycleStatus)
        assertEquals(null, stopped.activeStartedAt)
        assertEquals(40_000L, stopped.accumulatedElapsed)
        assertEquals(4, database.outboxDao().findByIncidentId(INCIDENT_ID).size)
    }

    @Test
    fun stoppedSearchIgnoresLatePauseAndResumeRequests() = runBlocking {
        var now = CLIENT_TS
        var sequence = 1L
        val recorder =
            SearchPathLocalRecorder(
                syncClient = RoomSyncClient(database),
                now = { now },
                sequenceSource = { sequence++ },
                idFactory = { prefix ->
                    when (prefix) {
                        "path" -> PATH_ID
                        else -> operationIdFixture("late-$prefix-$sequence")
                    }
                }
            )
        val context = searchPathWriteContext()

        recorder.start(context)
        now = now.plusSeconds(10)
        recorder.end(context, PATH_ID)
        now = now.plusSeconds(10)
        recorder.pause(context, PATH_ID)
        now = now.plusSeconds(10)
        recorder.resume(context, PATH_ID)

        val state = checkNotNull(database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID))
        assertEquals("STOPPED", state.lifecycleStatus)
        assertEquals(PATH_ID, state.searchPathId)
    }

    @Test
    fun lifecycleRequestForDifferentPathDoesNotReplaceCurrentSearch() = runBlocking {
        var sequence = 1L
        val recorder =
            SearchPathLocalRecorder(
                syncClient = RoomSyncClient(database),
                now = { CLIENT_TS },
                sequenceSource = { sequence++ },
                idFactory = { prefix ->
                    when (prefix) {
                        "path" -> PATH_ID
                        else -> operationIdFixture("different-$prefix-$sequence")
                    }
                }
            )

        recorder.start(searchPathWriteContext())
        recorder.pause(searchPathWriteContext(), OTHER_PATH_ID)

        val state = checkNotNull(database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID))
        assertEquals("ACTIVE", state.lifecycleStatus)
        assertEquals(PATH_ID, state.searchPathId)
    }

    @Test
    fun failedRecoveryStateInsertRollsBackOutboxAndDraft() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_search_recording_state_insert
            BEFORE INSERT ON search_recording_state
            BEGIN
                SELECT RAISE(ABORT, 'forced failure');
            END
            """.trimIndent()
        )
        val recorder =
            SearchPathLocalRecorder(
                syncClient = RoomSyncClient(database),
                now = { CLIENT_TS },
                sequenceSource = { 1L },
                idFactory = { prefix ->
                    when (prefix) {
                        "op-path-start" -> operationIdFixture("path-start-rollback-001")
                        "path" -> PATH_ID
                        else -> error("Unexpected id prefix: $prefix")
                    }
                }
            )

        val result =
            runCatching {
                recorder.start(
                    SearchPathWriteContext(
                        incidentId = INCIDENT_ID,
                        opId = OP_ID,
                        accountId = ACCOUNT_ID,
                        policePhoneId = POLICE_PHONE_ID
                    )
                )
            }

        assertEquals(true, result.isFailure)
        assertEquals(0, database.outboxDao().findByIncidentId(INCIDENT_ID).size)
        assertEquals(0, tableRowCount("local_write_draft"))
    }

    private fun tableRowCount(tableName: String): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM $tableName")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

    private fun searchPathWriteContext(): SearchPathWriteContext =
        SearchPathWriteContext(
            incidentId = INCIDENT_ID,
            opId = OP_ID,
            accountId = ACCOUNT_ID,
            policePhoneId = POLICE_PHONE_ID
        )

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-first-001")
        val PATH_ID = pathIdFixture("precinct-first-001")
        val OTHER_PATH_ID = pathIdFixture("precinct-first-002")
        const val ACCOUNT_ID = "account-path-001"
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
    }
}
