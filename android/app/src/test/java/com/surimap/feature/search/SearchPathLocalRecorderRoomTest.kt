package com.surimap.feature.search

import androidx.room.Room
import com.surimap.core.database.SuriMapDatabase
import com.surimap.core.sync.RoomSyncClient
import com.surimap.feature.search.data.SearchPathLocalRecorder
import com.surimap.feature.search.data.SearchPathWriteContext
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
    fun startSearchPathPersistsPendingOutboxRowBeforeNetworkReplay() = runBlocking {
        val recorder =
            SearchPathLocalRecorder(
                syncClient = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                now = { CLIENT_TS },
                sequenceSource = { 42L },
                idFactory = { prefix -> "$prefix-001" }
            )

        recorder.start(
            SearchPathWriteContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                policePhoneId = POLICE_PHONE_ID
            )
        )

        val row = database.outboxDao().findByIncidentId(INCIDENT_ID).single()

        assertEquals("op-path-start-001", row.operationId)
        assertEquals("/api/search-paths", row.requestPath)
        assertEquals("PATH", row.dependencyGroup)
        assertEquals("PENDING", row.idempotencyStatus)
        assertEquals("PENDING_SEND", row.localMirrorStatus)
        assertEquals(POLICE_PHONE_ID, row.policePhoneId)
        assertEquals(OP_ID, row.opId)
    }

    private companion object {
        const val INCIDENT_ID = "inc-precinct-first-001"
        const val OP_ID = "op-precinct-first-001"
        const val POLICE_PHONE_ID = "phone-precinct-001"
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
    }
}
