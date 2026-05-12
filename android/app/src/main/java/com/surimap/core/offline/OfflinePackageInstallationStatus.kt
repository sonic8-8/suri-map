package com.surimap.core.offline

data class OfflinePackageInstallationStatus(
    val incidentId: String,
    val policePhoneId: String,
    val manifestId: String,
    val manifestVersion: Int,
    val status: String,
    val totalItems: Int,
    val completedItems: Int,
    val failedItems: Int,
    val version: Long,
    val readyForOfflineUse: Boolean
)
