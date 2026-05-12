package com.surimap.core.offline

import org.json.JSONException
import org.json.JSONObject

data class OfflinePackageDownloadPlan(
    val manifestId: String,
    val manifestVersion: Int,
    val items: List<OfflinePackageDownloadItem>
) {
    fun initialItemStatuses(
        incidentId: String,
        policePhoneId: String
    ): List<OfflinePackageItemStatus> {
        return items.map { item ->
            val completedBytes = if (item.status.isCompletePackageItemStatus()) item.bytesTotal else null
            OfflinePackageItemStatus(
                incidentId = incidentId,
                policePhoneId = policePhoneId,
                manifestId = manifestId,
                manifestVersion = manifestVersion,
                itemKey = item.itemKey,
                itemType = item.itemType,
                status = item.status,
                sourceVersion = item.sourceVersion,
                sourceHash = item.sourceHash,
                bytesTotal = item.bytesTotal,
                bytesDownloaded = completedBytes
            )
        }
    }

    fun installCommand(
        incidentId: String,
        policePhoneId: String
    ): OfflinePackageItemInstallCommand =
        OfflinePackageItemInstallCommand(
            incidentId = incidentId,
            policePhoneId = policePhoneId,
            manifestId = manifestId,
            manifestVersion = manifestVersion,
            items = items
        )

    companion object {
        fun fromManifestJson(body: String?): OfflinePackageDownloadPlan? {
            if (body.isNullOrBlank()) {
                return null
            }
            return try {
                val json = JSONObject(body)
                val manifestId = json.optString("manifestId").takeIf(String::isNotBlank) ?: return null
                val manifestVersion = json.optInt("manifestVersion", 0)
                val packageItems = json.optJSONArray("packageItems") ?: return null
                val items = buildList {
                    repeat(packageItems.length()) { index ->
                        val item = packageItems.optJSONObject(index) ?: return@repeat
                        val itemKey = item.optString("itemKey").takeIf(String::isNotBlank) ?: return@repeat
                        val itemType = item.optString("itemType").takeIf(String::isNotBlank) ?: return@repeat
                        val sourceHash = item.optString("sourceHash").takeIf(String::isNotBlank) ?: return@repeat
                        val tile = item.optJSONObject("tile")
                        add(
                            OfflinePackageDownloadItem(
                                itemKey = itemKey,
                                itemType = itemType,
                                sourceVersion = item.optInt("sourceVersion", 0),
                                sourceHash = sourceHash,
                                downloadUrl = tile?.optString("url")?.takeIf(String::isNotBlank),
                                status = item.optString("status").takeIf(String::isNotBlank) ?: "PENDING",
                                bytesTotal = tile?.optLongOrNull("bytes")
                            )
                        )
                    }
                }
                OfflinePackageDownloadPlan(
                    manifestId = manifestId,
                    manifestVersion = manifestVersion,
                    items = items
                )
            } catch (_: JSONException) {
                null
            }
        }
    }
}

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun String.isCompletePackageItemStatus(): Boolean = this == "DOWNLOADED" || this == "SKIPPED"
