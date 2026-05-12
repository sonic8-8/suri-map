package com.surimap.core.offline

import com.surimap.core.sync.EnqueueResult
import java.time.Instant

data class OfflinePackageInstallationProgressCommand(
    val operationId: String,
    val incidentId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val manifestId: String,
    val manifestVersion: Int,
    val version: Long,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null,
    val items: List<OfflinePackageItemStatus>
)

data class OfflinePackageInstallationAggregate(
    val status: String,
    val totalItems: Int,
    val completedItems: Int,
    val failedItems: Int,
    val failedItemKeys: List<String>,
    val readyForOfflineUse: Boolean
)

class OfflinePackageInstallationProgressReporter(
    private val repository: OfflinePackageRepository
) {
    suspend fun report(command: OfflinePackageInstallationProgressCommand): EnqueueResult {
        val aggregate = aggregate(command.items)
        return repository.reportInstallation(
            OfflinePackageInstallationCommand(
                operationId = command.operationId,
                incidentId = command.incidentId,
                policePhoneId = command.policePhoneId,
                idempotencyKey = command.idempotencyKey,
                sequence = command.sequence,
                manifestId = command.manifestId,
                manifestVersion = command.manifestVersion,
                status = aggregate.status,
                totalItems = aggregate.totalItems,
                completedItems = aggregate.completedItems,
                failedItems = aggregate.failedItems,
                version = command.version,
                readyForOfflineUse = aggregate.readyForOfflineUse,
                failedItemKeys = aggregate.failedItemKeys,
                clientTs = command.clientTs,
                clockOffsetMs = command.clockOffsetMs,
                clockSyncedAt = command.clockSyncedAt
            )
        )
    }

    companion object {
        fun aggregate(items: List<OfflinePackageItemStatus>): OfflinePackageInstallationAggregate {
            val totalItems = items.size
            val completedItems = items.count { item -> item.status.isCompletePackageItemStatus() }
            val failedItemKeys = items
                .filter { item -> item.status == "FAILED" }
                .map(OfflinePackageItemStatus::itemKey)
            val failedItems = failedItemKeys.size
            val status =
                when {
                    totalItems == 0 -> "NOT_STARTED"
                    completedItems == totalItems -> "READY"
                    failedItems == totalItems -> "FAILED"
                    failedItems > 0 || completedItems > 0 -> "PARTIAL"
                    items.any(OfflinePackageItemStatus::hasByteProgress) -> "DOWNLOADING"
                    else -> "NOT_STARTED"
                }

            return OfflinePackageInstallationAggregate(
                status = status,
                totalItems = totalItems,
                completedItems = completedItems,
                failedItems = failedItems,
                failedItemKeys = failedItemKeys,
                readyForOfflineUse = status == "READY"
            )
        }
    }
}

private fun String.isCompletePackageItemStatus(): Boolean = this == "DOWNLOADED" || this == "SKIPPED"

private fun OfflinePackageItemStatus.hasByteProgress(): Boolean =
    bytesTotal != null && bytesTotal > 0L && (bytesDownloaded ?: 0L) > 0L
