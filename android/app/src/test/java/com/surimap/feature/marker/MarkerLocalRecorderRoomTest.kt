package com.surimap.feature.marker

import androidx.room.Room
import com.surimap.core.database.SuriMapDatabase
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.NoopOutboxSender
import com.surimap.core.sync.OutboxSender
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.RoomOutboxReplay
import com.surimap.core.sync.RoomSyncClient
import com.surimap.core.sync.SendResult
import com.surimap.core.sync.SyncClient
import com.surimap.feature.marker.data.MarkerLocation
import com.surimap.feature.marker.data.MarkerPhotoAttachInput
import com.surimap.feature.marker.data.MarkerPhotoUploadUrlInput
import com.surimap.feature.marker.data.MarkerWriteContext
import com.surimap.feature.marker.data.MarkerWriteResult
import com.surimap.feature.marker.data.MarkerLocalRecorder
import com.surimap.feature.marker.data.MarkerUpsertInput
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.markerIdFixture
import com.surimap.testing.operationIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.photoIdFixture
import com.surimap.testing.policePhoneIdFixture
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MarkerLocalRecorderRoomTest {
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
    fun markerReplayAckClearsDraftAndMarksLocalMarkerSynced() = runBlocking {
        val replayEligibleTs = Instant.ofEpochMilli(System.currentTimeMillis())
        val recorder =
            MarkerLocalRecorder(
                syncClient = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                localMarkerDao = database.localMarkerDao(),
                now = { replayEligibleTs },
                clockSyncedAt = { replayEligibleTs },
                sequenceSource = sequenceSource(50),
                idFactory = idFactory()
            )
        val replay = RoomOutboxReplay(database.outboxDao(), NoopOutboxSender)

        val create =
            recorder.createMarker(
                context = CONTEXT,
                input = MarkerUpsertInput(type = "CLUE", location = LOCATION, memo = "등산로 입구 제보")
            ) as MarkerWriteResult.Enqueued

        assertNotNull(database.localWriteDraftDao().findById(create.outboxId))

        replay.flushPending(policePhoneId = POLICE_PHONE_ID, incidentId = INCIDENT_ID)

        val row = database.outboxDao().findById(create.outboxId)!!
        assertEquals(OutboxStatus.ACKED.name, row.idempotencyStatus)
        assertEquals(HarnessSyncStatus.SYNCED.name, row.localMirrorStatus)
        assertNull(database.localWriteDraftDao().findById(create.outboxId))
        assertEquals(
            HarnessSyncStatus.SYNCED.name,
            database.localMarkerDao().findByOutboxId(create.outboxId)!!.syncStatus
        )
    }

    @Test
    fun markerAndPhotoWritesPersistPendingOutboxRowsBeforeNetworkReplay() = runBlocking {
        val recorder =
            MarkerLocalRecorder(
                syncClient = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                localMarkerDao = database.localMarkerDao(),
                now = { CLIENT_TS },
                sequenceSource = sequenceSource(50),
                idFactory = idFactory()
            )

        val create =
            recorder.createMarker(
                context = CONTEXT,
                input = MarkerUpsertInput(type = "CLUE", location = LOCATION, memo = "등산로 입구 제보")
            ) as MarkerWriteResult.Enqueued
        recorder.updateMarker(
            context = CONTEXT,
            markerId = MARKER_ID,
            version = 3,
            input = MarkerUpsertInput(memo = "메모 수정")
        )
        recorder.deleteMarker(
            context = CONTEXT,
            markerId = MARKER_ID,
            version = 4,
            reason = "duplicated"
        )
        val upload =
            recorder.requestPhotoUploadUrl(
                context = CONTEXT,
                input =
                MarkerPhotoUploadUrlInput(
                    markerId = MARKER_ID,
                    contentType = "image/jpeg",
                    sizeBytes = 512_000,
                    checksumSha256 = "sha256-local-photo"
                )
            ) as MarkerWriteResult.Enqueued
        recorder.attachPhoto(
            context = CONTEXT,
            input =
            MarkerPhotoAttachInput(
                markerId = MARKER_ID,
                photoId = PHOTO_ID,
                contentType = "image/jpeg",
                sizeBytes = 512_000,
                checksumSha256 = "sha256-local-photo",
                parentOperationId = upload.operationId
            )
        )

        val rows = database.outboxDao().findByIncidentId(INCIDENT_ID)

        assertEquals(
            listOf("POST", "PATCH", "DELETE", "POST", "POST"),
            rows.map { it.requestMethod }
        )
        assertEquals(
            listOf(
                "/api/markers",
                "/api/markers/$MARKER_ID",
                "/api/markers/$MARKER_ID",
                "/api/markers/$MARKER_ID/photos/upload-url",
                "/api/markers/$MARKER_ID/photos/$PHOTO_ID/attach"
            ),
            rows.map { it.requestPath }
        )
        assertEquals(listOf("MARKER", "MARKER", "MARKER", "PHOTO", "PHOTO"), rows.map { it.dependencyGroup })
        assertTrue(rows.all { it.idempotencyStatus == "PENDING" })
        assertTrue(rows.all { it.localMirrorStatus == "PENDING_SEND" })
        assertEquals(listOf(50L, 51L, 52L, 53L, 54L), rows.map { it.sequence })
        assertEquals(upload.operationId, rows.last().parentOperationId)

        val draft = database.localWriteDraftDao().findById(create.outboxId)
        assertEquals("marker", draft!!.entityType)
        assertEquals(create.operationId, draft.operationId)
        assertTrue(draft.payload.contains("\"type\":\"CLUE\""))

        val pendingMarkers = database.localMarkerDao().findPendingByIncidentAndPolicePhone(INCIDENT_ID, POLICE_PHONE_ID)
        assertEquals(1, pendingMarkers.size)
        assertEquals(create.operationId, pendingMarkers.single().localMarkerId)
        assertEquals("CLUE", pendingMarkers.single().type)
        assertEquals(126.9134, pendingMarkers.single().lon, 0.0)
        assertEquals(35.1631, pendingMarkers.single().lat, 0.0)
        assertEquals("PENDING_SEND", pendingMarkers.single().syncStatus)
    }

    @Test
    fun allSc06MarkerTypesPersistAsPendingLocalMarkerMirrors() = runBlocking {
        val recorder =
            MarkerLocalRecorder(
                syncClient = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                localMarkerDao = database.localMarkerDao(),
                now = { CLIENT_TS },
                sequenceSource = sequenceSource(100),
                idFactory = idFactory()
            )
        val markerTypes =
            listOf(
                "CLUE" to null,
                "PERSON_FOUND" to null,
                "FIELD_CONDITION" to null,
                "SUPPORT_REQUEST" to "POLICE_DOG",
                "NOTE" to null
            )

        markerTypes.forEach { (type, supportRequestType) ->
            recorder.createMarker(
                context = CONTEXT,
                input =
                MarkerUpsertInput(
                    type = type,
                    location = LOCATION,
                    supportRequestType = supportRequestType,
                    memo = "SC-06 $type"
                )
            )
        }

        val rows = database.outboxDao().findByIncidentId(INCIDENT_ID)
        val pendingMarkers = database.localMarkerDao().findPendingByIncidentAndPolicePhone(INCIDENT_ID, POLICE_PHONE_ID)
        val pendingByType = pendingMarkers.associateBy { it.type }

        assertEquals(5, rows.size)
        assertEquals(markerTypes.map { it.first }.toSet(), pendingByType.keys)
        assertEquals("POLICE_DOG", pendingByType["SUPPORT_REQUEST"]?.supportRequestType)
        assertTrue(pendingMarkers.all { it.syncStatus == "PENDING_SEND" })
        assertTrue(pendingMarkers.all { it.lon == 126.9134 && it.lat == 35.1631 })
    }

    @Test
    fun markerCreateReplayMarksLocalMarkerSynced() = runBlocking {
        val replayableClientTs = Instant.ofEpochMilli(System.currentTimeMillis())
        val recorder =
            MarkerLocalRecorder(
                syncClient = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                localMarkerDao = database.localMarkerDao(),
                now = { replayableClientTs },
                sequenceSource = sequenceSource(80),
                idFactory = idFactory()
            )
        val create =
            recorder.createMarker(
                context = CONTEXT,
                input = MarkerUpsertInput(type = "CLUE", location = LOCATION, memo = "등산로 입구 제보")
            ) as MarkerWriteResult.Enqueued
        val replay =
            RoomOutboxReplay(
                database.outboxDao(),
                OutboxSender { SendResult.ACKED }
            )

        replay.flushPending(policePhoneId = POLICE_PHONE_ID, incidentId = INCIDENT_ID)

        val row = database.outboxDao().findByIncidentId(INCIDENT_ID).single()
        assertEquals("ACKED", row.idempotencyStatus)
        assertEquals("SYNCED", row.localMirrorStatus)
        assertEquals("SYNCED", database.localMarkerDao().findById(create.operationId)!!.syncStatus)
        assertTrue(database.localMarkerDao().findPendingByIncidentAndPolicePhone(INCIDENT_ID, POLICE_PHONE_ID).isEmpty())
    }

    @Test
    fun markerCreateKeepsLocalMarkerSyncedWhenReplayAcksBeforeLocalMarkerInsert() = runBlocking {
        val replayableClientTs = Instant.ofEpochMilli(System.currentTimeMillis())
        val roomSyncClient = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao())
        val replay =
            RoomOutboxReplay(
                database.outboxDao(),
                OutboxSender { SendResult.ACKED }
            )
        val replayBeforeLocalMarkerInsert =
            SyncClient { writeOperation ->
                val result = roomSyncClient.enqueue(writeOperation)
                replay.flushPending(policePhoneId = POLICE_PHONE_ID, incidentId = INCIDENT_ID)
                result
            }
        val recorder =
            MarkerLocalRecorder(
                syncClient = replayBeforeLocalMarkerInsert,
                localMarkerDao = database.localMarkerDao(),
                now = { replayableClientTs },
                sequenceSource = sequenceSource(90),
                idFactory = idFactory()
            )

        val create =
            recorder.createMarker(
                context = CONTEXT,
                input = MarkerUpsertInput(type = "CLUE", location = LOCATION, memo = "등산로 입구 제보")
            ) as MarkerWriteResult.Enqueued

        val row = database.outboxDao().findByIncidentId(INCIDENT_ID).single()
        assertEquals("ACKED", row.idempotencyStatus)
        assertEquals("SYNCED", row.localMirrorStatus)
        assertEquals("SYNCED", database.localMarkerDao().findById(create.operationId)!!.syncStatus)
        assertTrue(database.localMarkerDao().findPendingByIncidentAndPolicePhone(INCIDENT_ID, POLICE_PHONE_ID).isEmpty())
    }

    private fun sequenceSource(first: Long): () -> Long {
        var next = first
        return { next++ }
    }

    private fun idFactory(): (String) -> String {
        var next = 1
        return { prefix -> operationIdFixture("${prefix.removePrefix("op-")}-${next.toString().padStart(3, '0')}").also { next++ } }
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-first-001")
        val MARKER_ID = markerIdFixture("precinct-clue-001")
        val PHOTO_ID = photoIdFixture("precinct-clue-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
        val LOCATION = MarkerLocation(lon = 126.9134, lat = 35.1631)
        val CONTEXT =
            MarkerWriteContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                policePhoneId = POLICE_PHONE_ID
            )
    }
}
