package com.surimap.core.sync

fun interface SyncClient {
    suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult
}

data class OutboxReplayResult(
    val attemptedCount: Int = 0,
    val ackedCount: Int = 0,
    val retryableFailureCount: Int = 0,
    val finalFailureCount: Int = 0
) {
    val shouldRetry: Boolean
        get() = retryableFailureCount > 0
}

interface OutboxReplay {
    suspend fun flushPending(policePhoneId: String, incidentId: String): OutboxReplayResult
}

interface OutboxRequeue {
    suspend fun requeue(operationId: String, reason: String)
}

interface LocalSyncPurgeHook {
    suspend fun purgeIncidentLocalSync(
        incidentId: String,
        purgeRunId: String,
        closedAt: String,
        purgeDeadlineTs: String
    ): LocalSyncPurgeResult

    suspend fun handleIncidentClosed(
        incidentId: String,
        policePhoneId: String,
        closedAt: String,
        purgeRunId: String
    ): LocalSyncPurgeResult
}

data class LocalSyncPurgeResult(
    val status: String,
    val purgedCount: Int,
    val retainedCount: Int,
    val retainedRows: List<LocalSyncRetainedRow>,
    val orderedCleanupSteps: List<LocalSyncCleanupStep>,
    val errorCode: String? = null
)

data class LocalSyncRetainedRow(
    val outboxId: String,
    val operationId: String,
    val incidentId: String?,
    val idempotencyStatus: String,
    val localPurgeState: String,
    val retentionAccountingState: String
)

data class LocalSyncCleanupStep(
    val code: String
)
