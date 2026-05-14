package com.surimap.feature.outbox

import com.surimap.core.database.OutboxEntity
import com.surimap.core.database.OutboxStatusSummary
import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.OutboxStatus
import com.surimap.feature.outbox.data.BlockedOutboxQuery
import com.surimap.feature.outbox.data.BlockedOutboxStateLoader
import com.surimap.feature.outbox.ui.BlockedOutboxReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedOutboxStateLoaderTest {

    @Test
    fun roomRowsMapToBlockedDiagnosticsAndPendingSummary() = runBlocking {
        val loader =
            BlockedOutboxStateLoader(
                rowsByIncident = {
                    listOf(
                        row(
                            operationId = "op-final-closed",
                            status = OutboxStatus.FAILED_FINAL,
                            mirrorStatus = HarnessSyncStatus.FAILED,
                            dependencyGroup = DependencyGroup.PATH.name,
                            lastError = "incident_closed",
                            requestedAt = 1_000L
                        ),
                        row(
                            operationId = "op-retry-network",
                            status = OutboxStatus.FAILED_RETRYABLE,
                            mirrorStatus = HarnessSyncStatus.FAILED,
                            dependencyGroup = DependencyGroup.MARKER.name,
                            lastError = "network_unavailable",
                            requestedAt = 2_000L
                        ),
                        row(
                            operationId = "op-pending",
                            status = OutboxStatus.PENDING,
                            mirrorStatus = HarnessSyncStatus.PENDING_SEND,
                            dependencyGroup = DependencyGroup.PHOTO.name,
                            requestedAt = 3_000L
                        ),
                        row(
                            operationId = "op-other-phone",
                            policePhoneId = "50000000-0000-0000-0000-000000000999",
                            status = OutboxStatus.FAILED_FINAL,
                            mirrorStatus = HarnessSyncStatus.FAILED,
                            dependencyGroup = DependencyGroup.PATH.name,
                            lastError = "incident_closed",
                            requestedAt = 4_000L
                        )
                    )
                },
                statusSummary = { _, _ ->
                    OutboxStatusSummary(
                        pendingCount = 1,
                        retryableCount = 1,
                        finalFailedCount = 1,
                        oldestPendingClientRequestedAt = 1_000L
                    )
                },
                nowMs = { 181_000L }
            )

        val state =
            loader.load(
                BlockedOutboxQuery(
                    incidentId = INCIDENT_ID,
                    policePhoneId = POLICE_PHONE_ID
                )
            )

        assertEquals(2, state.blockedCount)
        assertEquals(listOf("op-final-closed", "op-retry-network"), state.blockedItems.map { it.operationId })
        assertEquals(BlockedOutboxReason.IncidentClosed, state.blockedItems[0].reason)
        assertFalse(state.blockedItems[0].retryable)
        assertEquals(BlockedOutboxReason.RetryableNetwork, state.blockedItems[1].reason)
        assertTrue(state.blockedItems[1].retryable)
        assertTrue(state.visibleText().any { it.contains("지금 재시도") })
        assertEquals("자동 처리 대기 2건", state.pendingSummary!!.title)
        assertTrue(state.pendingSummary.message.contains("3분 전"))
    }

    private fun row(
        operationId: String,
        status: OutboxStatus,
        mirrorStatus: HarnessSyncStatus,
        dependencyGroup: String,
        policePhoneId: String = POLICE_PHONE_ID,
        lastError: String? = null,
        requestedAt: Long
    ): OutboxEntity =
        OutboxEntity(
            outboxId = "outbox-$operationId",
            operationId = operationId,
            incidentId = INCIDENT_ID,
            opId = OP_ID,
            policePhoneId = policePhoneId,
            dependencyGroup = dependencyGroup,
            sequence = requestedAt,
            requestMethod = "POST",
            requestPath = "/api/test",
            payloadJson = "{}",
            requestBodyHash = "sha256:$operationId",
            idempotencyKey = "idem-$operationId",
            idempotencyStatus = status.name,
            localMirrorStatus = mirrorStatus.name,
            attemptCount = 1,
            clientRequestedAt = requestedAt,
            clockOffsetMs = 0L,
            clockSyncedAt = requestedAt,
            lastError = lastError
        )

    private companion object {
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
        const val OP_ID = "88888888-8888-8888-8888-888888880001"
        const val POLICE_PHONE_ID = "50000000-0000-0000-0000-000000000001"
    }
}
