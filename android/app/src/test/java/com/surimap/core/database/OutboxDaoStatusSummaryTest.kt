package com.surimap.core.database

import androidx.room.Room
import com.surimap.core.sync.DependencyGroup
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxDaoStatusSummaryTest {
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
    fun statusSummaryCountsPendingRetryableAndFinalRowsForIncidentPhone() = runBlocking {
        val dao = database.outboxDao()
        dao.upsert(row("pending", status = "PENDING", requestedAt = 1_000L))
        dao.upsert(row("retryable", status = "FAILED_RETRYABLE", requestedAt = 2_000L))
        dao.upsert(row("final", status = "FAILED_FINAL", requestedAt = 3_000L))
        dao.upsert(row("acked", status = "ACKED", requestedAt = 500L))
        dao.upsert(row("other-phone", policePhoneId = "phone-other", status = "PENDING", requestedAt = 100L))

        val summary = dao.statusSummary(
            incidentId = INCIDENT_ID,
            policePhoneId = POLICE_PHONE_ID
        )

        assertEquals(1, summary.pendingCount)
        assertEquals(1, summary.retryableCount)
        assertEquals(1, summary.finalFailedCount)
        assertEquals(1_000L, summary.oldestPendingClientRequestedAt)
    }

    @Test
    fun emptyStatusSummaryReturnsZeroCounts() = runBlocking {
        val summary = database.outboxDao().statusSummary(
            incidentId = INCIDENT_ID,
            policePhoneId = POLICE_PHONE_ID
        )

        assertEquals(0, summary.pendingCount)
        assertEquals(0, summary.retryableCount)
        assertEquals(0, summary.finalFailedCount)
        assertNull(summary.oldestPendingClientRequestedAt)
    }

    private fun row(
        suffix: String,
        policePhoneId: String = POLICE_PHONE_ID,
        status: String,
        requestedAt: Long
    ): OutboxEntity =
        OutboxEntity(
            outboxId = "outbox-$suffix",
            operationId = "op-$suffix",
            incidentId = INCIDENT_ID,
            opId = "op-precinct-first-001",
            policePhoneId = policePhoneId,
            dependencyGroup = DependencyGroup.PATH.name,
            sequence = requestedAt,
            requestMethod = "POST",
            requestPath = "/api/search-paths/batch",
            payloadJson = "{}",
            requestBodyHash = "sha256:$suffix",
            idempotencyKey = "idem-$suffix",
            idempotencyStatus = status,
            localMirrorStatus = if (status == "ACKED") "ACKED" else "PENDING_SEND",
            attemptCount = 0,
            clientRequestedAt = requestedAt,
            clockOffsetMs = 0,
            clockSyncedAt = requestedAt
        )

    private companion object {
        const val INCIDENT_ID = "inc-precinct-first-001"
        const val POLICE_PHONE_ID = "phone-precinct-001"
    }
}
