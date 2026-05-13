package com.surimap.core.offline

import org.json.JSONException
import org.json.JSONArray
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
                val manifestTiles = manifestTileItems(json.optJSONArray("tileItems"))
                val items = buildList {
                    repeat(packageItems.length()) { index ->
                        val item = packageItems.optJSONObject(index) ?: return@repeat
                        val itemKey = item.optString("itemKey").takeIf(String::isNotBlank) ?: return@repeat
                        val itemType = item.optString("itemType").takeIf(String::isNotBlank) ?: return@repeat
                        val sourceHash = item.optString("sourceHash").takeIf(String::isNotBlank) ?: return@repeat
                        val tile = item.optJSONObject("tile")
                        if (itemType == "TILE" && tile == null) {
                            return@repeat
                        }
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
                    addAll(
                        manifestTiles
                            .filterNot { tile -> any { item -> item.itemKey == tile.itemKey } }
                            .map { tile ->
                                OfflinePackageDownloadItem(
                                    itemKey = tile.itemKey,
                                    itemType = "TILE",
                                    sourceVersion = manifestVersion,
                                    sourceHash = tile.sourceHash,
                                    downloadUrl = tile.downloadUrl,
                                    status = "PENDING",
                                    bytesTotal = tile.bytesTotal
                                )
                            }
                    )
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

private data class ManifestTileDownload(
    val itemKey: String,
    val sourceHash: String,
    val downloadUrl: String,
    val bytesTotal: Long?
)

private fun manifestTileItems(tileItems: JSONArray?): List<ManifestTileDownload> {
    if (tileItems == null) {
        return emptyList()
    }
    return buildList {
        repeat(tileItems.length()) { index ->
            val tile = tileItems.optJSONObject(index) ?: return@repeat
            val styleId = tile.optString("styleId").takeIf(String::isNotBlank) ?: return@repeat
            val z = tile.optIntOrNull("z") ?: return@repeat
            val x = tile.optIntOrNull("x") ?: return@repeat
            val y = tile.optIntOrNull("y") ?: return@repeat
            val checksum = tile.optString("checksum").takeIf(String::isNotBlank) ?: return@repeat
            val key = "tile:$styleId:$z:$x:$y"
            add(
                ManifestTileDownload(
                    itemKey = key,
                    sourceHash = checksum,
                    downloadUrl = "/tiles/$styleId/$z/$x/$y.pbf",
                    bytesTotal = tile.optLongOrNull("bytes")
                )
            )
        }
    }
}

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun String.isCompletePackageItemStatus(): Boolean = this == "DOWNLOADED" || this == "SKIPPED"
