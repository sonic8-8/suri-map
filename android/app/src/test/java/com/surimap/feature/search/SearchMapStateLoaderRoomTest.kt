package com.surimap.feature.search

import androidx.room.Room
import com.surimap.core.database.SearchRecordingStateEntity
import com.surimap.core.database.SuriMapDatabase
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.RoomSyncClient
import com.surimap.feature.search.data.RoomSearchMapResponseCache
import com.surimap.feature.search.data.RoomSearchRecordingStateStore
import com.surimap.feature.search.data.SearchMapSessionContext
import com.surimap.feature.search.data.SearchMapStateLoader
import com.surimap.testing.incidentIdFixture
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
class SearchMapStateLoaderRoomTest {
    private lateinit var database: SuriMapDatabase

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                SuriMapDatabase::class.java
            ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun serverRecordingPathIsStoredForOfflineRelaunch() = runBlocking {
        val loader = serverRecordingPathLoader()

        loader.load(sessionContext())

        val state = database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID)
        checkNotNull(state)
        assertEquals(OP_ID, state.opId)
        assertEquals(PATH_ID, state.searchPathId)
        assertEquals("ACTIVE", state.lifecycleStatus)
        assertEquals(STARTED_AT_MS, state.activeStartedAt)
        assertEquals(0L, state.accumulatedElapsed)
    }

    @Test
    fun serverPausedPathIsStoredWithoutActiveStartTime() = runBlocking {
        val loader = serverRecordingPathLoader(status = "PAUSED")

        loader.load(sessionContext())

        val state = database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID)
        checkNotNull(state)
        assertEquals("PAUSED", state.lifecycleStatus)
        assertEquals(null, state.activeStartedAt)
        assertEquals(0L, state.accumulatedElapsed)
    }

    @Test
    fun freshServerPauseReplacesPreviousServerRecordingState() = runBlocking {
        database.searchRecordingStateDao().upsert(
            SearchRecordingStateEntity(
                accountId = ACCOUNT_ID,
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                searchPathId = PATH_ID,
                lifecycleStatus = "ACTIVE",
                activeStartedAt = STARTED_AT_MS,
                accumulatedElapsed = 0L,
                updatedAt = 1L
            )
        )
        val loader = serverRecordingPathLoader(status = "PAUSED")

        loader.load(sessionContext())

        val state = database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID)
        checkNotNull(state)
        assertEquals("PAUSED", state.lifecycleStatus)
        assertEquals(null, state.activeStartedAt)
        assertEquals(0L, state.accumulatedElapsed)
    }

    @Test
    fun freshServerResponseWithoutActivePathRemovesPreviousServerState() = runBlocking {
        database.searchRecordingStateDao().upsert(activeRecordingState())
        val loader = serverRecordingPathLoader(body = """{"paths":[]}""")

        val state = loader.load(sessionContext())

        assertEquals(null, state.activeSearchPathId)
        assertEquals(null, database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID))
    }

    @Test
    fun malformedServerPathListKeepsPreviousServerState() = runBlocking {
        database.searchRecordingStateDao().upsert(activeRecordingState())
        val loader = serverRecordingPathLoader(body = """{"paths":[null]}""")

        loader.load(sessionContext())

        val state = database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID)
        checkNotNull(state)
        assertEquals(PATH_ID, state.searchPathId)
        assertEquals("ACTIVE", state.lifecycleStatus)
    }

    @Test
    fun cachedRecordingPathDoesNotCreateRecoveryStateBeforeServerValidation() = runBlocking {
        val cache = RoomSearchMapResponseCache(database.searchMapResponseCacheDao())
        cache.upsertIfChanged(
            context = sessionContext(),
            source = "search_paths",
            body = recordingPathBody(),
            sourceRevision = "paths-revision-1"
        )
        val loader = serverRecordingPathLoader(responseCache = cache)

        loader.cached(sessionContext())

        assertEquals(null, database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID))
    }

    @Test
    fun serverValidatedCacheCreatesRecoveryStateWithoutRefetchingPaths() = runBlocking {
        val cache = RoomSearchMapResponseCache(database.searchMapResponseCacheDao())
        cache.upsertIfChanged(
            context = sessionContext(),
            source = "search_paths",
            body = recordingPathBody(),
            sourceRevision = "paths-revision-1"
        )
        val loader =
            serverRecordingPathLoader(
                responseCache = cache,
                mapRevisions = {
                    SuriMapApiResponse(
                        statusCode = 200,
                        body =
                            """
                            {"sources":[{"source":"search_paths","revision":"paths-revision-1"}]}
                            """.trimIndent(),
                        errorCode = null
                    )
                },
                searchPaths = { error("validated cache must not refetch paths") }
            )

        loader.load(sessionContext())

        val state = database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID)
        checkNotNull(state)
        assertEquals("ACTIVE", state.lifecycleStatus)
    }

    @Test
    fun pendingLocalEndIsNotOverwrittenByServerRecordingState() = runBlocking {
        database.searchRecordingStateDao().upsert(activeRecordingState())
        RoomSyncClient(database).enqueue(
            LocalWriteOperation(
                operationId = "operation-path-end-001",
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID,
                dependencyGroup = DependencyGroup.PATH,
                sequence = 1L,
                method = "PATCH",
                endpoint = "/api/search-paths/$PATH_ID",
                payload = """{"action":"END"}""",
                bodyHash = "sha256:path-end-001",
                idempotencyKey = "idem-path-end-001",
                clientTs = Instant.ofEpochMilli(UPDATED_AT_MS),
                clockOffsetMs = 0L,
                clockSyncedAt = Instant.ofEpochMilli(UPDATED_AT_MS),
                opId = OP_ID,
                entityId = PATH_ID,
                entityType = "search_path",
                accountId = ACCOUNT_ID
            )
        )
        val loader = serverRecordingPathLoader()

        loader.load(sessionContext())

        val state = database.searchRecordingStateDao().find(ACCOUNT_ID, INCIDENT_ID)
        checkNotNull(state)
        assertEquals("STOPPED", state.lifecycleStatus)
    }

    private fun serverRecordingPathLoader(
        status: String = "RECORDING",
        body: String = recordingPathBody(status),
        responseCache: RoomSearchMapResponseCache? = null,
        mapRevisions: suspend (SearchMapSessionContext) -> SuriMapApiResponse = { notFoundResponse() },
        searchPaths: suspend (com.surimap.core.path.SearchPathQuery) -> SuriMapApiResponse = {
            SuriMapApiResponse(statusCode = 200, body = body, errorCode = null)
        }
    ): SearchMapStateLoader =
        SearchMapStateLoader(
            incidentDetail = { notFoundResponse() },
            overallSearchArea = { notFoundResponse() },
            opSearchAreas = { _, _ -> notFoundResponse() },
            searchPaths = searchPaths,
            mapRevisions = mapRevisions,
            responseCache = responseCache,
            recordingStateStore =
                RoomSearchRecordingStateStore(
                    database = database,
                    nowMillis = { UPDATED_AT_MS }
                )
        )

    private fun sessionContext(): SearchMapSessionContext =
        SearchMapSessionContext(
            incidentId = INCIDENT_ID,
            currentOpId = OP_ID,
            currentDutyShiftId = null,
            policePhoneId = POLICE_PHONE_ID,
            accountId = ACCOUNT_ID
        )

    private fun activeRecordingState(): SearchRecordingStateEntity =
        SearchRecordingStateEntity(
            accountId = ACCOUNT_ID,
            incidentId = INCIDENT_ID,
            opId = OP_ID,
            searchPathId = PATH_ID,
            lifecycleStatus = "ACTIVE",
            activeStartedAt = STARTED_AT_MS,
            accumulatedElapsed = 0L,
            updatedAt = 1L
        )

    private fun recordingPathBody(status: String = "RECORDING"): String =
        """
        {
          "paths": [{
            "id": "$PATH_ID",
            "incidentId": "$INCIDENT_ID",
            "opId": "$OP_ID",
            "accountId": "$ACCOUNT_ID",
            "status": "$status",
            "startedAt": "2026-05-18T04:53:12.331Z"
          }]
        }
        """.trimIndent()

    private fun notFoundResponse(): SuriMapApiResponse =
        SuriMapApiResponse(statusCode = 404, body = null, errorCode = null)

    private companion object {
        val INCIDENT_ID = incidentIdFixture("offline-server-recording-001")
        val OP_ID = opIdFixture("offline-server-recording-001")
        val PATH_ID = pathIdFixture("offline-server-recording-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("offline-server-recording-001")
        const val ACCOUNT_ID = "account-offline-server-recording-001"
        const val STARTED_AT_MS = 1_779_079_992_331L
        const val UPDATED_AT_MS = 1_779_080_000_000L
    }
}
