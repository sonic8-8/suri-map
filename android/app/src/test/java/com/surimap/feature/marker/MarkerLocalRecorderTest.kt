package com.surimap.feature.marker

import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
import com.surimap.feature.marker.data.MarkerLocation
import com.surimap.feature.marker.data.MarkerPhotoAttachInput
import com.surimap.feature.marker.data.MarkerPhotoUploadUrlInput
import com.surimap.feature.marker.data.MarkerWriteContext
import com.surimap.feature.marker.data.MarkerWriteResult
import com.surimap.feature.marker.data.MarkerLocalRecorder
import com.surimap.feature.marker.data.MarkerUpsertInput
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerLocalRecorderTest {

    @Test
    fun markerAndPhotoWritesEnqueueCanonicalOperationsInOrder() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder =
            MarkerLocalRecorder(
                syncClient = syncClient,
                now = { CLIENT_TS },
                sequenceSource = sequenceSource(30),
                idFactory = idFactory()
            )

        val create =
            recorder.createMarker(
                context = CONTEXT,
                input =
                MarkerUpsertInput(
                    type = "SUPPORT_REQUEST",
                    location = LOCATION,
                    supportRequestType = "DRONE",
                    memo = "북측 능선 확인 요청"
                )
            ) as MarkerWriteResult.Enqueued
        val update =
            recorder.updateMarker(
                context = CONTEXT,
                markerId = MARKER_ID,
                version = 3,
                input = MarkerUpsertInput(type = "NOTE", location = LOCATION, memo = "메모 수정")
            ) as MarkerWriteResult.Enqueued
        val delete =
            recorder.deleteMarker(
                context = CONTEXT,
                markerId = MARKER_ID,
                version = 4,
                reason = "duplicated"
            ) as MarkerWriteResult.Enqueued
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
        val attach =
            recorder.attachPhoto(
                context = CONTEXT,
                input =
                MarkerPhotoAttachInput(
                    markerId = MARKER_ID,
                    photoId = PHOTO_ID,
                    contentType = "image/jpeg",
                    sizeBytes = 512_000,
                    width = 1280,
                    height = 960,
                    checksumSha256 = "sha256-local-photo",
                    parentOperationId = upload.operationId
                )
            ) as MarkerWriteResult.Enqueued

        assertEquals(
            listOf(
                OP_MARKER_CREATE_ID,
                OP_MARKER_UPDATE_ID,
                OP_MARKER_DELETE_ID,
                OP_PHOTO_UPLOAD_ID,
                OP_PHOTO_ATTACH_ID
            ),
            syncClient.operations.map { it.operationId }
        )
        assertEquals(listOf(30L, 31L, 32L, 33L, 34L), syncClient.operations.map { it.sequence })
        assertEquals(
            listOf(
                "/api/markers",
                "/api/markers/$MARKER_ID",
                "/api/markers/$MARKER_ID",
                "/api/markers/$MARKER_ID/photos/upload-url",
                "/api/markers/$MARKER_ID/photos/$PHOTO_ID/attach"
            ),
            syncClient.operations.map { it.endpoint }
        )
        assertEquals(
            listOf(DependencyGroup.MARKER, DependencyGroup.MARKER, DependencyGroup.MARKER, DependencyGroup.PHOTO, DependencyGroup.PHOTO),
            syncClient.operations.map { it.dependencyGroup }
        )
        assertTrue(syncClient.operations.all { it.idempotencyKey == "idem-${it.operationId}" })
        assertTrue(syncClient.operations.all { it.clockOffsetMs == 0L })
        assertTrue(syncClient.operations.all { it.clockSyncedAt == CLIENT_TS })
        assertEquals(OP_MARKER_CREATE_ID, create.operationId)
        assertEquals(OP_MARKER_UPDATE_ID, update.operationId)
        assertEquals(OP_MARKER_DELETE_ID, delete.operationId)
        assertEquals(OP_PHOTO_ATTACH_ID, attach.operationId)
        assertEquals(upload.operationId, syncClient.operations.last().parentOperationId)
    }

    @Test
    fun missingCurrentContextDoesNotCreateOutboxOperation() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder = MarkerLocalRecorder(syncClient = syncClient)

        val result =
            recorder.createMarker(
                context = CONTEXT.copy(opId = null),
                input = MarkerUpsertInput(type = "CLUE", location = LOCATION)
            )

        assertEquals(MarkerWriteResult.Blocked, result)
        assertTrue(syncClient.operations.isEmpty())
    }

    @Test
    fun supportRequestRequiresSubtypeBeforeEnqueue() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder = MarkerLocalRecorder(syncClient = syncClient)

        val result =
            recorder.createMarker(
                context = CONTEXT,
                input = MarkerUpsertInput(type = "SUPPORT_REQUEST", location = LOCATION)
            )

        assertEquals(MarkerWriteResult.Blocked, result)
        assertTrue(syncClient.operations.isEmpty())
    }

    @Test
    fun invalidLocationAndOversizedPhotoAreBlockedLocally() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder = MarkerLocalRecorder(syncClient = syncClient)

        val markerResult =
            recorder.createMarker(
                context = CONTEXT,
                input = MarkerUpsertInput(type = "CLUE", location = MarkerLocation(lon = Double.NaN, lat = LOCATION.lat))
            )
        val photoResult =
            recorder.requestPhotoUploadUrl(
                context = CONTEXT,
                input =
                MarkerPhotoUploadUrlInput(
                    markerId = MARKER_ID,
                    contentType = "image/jpeg",
                    sizeBytes = 10_485_761
                )
            )

        assertEquals(MarkerWriteResult.Blocked, markerResult)
        assertEquals(MarkerWriteResult.Blocked, photoResult)
        assertTrue(syncClient.operations.isEmpty())
    }

    @Test
    fun attachWithoutKnownPhotoIdIsBlockedLocally() = runBlocking {
        val syncClient = CapturingSyncClient()
        val recorder = MarkerLocalRecorder(syncClient = syncClient)

        val result =
            recorder.attachPhoto(
                context = CONTEXT,
                input =
                MarkerPhotoAttachInput(
                    markerId = MARKER_ID,
                    photoId = "",
                    contentType = "image/jpeg",
                    sizeBytes = 512_000
                )
            )

        assertEquals(MarkerWriteResult.Blocked, result)
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
        val ids =
            listOf(
                OP_MARKER_CREATE_ID,
                OP_MARKER_UPDATE_ID,
                OP_MARKER_DELETE_ID,
                OP_PHOTO_UPLOAD_ID,
                OP_PHOTO_ATTACH_ID
            )
        var next = 0
        return { ids[next++] }
    }

    private companion object {
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
        const val OP_ID = "88888888-8888-8888-8888-888888880001"
        const val MARKER_ID = "55555555-5555-5555-5555-555555550001"
        const val PHOTO_ID = "55555555-5555-5555-5555-555555550101"
        const val POLICE_PHONE_ID = "50000000-0000-0000-0000-000000000001"
        const val OP_MARKER_CREATE_ID = "22222222-2222-4222-8222-222222222001"
        const val OP_MARKER_UPDATE_ID = "22222222-2222-4222-8222-222222222002"
        const val OP_MARKER_DELETE_ID = "22222222-2222-4222-8222-222222222003"
        const val OP_PHOTO_UPLOAD_ID = "22222222-2222-4222-8222-222222222004"
        const val OP_PHOTO_ATTACH_ID = "22222222-2222-4222-8222-222222222005"
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
        val LOCATION = MarkerLocation(lon = 126.9565, lat = 37.5712)
        val CONTEXT =
            MarkerWriteContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                policePhoneId = POLICE_PHONE_ID
            )
    }
}
