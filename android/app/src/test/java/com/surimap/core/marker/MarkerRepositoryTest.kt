package com.surimap.core.marker

import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerRepositoryTest {

    @Test
    fun createMarkerEnqueuesAppOnlyMarkerWrite() = runBlocking {
        val syncClient = CapturingSyncClient()
        val repository = MarkerRepository(syncClient = syncClient)

        repository.createMarker(
            CreateMarkerCommand(
                operationId = "op-marker-create-001",
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-marker-create-001",
                sequence = 20,
                type = "SUPPORT_REQUEST",
                lon = 126.970123,
                lat = 37.580123,
                supportRequestType = "DRONE",
                memo = "계곡 북측 확인 요청",
                clientTs = CLIENT_TS,
                clockOffsetMs = 90,
                clockSyncedAt = CLOCK_SYNCED_AT
            )
        )

        val operation = syncClient.lastOperation!!
        assertEquals(DependencyGroup.MARKER, operation.dependencyGroup)
        assertEquals("POST", operation.method)
        assertEquals("/api/markers", operation.endpoint)
        assertEquals("marker", operation.entityType)
        assertEquals("idem-marker-create-001", operation.idempotencyKey)
        assertEquals(POLICE_PHONE_ID, operation.policePhoneId)
        assertEquals(
            """{"incidentId":"$INCIDENT_ID","opId":"$OP_ID","type":"SUPPORT_REQUEST","location":{"type":"Point","coordinates":[126.970123,37.580123]},"supportRequestType":"DRONE","memo":"계곡 북측 확인 요청","clientTs":"2026-05-11T06:00:00Z","clockOffsetMs":90}""",
            operation.payload
        )
        assertTrue(operation.bodyHash.startsWith("sha256:"))
    }

    @Test
    fun updateAndDeleteMarkerUseMarkerIdPathAndVersionPayload() = runBlocking {
        val syncClient = CapturingSyncClient()
        val repository = MarkerRepository(syncClient = syncClient)

        repository.updateMarker(
            UpdateMarkerCommand(
                operationId = "op-marker-update-001",
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                markerId = MARKER_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-marker-update-001",
                sequence = 21,
                version = 3,
                type = "NOTE",
                memo = "메모 수정",
                lon = 126.970223,
                lat = 37.580223,
                clientTs = CLIENT_TS,
                clockSyncedAt = CLOCK_SYNCED_AT
            )
        )

        var operation = syncClient.lastOperation!!
        assertEquals("PATCH", operation.method)
        assertEquals("/api/markers/$MARKER_ID", operation.endpoint)
        assertEquals(MARKER_ID, operation.entityId)
        assertEquals(
            """{"version":3,"location":{"type":"Point","coordinates":[126.970223,37.580223]},"memo":"메모 수정","type":"NOTE"}""",
            operation.payload
        )

        repository.deleteMarker(
            DeleteMarkerCommand(
                operationId = "op-marker-delete-001",
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                markerId = MARKER_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-marker-delete-001",
                sequence = 22,
                version = 4,
                reason = "duplicated",
                clientTs = CLIENT_TS,
                clockSyncedAt = CLOCK_SYNCED_AT
            )
        )

        operation = syncClient.lastOperation!!
        assertEquals("DELETE", operation.method)
        assertEquals("/api/markers/$MARKER_ID", operation.endpoint)
        assertEquals(
            """{"version":4,"reason":"duplicated"}""",
            operation.payload
        )
    }

    @Test
    fun photoUploadUrlAndAttachUseCanonicalPhotoPaths() = runBlocking {
        val syncClient = CapturingSyncClient()
        val repository = MarkerRepository(syncClient = syncClient)

        repository.requestPhotoUploadUrl(
            PhotoUploadUrlCommand(
                operationId = "op-photo-upload-url-001",
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                markerId = MARKER_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-photo-upload-url-001",
                sequence = 23,
                contentType = "image/jpeg",
                sizeBytes = 512_000,
                checksumSha256 = "sha256-local-photo",
                clientTs = CLIENT_TS,
                clockSyncedAt = CLOCK_SYNCED_AT
            )
        )

        var operation = syncClient.lastOperation!!
        assertEquals(DependencyGroup.PHOTO, operation.dependencyGroup)
        assertEquals("POST", operation.method)
        assertEquals("/api/markers/$MARKER_ID/photos/upload-url", operation.endpoint)
        assertEquals(
            """{"contentType":"image/jpeg","sizeBytes":512000,"checksumSha256":"sha256-local-photo"}""",
            operation.payload
        )

        repository.attachPhoto(
            PhotoAttachCommand(
                operationId = "op-photo-attach-001",
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                markerId = MARKER_ID,
                photoId = PHOTO_ID,
                policePhoneId = POLICE_PHONE_ID,
                idempotencyKey = "idem-photo-attach-001",
                sequence = 24,
                sizeBytes = 512_000,
                contentType = "image/jpeg",
                width = 1280,
                height = 960,
                checksumSha256 = "sha256-local-photo",
                clientTs = CLIENT_TS,
                clockSyncedAt = CLOCK_SYNCED_AT,
                parentOperationId = "op-photo-upload-url-001"
            )
        )

        operation = syncClient.lastOperation!!
        assertEquals("POST", operation.method)
        assertEquals("/api/markers/$MARKER_ID/photos/$PHOTO_ID/attach", operation.endpoint)
        assertEquals(PHOTO_ID, operation.entityId)
        assertEquals("op-photo-upload-url-001", operation.parentOperationId)
        assertEquals(
            """{"sizeBytes":512000,"contentType":"image/jpeg","width":1280,"height":960,"checksumSha256":"sha256-local-photo"}""",
            operation.payload
        )
    }

    private class CapturingSyncClient : SyncClient {
        var lastOperation: LocalWriteOperation? = null

        override suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult {
            lastOperation = writeOperation
            return EnqueueResult(
                outboxId = "outbox-001",
                operationId = writeOperation.operationId,
                status = OutboxStatus.PENDING,
                harnessStatus = HarnessSyncStatus.PENDING_SEND
            )
        }
    }

    private companion object {
        const val INCIDENT_ID = "inc-precinct-first-001"
        const val OP_ID = "op-precinct-first-001"
        const val MARKER_ID = "mk-precinct-clue-001"
        const val PHOTO_ID = "photo-precinct-clue-001"
        const val POLICE_PHONE_ID = "phone-precinct-001"
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
        val CLOCK_SYNCED_AT: Instant = Instant.parse("2026-05-11T05:59:30Z")
    }
}
