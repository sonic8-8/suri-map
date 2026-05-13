package com.surimap.core.sync

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.policePhoneIdFixture
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxReplaySchedulerTest {

    @Test
    fun schedulesUniqueReplayWorkWithIncidentPhoneAndAuthContext() {
        val enqueued = mutableListOf<EnqueuedWork>()
        val scheduler =
            OutboxReplayScheduler { name, policy, request ->
                enqueued += EnqueuedWork(name, policy, request)
            }

        scheduler.schedule(
            OutboxReplayWorkRequest(
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID,
                apiBaseUrl = "https://suri-map.internal",
                accessToken = "bootstrap-token-1"
            )
        )

        val work = enqueued.single()
        assertEquals("outbox-replay-$INCIDENT_ID-$POLICE_PHONE_ID", work.name)
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, work.policy)
        assertEquals(INCIDENT_ID, work.request.workSpec.input.getString(OutboxWorker.KEY_INCIDENT_ID))
        assertEquals(POLICE_PHONE_ID, work.request.workSpec.input.getString(OutboxWorker.KEY_POLICE_PHONE_ID))
        assertEquals("https://suri-map.internal", work.request.workSpec.input.getString(OutboxWorker.KEY_API_BASE_URL))
        assertEquals("bootstrap-token-1", work.request.workSpec.input.getString(OutboxWorker.KEY_ACCESS_TOKEN))
        assertEquals(NetworkType.CONNECTED, work.request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, work.request.workSpec.backoffPolicy)
        assertEquals(10_000L, work.request.workSpec.backoffDelayDuration)
    }

    @Test
    fun skipsSchedulingWhenRequiredIdentityIsBlank() {
        val enqueued = mutableListOf<EnqueuedWork>()
        val scheduler =
            OutboxReplayScheduler { name, policy, request ->
                enqueued += EnqueuedWork(name, policy, request)
            }

        scheduler.schedule(
            OutboxReplayWorkRequest(
                incidentId = "",
                policePhoneId = POLICE_PHONE_ID,
                apiBaseUrl = "https://suri-map.internal"
            )
        )

        assertTrue(enqueued.isEmpty())
    }

    @Test
    fun schedulingSyncClientSchedulesReplayAfterPendingAppWriteIsPersisted() = runBlocking {
        val scheduled = mutableListOf<OutboxReplayWorkRequest>()
        val delegate =
            SyncClient { writeOperation ->
                assertEquals(INCIDENT_ID, writeOperation.incidentId)
                EnqueueResult(
                    outboxId = "outbox-1",
                    operationId = writeOperation.operationId,
                    status = OutboxStatus.PENDING,
                    harnessStatus = HarnessSyncStatus.PENDING_SEND
                )
            }
        val client =
            SchedulingSyncClient(
                delegate = delegate,
                scheduleReplay = { scheduled += it },
                apiBaseUrl = "https://suri-map.internal",
                accessToken = "bootstrap-token-1"
            )

        client.enqueue(appWriteOperation())

        assertEquals(
            listOf(
                OutboxReplayWorkRequest(
                    incidentId = INCIDENT_ID,
                    policePhoneId = POLICE_PHONE_ID,
                    apiBaseUrl = "https://suri-map.internal",
                    accessToken = "bootstrap-token-1"
                )
            ),
            scheduled
        )
    }

    @Test
    fun schedulingSyncClientDoesNotScheduleFinalRejectedWrites() = runBlocking {
        var scheduled = false
        val client =
            SchedulingSyncClient(
                delegate =
                SyncClient {
                    EnqueueResult(
                        outboxId = "outbox-1",
                        operationId = it.operationId,
                        status = OutboxStatus.FAILED_FINAL,
                        harnessStatus = HarnessSyncStatus.FAILED
                    )
                },
                scheduleReplay = { scheduled = true },
                apiBaseUrl = "https://suri-map.internal",
                accessToken = "bootstrap-token-1"
            )

        client.enqueue(appWriteOperation())

        assertTrue(!scheduled)
    }

    private fun appWriteOperation(): LocalWriteOperation =
        LocalWriteOperation(
            operationId = "op-marker-create-1",
            incidentId = INCIDENT_ID,
            policePhoneId = POLICE_PHONE_ID,
            dependencyGroup = DependencyGroup.MARKER,
            sequence = 10L,
            method = "POST",
            endpoint = "/api/markers",
            payload = """{"type":"CLUE"}""",
            bodyHash = "sha256:marker",
            idempotencyKey = "idem-op-marker-create-1",
            clientTs = Instant.EPOCH,
            clockOffsetMs = 0L,
            clockSyncedAt = Instant.EPOCH,
            opId = "op-1",
            entityType = "marker"
        )

    private data class EnqueuedWork(
        val name: String,
        val policy: ExistingWorkPolicy,
        val request: OneTimeWorkRequest
    )

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-car-01")
    }
}
