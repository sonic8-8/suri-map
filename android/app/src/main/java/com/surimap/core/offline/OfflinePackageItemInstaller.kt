package com.surimap.core.offline

import java.security.MessageDigest

data class OfflinePackageDownloadItem(
    val itemKey: String,
    val itemType: String,
    val sourceVersion: Int,
    val sourceHash: String,
    val downloadUrl: String?,
    val status: String = "PENDING",
    val bytesTotal: Long? = null
)

data class OfflinePackageItemInstallCommand(
    val incidentId: String,
    val policePhoneId: String,
    val manifestId: String,
    val manifestVersion: Int,
    val items: List<OfflinePackageDownloadItem>
)

class OfflinePackageItemInstaller(
    private val fetchBytes: suspend (OfflinePackageDownloadItem) -> ByteArray,
    private val persistDownloadedBytes: suspend (OfflinePackageDownloadItem, ByteArray) -> Unit = { _, _ -> },
    private val persistItemStatuses: suspend (List<OfflinePackageItemStatus>) -> Unit,
    private val reportInstallationProgress: suspend (List<OfflinePackageItemStatus>) -> Unit
) {
    suspend fun install(command: OfflinePackageItemInstallCommand): List<OfflinePackageItemStatus> {
        val statuses = command.items.map { item -> installItem(command, item) }
        persistItemStatuses(statuses)
        reportInstallationProgress(statuses)
        return statuses
    }

    private suspend fun installItem(
        command: OfflinePackageItemInstallCommand,
        item: OfflinePackageDownloadItem
    ): OfflinePackageItemStatus {
        val url = item.downloadUrl
        if (url.isNullOrBlank()) {
            return item.toStatus(
                command = command,
                status = "SKIPPED",
                bytesTotal = null,
                bytesDownloaded = null
            )
        }
        return runCatching {
            val bytes = fetchBytes(item)
            val checksumMatches = sha256(bytes) == item.sourceHash.lowercase()
            if (checksumMatches) {
                persistDownloadedBytes(item, bytes)
            }
            DownloadedItem(bytesSize = bytes.size, checksumMatches = checksumMatches)
        }
            .fold(
                onSuccess = { result ->
                    item.toStatus(
                        command = command,
                        status = if (result.checksumMatches) "DOWNLOADED" else "FAILED",
                        bytesTotal = result.bytesSize.toLong(),
                        bytesDownloaded = result.bytesSize.toLong()
                    )
                },
                onFailure = {
                    item.toStatus(
                        command = command,
                        status = "FAILED",
                        bytesTotal = null,
                        bytesDownloaded = null
                    )
                }
            )
    }

    private data class DownloadedItem(
        val bytesSize: Int,
        val checksumMatches: Boolean
    )

    private fun OfflinePackageDownloadItem.toStatus(
        command: OfflinePackageItemInstallCommand,
        status: String,
        bytesTotal: Long?,
        bytesDownloaded: Long?
    ): OfflinePackageItemStatus =
        OfflinePackageItemStatus(
            incidentId = command.incidentId,
            policePhoneId = command.policePhoneId,
            manifestId = command.manifestId,
            manifestVersion = command.manifestVersion,
            itemKey = itemKey,
            itemType = itemType,
            status = status,
            sourceVersion = sourceVersion,
            sourceHash = sourceHash,
            bytesTotal = bytesTotal,
            bytesDownloaded = bytesDownloaded
        )
}

private fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return "sha256:" + digest.joinToString("") { byte -> "%02x".format(byte) }
}
