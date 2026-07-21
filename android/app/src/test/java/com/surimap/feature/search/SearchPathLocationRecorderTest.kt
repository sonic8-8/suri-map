package com.surimap.feature.search

import com.surimap.core.location.GpsLocationFix
import com.surimap.core.location.LocationUpdates
import com.surimap.core.location.LocationUpdatesHandle
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
import com.surimap.feature.search.data.SearchPathGpsBatchRecorder
import com.surimap.feature.search.data.SearchPathLocalRecorder
import com.surimap.feature.search.data.SearchPathLocationRecorder
import com.surimap.feature.search.data.SearchPathWriteContext
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SearchPathLocationRecorderTest {

    @Test
    fun sameActivePathKeepsCollectingGpsUntilRecordingStops() = runBlocking {
        val locationUpdates = FakeLocationUpdates()
        val syncClient = CapturingSyncClient()
        val batchRecorder =
            SearchPathGpsBatchRecorder(
                localRecorder = SearchPathLocalRecorder(syncClient = syncClient),
                pointIdFactory = pointIdFactory()
            )
        val recorder =
            SearchPathLocationRecorder(
                locationUpdates = locationUpdates,
                batchRecorder = batchRecorder,
                coroutineScope = this
            )

        recorder.start(CONTEXT, PATH_ID)
        locationUpdates.emit(fix(0))

        recorder.start(CONTEXT, PATH_ID)
        locationUpdates.emit(fix(10))

        assertEquals(1, locationUpdates.startCount)
        assertEquals(0, locationUpdates.stopCount)

        recorder.stop()

        assertEquals(1, locationUpdates.stopCount)
        assertEquals(PATH_ID, syncClient.operations.single().entityId)
    }

    @Test
    fun stoppingRecordingFinishesGpsFlushWhenCallerIsCancelled() = runBlocking {
        val locationUpdates = FakeLocationUpdates()
        val syncClient = BlockingSyncClient()
        val recorder =
            SearchPathLocationRecorder(
                locationUpdates = locationUpdates,
                batchRecorder =
                SearchPathGpsBatchRecorder(
                    localRecorder = SearchPathLocalRecorder(syncClient = syncClient),
                    pointIdFactory = pointIdFactory()
                ),
                coroutineScope = this
            )

        recorder.start(CONTEXT, PATH_ID)
        locationUpdates.emit(fix(0))
        locationUpdates.emit(fix(5))

        val stopJob = launch { recorder.stop() }
        syncClient.enqueueStarted.await()
        stopJob.cancel()
        syncClient.allowEnqueue.complete(Unit)
        stopJob.join()

        assertEquals(PATH_ID, syncClient.operations.single().entityId)
    }

    @Test
    fun concurrentStopsWaitForTheSameGpsFlush() = runBlocking {
        val locationUpdates = FakeLocationUpdates()
        val syncClient = BlockingSyncClient()
        val recorder =
            SearchPathLocationRecorder(
                locationUpdates = locationUpdates,
                batchRecorder =
                SearchPathGpsBatchRecorder(
                    localRecorder = SearchPathLocalRecorder(syncClient = syncClient),
                    pointIdFactory = pointIdFactory()
                ),
                coroutineScope = this
            )

        recorder.start(CONTEXT, PATH_ID)
        locationUpdates.emit(fix(0))
        locationUpdates.emit(fix(5))

        val firstStop = launch { recorder.stop() }
        syncClient.enqueueStarted.await()
        val secondStop = launch { recorder.stop() }
        yield()
        val secondStopReturnedBeforeFlush = secondStop.isCompleted

        syncClient.allowEnqueue.complete(Unit)
        firstStop.join()
        secondStop.join()
        assertFalse(secondStopReturnedBeforeFlush)
        assertEquals(PATH_ID, syncClient.operations.single().entityId)
    }

    @Test
    fun singlePendingFixIsSavedWhenTheSamePathResumes() = runBlocking {
        val locationUpdates = FakeLocationUpdates()
        val syncClient = CapturingSyncClient()
        val recorder =
            SearchPathLocationRecorder(
                locationUpdates = locationUpdates,
                batchRecorder =
                SearchPathGpsBatchRecorder(
                    localRecorder = SearchPathLocalRecorder(syncClient = syncClient),
                    pointIdFactory = pointIdFactory()
                ),
                coroutineScope = this
            )

        recorder.start(CONTEXT, PATH_ID)
        locationUpdates.emit(fix(0))
        recorder.stop()

        recorder.start(CONTEXT, PATH_ID)
        locationUpdates.emit(fix(5))
        recorder.stop()

        assertEquals(PATH_ID, syncClient.operations.single().entityId)
    }

    private class FakeLocationUpdates : LocationUpdates {
        var startCount: Int = 0
            private set
        var stopCount: Int = 0
            private set
        private var onFix: ((GpsLocationFix) -> Unit)? = null

        override fun start(onFix: (GpsLocationFix) -> Unit): LocationUpdatesHandle {
            startCount += 1
            this.onFix = onFix
            return LocationUpdatesHandle {
                stopCount += 1
                this.onFix = null
            }
        }

        fun emit(fix: GpsLocationFix) {
            onFix?.invoke(fix)
        }
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

    private class BlockingSyncClient : SyncClient {
        val enqueueStarted = CompletableDeferred<Unit>()
        val allowEnqueue = CompletableDeferred<Unit>()
        val operations = mutableListOf<LocalWriteOperation>()

        override suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult {
            enqueueStarted.complete(Unit)
            allowEnqueue.await()
            operations += writeOperation
            return EnqueueResult(
                outboxId = "outbox-${operations.size}",
                operationId = writeOperation.operationId,
                status = OutboxStatus.PENDING,
                harnessStatus = HarnessSyncStatus.PENDING_SEND
            )
        }
    }

    private fun fix(second: Long): GpsLocationFix = GpsLocationFix(
        lon = 126.970000 + (second * 0.00001),
        lat = 37.580000 + (second * 0.00001),
        bearingDegrees = null,
        speedMps = 1.4,
        horizontalAccuracyM = 5,
        capturedAt = CLIENT_TS.plusSeconds(second)
    )

    private fun pointIdFactory(): () -> String {
        var next = 1
        return { "pt-location-${(next++).toString().padStart(3, '0')}" }
    }

    private companion object {
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
        const val OP_ID = "88888888-8888-8888-8888-888888880001"
        const val PATH_ID = "ffffffff-ffff-ffff-ffff-ffffffff0001"
        const val ACCOUNT_ID = "account-path-001"
        const val POLICE_PHONE_ID = "50000000-0000-0000-0000-000000000001"
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
        val CONTEXT =
            SearchPathWriteContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                accountId = ACCOUNT_ID,
                policePhoneId = POLICE_PHONE_ID
            )
    }
}
