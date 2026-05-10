package com.surimap.core.sync

import com.surimap.core.database.OutboxEntity

data class OutboxDiagnosticsView(
    val operationId: String,
    val outboxStatus: String,
    val retryable: Boolean,
    val userSafeFailureCategory: String,
    val attemptCount: Int,
    val nextAttemptAt: Long?
)

object OutboxDiagnosticsClassifier {
    fun classify(row: OutboxEntity): OutboxDiagnosticsView {
        val (category, retryable) = classifyCategory(row)
        return OutboxDiagnosticsView(
            operationId = row.operationId,
            outboxStatus = row.idempotencyStatus,
            retryable = retryable,
            userSafeFailureCategory = category,
            attemptCount = row.attemptCount,
            nextAttemptAt = if (retryable) row.nextAttemptAt else null
        )
    }

    private fun classifyCategory(row: OutboxEntity): Pair<String, Boolean> {
        val lastError = row.lastError ?: ""
        return when {
            lastError in setOf("clock_skew_exceeded", "clock_skew_exceeded_after_resync") ->
                "CLOCK_RESYNC_REQUIRED" to true

            lastError in setOf(
                "network_unavailable",
                "low_connectivity_timeout",
                "http_408",
                "http_429",
                "http_500",
                "http_502",
                "http_503",
                "http_504"
            ) -> "RETRYABLE_NETWORK" to true

            lastError in setOf("incident_closed", "post_close_requeue_rejected") ->
                "CLOSED_NO_RETRY" to false

            lastError in setOf(
                "police_phone_not_assigned",
                "police_phone_not_registered",
                "device_not_assigned",
                "device_not_registered",
                "device_required"
            ) -> "DEVICE_ACCESS_REQUIRED" to false

            lastError in setOf(
                "idempotency_mismatch",
                "request_body_hash_mismatch",
                "invalid_payload",
                "write_conflict"
            ) -> "NON_RETRYABLE_CONFLICT" to false

            row.idempotencyStatus == OutboxStatus.FAILED_RETRYABLE.name ->
                "RETRYABLE_NETWORK" to true

            else -> "NON_RETRYABLE_CONFLICT" to false
        }
    }
}

