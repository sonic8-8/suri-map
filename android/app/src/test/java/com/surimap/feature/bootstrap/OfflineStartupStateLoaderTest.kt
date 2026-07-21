package com.surimap.feature.bootstrap

import androidx.room.Room
import com.surimap.core.database.IncidentSummaryEntity
import com.surimap.core.database.SearchRecordingStateEntity
import com.surimap.core.database.SuriMapDatabase
import com.surimap.core.path.PathPoint
import com.surimap.core.sync.RoomSyncClient
import com.surimap.feature.bootstrap.data.OfflineStartupState
import com.surimap.feature.bootstrap.data.OfflineStartupStateLoader
import com.surimap.feature.search.data.SearchPathLocalRecorder
import com.surimap.feature.search.data.SearchPathWriteContext
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.pathIdFixture
import kotlinx.coroutines.runBlocking
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OfflineStartupStateLoaderTest {
    private lateinit var database: SuriMapDatabase
    private lateinit var loader: OfflineStartupStateLoader

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                SuriMapDatabase::class.java
            ).build()
        loader =
            OfflineStartupStateLoader(
                incidentSummaryDao = database.incidentSummaryDao(),
                searchRecordingStateDao = database.searchRecordingStateDao()
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun activeSearchOpensSameSearchMapAndResumesGps() = runBlocking {
        saveIncident()
        saveSearchState(status = "ACTIVE", activeStartedAt = 1_000L, accumulatedElapsed = 2_000L)

        val state = loader.load(ACCOUNT_ID) as OfflineStartupState.SearchMap

        assertEquals(INCIDENT_ID, state.incidentContext.incidentId)
        assertEquals(OP_ID, state.incidentContext.currentOpId)
        assertEquals(DUTY_SHIFT_ID, state.incidentContext.currentDutyShiftId)
        assertEquals(SearchLifecycleStatus.Active, state.recordingSession.lifecycleOverride)
        assertEquals(PATH_ID, state.recordingSession.activeLocalSearchPathId)
        assertEquals(1_000L, state.recordingSession.activeStartedAtMs)
        assertEquals(2_000L, state.recordingSession.accumulatedElapsedMs)
    }

    @Test
    fun pausedSearchOpensSameSearchMapWithoutResumingGps() = runBlocking {
        saveIncident()
        saveSearchState(status = "PAUSED", activeStartedAt = null, accumulatedElapsed = 12_000L)

        val state = loader.load(ACCOUNT_ID) as OfflineStartupState.SearchMap

        assertEquals(SearchLifecycleStatus.Paused, state.recordingSession.lifecycleOverride)
        assertEquals(PATH_ID, state.recordingSession.activeLocalSearchPathId)
        assertEquals(null, state.recordingSession.activeStartedAtMs)
        assertEquals(12_000L, state.recordingSession.accumulatedElapsedMs)
    }

    @Test
    fun cachedIncidentsWithoutActiveSearchOpenIncidentList() = runBlocking {
        saveIncident()

        assertEquals(OfflineStartupState.IncidentList, loader.load(ACCOUNT_ID))
    }

    @Test
    fun missingLocalDataKeepsConnectionCheck() = runBlocking {
        assertEquals(OfflineStartupState.RequiresConnection, loader.load(ACCOUNT_ID))
    }

    @Test
    fun clearingRecoveredSearchKeepsOutboxGpsBatchAndDrafts() = runBlocking {
        val ids =
            mapOf(
                "op-path-start" to "11111111-1111-4111-8111-111111111001",
                "path" to PATH_ID,
                "op-path-batch" to "11111111-1111-4111-8111-111111111002"
            )
        val recorder =
            SearchPathLocalRecorder(
                syncClient = RoomSyncClient(database),
                now = { CLIENT_TS },
                sequenceSource = sequenceSource(),
                idFactory = { prefix -> checkNotNull(ids[prefix]) }
            )
        val context =
            SearchPathWriteContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                accountId = ACCOUNT_ID,
                policePhoneId = POLICE_PHONE_ID
            )
        recorder.start(context)
        recorder.appendBatch(
            context = context,
            searchPathId = PATH_ID,
            points =
                listOf(
                    PathPoint("point-1", 126.9, 37.5, clientTs = CLIENT_TS),
                    PathPoint("point-2", 126.91, 37.51, clientTs = CLIENT_TS.plusSeconds(5))
                )
        )

        loader.clearRecoveredSearch(ACCOUNT_ID, INCIDENT_ID)

        assertEquals(null, database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID))
        assertEquals(
            listOf("/api/search-paths", "/api/search-paths/batch"),
            database.outboxDao().findByIncidentId(INCIDENT_ID).map { it.requestPath }
        )
        assertEquals(2, tableRowCount("local_write_draft"))
    }

    private suspend fun saveIncident() {
        database.incidentSummaryDao().upsertAll(
            listOf(
                IncidentSummaryEntity(
                    accountId = ACCOUNT_ID,
                    incidentId = INCIDENT_ID,
                    currentOpId = OP_ID,
                    currentOpLabel = "OP 1차",
                    currentDutyShiftId = DUTY_SHIFT_ID,
                    title = "실종자 수색",
                    summary = "현장 수색 진행 중",
                    packageStatus = "READY",
                    displayOrder = 0,
                    updatedAt = 1_000L
                )
            )
        )
    }

    private suspend fun saveSearchState(
        status: String,
        activeStartedAt: Long?,
        accumulatedElapsed: Long
    ) {
        database.searchRecordingStateDao().upsert(
            SearchRecordingStateEntity(
                accountId = ACCOUNT_ID,
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                searchPathId = PATH_ID,
                lifecycleStatus = status,
                activeStartedAt = activeStartedAt,
                accumulatedElapsed = accumulatedElapsed,
                updatedAt = 2_000L
            )
        )
    }

    private fun sequenceSource(): () -> Long {
        var next = 1L
        return { next++ }
    }

    private fun tableRowCount(tableName: String): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM $tableName")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

    private companion object {
        const val ACCOUNT_ID = "account-offline-001"
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-first-001")
        val PATH_ID = pathIdFixture("precinct-first-001")
        const val DUTY_SHIFT_ID = "99999999-9999-9999-9999-999999990001"
        const val POLICE_PHONE_ID = "50000000-0000-0000-0000-000000000001"
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
    }
}
