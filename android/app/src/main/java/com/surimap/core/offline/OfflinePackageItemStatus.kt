package com.surimap.core.offline

data class OfflinePackageItemStatus(
    val incidentId: String,
    val policePhoneId: String,
    val manifestId: String,
    val manifestVersion: Int,
    val itemKey: String,
    val itemType: String,
    val status: String,
    val sourceVersion: Int,
    val sourceHash: String,
    val bytesTotal: Long?,
    val bytesDownloaded: Long?
)
