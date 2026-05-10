package com.surimap.core.sync

interface SyncClient {
    suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult
}

interface OutboxReplay {
    suspend fun flushPending(policePhoneId: String, incidentId: String)
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
