package com.surimap.core.sync

enum class LocalWarningCode {
    BATTERY_LOW,
    OFFLINE_RECORDING,
    PACKAGE_MISSING,
    OUTBOX_BACKLOG
}

data class PackageAvailability(
    val status: Status,
    val manifestVersion: String? = null,
    val activeManifestVersion: String? = null,
    val failedRequiredItemKeys: Set<String> = emptySet()
) {
    enum class Status {
        COMPLETE,
        MISSING,
        STALE,
        EXPIRED
    }
}

object PackageAvailabilityInputAdapter {
    fun fromS7Status(
        status: String,
        manifestVersion: String?,
        activeManifestVersion: String?,
        failedRequiredItemKeys: Set<String> = emptySet()
    ): PackageAvailability = PackageAvailability(
        status = status.toPackageAvailabilityStatus(),
        manifestVersion = manifestVersion,
        activeManifestVersion = activeManifestVersion,
        failedRequiredItemKeys = failedRequiredItemKeys
    )

    private fun String.toPackageAvailabilityStatus(): PackageAvailability.Status = when (uppercase()) {
        "READY", "COMPLETE" -> PackageAvailability.Status.COMPLETE
        "STALE" -> PackageAvailability.Status.STALE
        "EXPIRED" -> PackageAvailability.Status.EXPIRED
        else -> PackageAvailability.Status.MISSING
    }
}

data class LocalWarningSignals(
    val nowMs: Long,
    val batteryPercent: Int,
    val batteryCharging: Boolean,
    val packageAvailability: PackageAvailability,
    val offlineRecordingStartedAtMs: Long?,
    val lastSuccessfulSyncAtMs: Long?,
    val networkConnected: Boolean,
    val pendingOutboxCount: Int = 0
)

data class LocalWarningSnapshot(val activeWarnings: Set<LocalWarningCode>)

fun interface LocalWarningServerRoundTrip {
    fun probe(): Boolean
}

class LocalWarningMonitor(@Suppress("unused") private val serverRoundTrip: LocalWarningServerRoundTrip? = null) {
    private var batteryLowActive = false
    private var outboxBacklogActive = false

    fun evaluate(signals: LocalWarningSignals): LocalWarningSnapshot {
        val activeWarnings = buildSet {
            if (isBatteryLow(signals)) {
                add(LocalWarningCode.BATTERY_LOW)
            }
            if (isPackageMissing(signals.packageAvailability)) {
                add(LocalWarningCode.PACKAGE_MISSING)
            }
            if (isOfflineRecording(signals)) {
                add(LocalWarningCode.OFFLINE_RECORDING)
            }
            if (isOutboxBacklog(signals)) {
                add(LocalWarningCode.OUTBOX_BACKLOG)
            }
        }

        return LocalWarningSnapshot(activeWarnings = activeWarnings)
    }

    private fun isBatteryLow(signals: LocalWarningSignals): Boolean {
        batteryLowActive = when {
            signals.batteryCharging -> false
            signals.batteryPercent >= BATTERY_CLEAR_AT_PERCENT -> false
            signals.batteryPercent < BATTERY_RAISE_BELOW_PERCENT -> true
            else -> batteryLowActive
        }
        return batteryLowActive
    }

    private fun isPackageMissing(packageAvailability: PackageAvailability): Boolean {
        if (packageAvailability.failedRequiredItemKeys.isNotEmpty()) {
            return true
        }
        if (packageAvailability.status in PACKAGE_WARNING_STATUSES) {
            return true
        }
        return packageAvailability.status == PackageAvailability.Status.COMPLETE &&
            packageAvailability.manifestVersion != null &&
            packageAvailability.activeManifestVersion != null &&
            packageAvailability.manifestVersion != packageAvailability.activeManifestVersion
    }

    private fun isOfflineRecording(signals: LocalWarningSignals): Boolean {
        val offlineSince = signals.offlineRecordingStartedAtMs ?: return false
        if ((signals.lastSuccessfulSyncAtMs ?: Long.MIN_VALUE) >= offlineSince) {
            return false
        }
        return signals.nowMs - offlineSince >= OFFLINE_RECORDING_RAISE_AFTER_MS
    }

    private fun isOutboxBacklog(signals: LocalWarningSignals): Boolean {
        outboxBacklogActive = when {
            signals.pendingOutboxCount > OUTBOX_BACKLOG_RAISE_ABOVE_COUNT -> true
            signals.pendingOutboxCount <= OUTBOX_BACKLOG_CLEAR_AT_OR_BELOW_COUNT -> false
            else -> outboxBacklogActive
        }
        return outboxBacklogActive
    }

    private companion object {
        const val BATTERY_RAISE_BELOW_PERCENT = 20
        const val BATTERY_CLEAR_AT_PERCENT = 25
        const val OFFLINE_RECORDING_RAISE_AFTER_MS = 60_000L
        const val OUTBOX_BACKLOG_RAISE_ABOVE_COUNT = 10
        const val OUTBOX_BACKLOG_CLEAR_AT_OR_BELOW_COUNT = 3
        val PACKAGE_WARNING_STATUSES = setOf(
            PackageAvailability.Status.MISSING,
            PackageAvailability.Status.STALE,
            PackageAvailability.Status.EXPIRED
        )
    }
}

data class LocalWarningUiState(val banners: List<LocalWarningBanner>) {
    companion object {
        val Empty = LocalWarningUiState(emptyList())

        fun from(snapshot: LocalWarningSnapshot): LocalWarningUiState = LocalWarningUiState(
            banners = LocalWarningCode.entries
                .filter { it in snapshot.activeWarnings }
                .map { it.toBanner() }
        )
    }
}

data class LocalWarningBanner(val code: LocalWarningCode, val title: String, val message: String)

private fun LocalWarningCode.toBanner(): LocalWarningBanner = when (this) {
    LocalWarningCode.BATTERY_LOW -> LocalWarningBanner(
        code = this,
        title = "배터리 부족",
        message = "배터리가 낮습니다. 현장 기록 유지를 위해 충전을 준비하세요."
    )

    LocalWarningCode.OFFLINE_RECORDING -> LocalWarningBanner(
        code = this,
        title = "오프라인 기록 중",
        message = "서버 연결 없이 로컬에 기록 중입니다. 연결 복구 후 자동 동기화됩니다."
    )

    LocalWarningCode.PACKAGE_MISSING -> LocalWarningBanner(
        code = this,
        title = "지도 패키지 확인 필요",
        message = "오프라인 지도 패키지가 준비되지 않았거나 최신 상태가 아닙니다."
    )

    LocalWarningCode.OUTBOX_BACKLOG -> LocalWarningBanner(
        code = this,
        title = "미전송 기록 적체",
        message = "로컬 미전송 기록이 많습니다. 연결 상태를 유지하고 동기화를 기다리세요."
    )
}
