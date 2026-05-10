package com.surimap.core.sync

import com.surimap.core.database.OutboxEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutboxRetryDiagnosticsStateTest {

    @Test
    fun classifyFailureCategoriesWithSafePublicFieldsOnly() {
        val base = sampleRow(lastError = "clock_skew_exceeded", status = OutboxStatus.FAILED_RETRYABLE)
        val clock = OutboxDiagnosticsClassifier.classify(base)
        assertEquals("CLOCK_RESYNC_REQUIRED", clock.userSafeFailureCategory)
        assertEquals(true, clock.retryable)

        val network = OutboxDiagnosticsClassifier.classify(base.copy(lastError = "low_connectivity_timeout"))
        assertEquals("RETRYABLE_NETWORK", network.userSafeFailureCategory)
        assertEquals(true, network.retryable)

        val closed = OutboxDiagnosticsClassifier.classify(base.copy(lastError = "post_close_requeue_rejected", idempotencyStatus = OutboxStatus.FAILED_FINAL.name))
        assertEquals("CLOSED_NO_RETRY", closed.userSafeFailureCategory)
        assertEquals(false, closed.retryable)

        val device = OutboxDiagnosticsClassifier.classify(base.copy(lastError = "police_phone_not_assigned", idempotencyStatus = OutboxStatus.FAILED_FINAL.name))
        assertEquals("DEVICE_ACCESS_REQUIRED", device.userSafeFailureCategory)
        assertEquals(false, device.retryable)

        val conflict = OutboxDiagnosticsClassifier.classify(base.copy(lastError = "idempotency_mismatch", idempotencyStatus = OutboxStatus.FAILED_FINAL.name))
        assertEquals("NON_RETRYABLE_CONFLICT", conflict.userSafeFailureCategory)
        assertEquals(false, conflict.retryable)
    }

    @Test
    fun retryableShowsNextAttemptAtAndTerminalMasksIt() {
        val retryable = OutboxDiagnosticsClassifier.classify(sampleRow("network_unavailable", OutboxStatus.FAILED_RETRYABLE))
        assertEquals(true, retryable.retryable)
        assertEquals(2_000L, retryable.nextAttemptAt)

        val terminal = OutboxDiagnosticsClassifier.classify(sampleRow("post_close_requeue_rejected", OutboxStatus.FAILED_FINAL))
        assertEquals(false, terminal.retryable)
        assertNull(terminal.nextAttemptAt)
    }

    private fun sampleRow(lastError: String, status: OutboxStatus): OutboxEntity =
        OutboxEntity(
            outboxId = "outbox-001",
            operationId = "op-001",
            incidentId = "inc-precinct-first-001",
            policePhoneId = "dev-precinct-car-01",
            dependencyGroup = "PATH",
            sequence = 1L,
            requestMethod = "POST",
            requestPath = "/api/search-paths/batch",
            payloadJson = """{"ok":true}""",
            requestBodyHash = "sha256:test",
            idempotencyKey = "idem-001",
            idempotencyStatus = status.name,
            localMirrorStatus = HarnessSyncStatus.FAILED.name,
            attemptCount = 2,
            firstAttemptAt = 1_000L,
            nextAttemptAt = 2_000L,
            clientRequestedAt = 1_500L,
            clockOffsetMs = 0L,
            clockSyncedAt = 1_000L,
            lastError = lastError
        )
}

