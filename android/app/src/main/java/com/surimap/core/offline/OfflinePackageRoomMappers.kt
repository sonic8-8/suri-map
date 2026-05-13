package com.surimap.core.offline

import com.surimap.core.database.OfflinePackageInstallationEntity
import com.surimap.core.database.OfflinePackageItemStatusEntity

fun OfflinePackageItemStatus.toOfflinePackageItemStatusEntity(updatedAt: Long): OfflinePackageItemStatusEntity =
    OfflinePackageItemStatusEntity(
        incidentId = incidentId,
        policePhoneId = policePhoneId,
        manifestId = manifestId,
        itemKey = itemKey,
        manifestVersion = manifestVersion,
        itemType = itemType,
        status = status,
        sourceVersion = sourceVersion,
        sourceHash = sourceHash,
        bytesTotal = bytesTotal,
        bytesDownloaded = bytesDownloaded,
        updatedAt = updatedAt
    )

fun OfflinePackageItemStatusEntity.toOfflinePackageItemStatus(): OfflinePackageItemStatus =
    OfflinePackageItemStatus(
        incidentId = incidentId,
        policePhoneId = policePhoneId,
        manifestId = manifestId,
        manifestVersion = manifestVersion,
        itemKey = itemKey,
        itemType = itemType,
        status = status,
        sourceVersion = sourceVersion,
        sourceHash = sourceHash,
        bytesTotal = bytesTotal,
        bytesDownloaded = bytesDownloaded
    )

fun OfflinePackageInstallationEntity.toOfflinePackageInstallationStatus(): OfflinePackageInstallationStatus =
    OfflinePackageInstallationStatus(
        incidentId = incidentId,
        policePhoneId = policePhoneId,
        manifestId = manifestId,
        manifestVersion = manifestVersion,
        status = status,
        totalItems = totalItems,
        completedItems = completedItems,
        failedItems = failedItems,
        version = version,
        readyForOfflineUse = readyForOfflineUse
    )

fun OfflinePackageInstallationProgressCommand.toOfflinePackageInstallationEntity(
    aggregate: OfflinePackageInstallationAggregate,
    updatedAt: Long
): OfflinePackageInstallationEntity =
    OfflinePackageInstallationEntity(
        incidentId = incidentId,
        policePhoneId = policePhoneId,
        manifestId = manifestId,
        manifestVersion = manifestVersion,
        status = aggregate.status,
        totalItems = aggregate.totalItems,
        completedItems = aggregate.completedItems,
        failedItems = aggregate.failedItems,
        version = version,
        readyForOfflineUse = aggregate.readyForOfflineUse,
        updatedAt = updatedAt
    )
