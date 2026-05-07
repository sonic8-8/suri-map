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
    )
}
