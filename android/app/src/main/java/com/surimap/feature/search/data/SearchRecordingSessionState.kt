package com.surimap.feature.search.data

import com.surimap.feature.search.ui.SearchLifecycleStatus

data class SearchRecordingSessionState(
    val lifecycleOverride: SearchLifecycleStatus? = null,
    val activeLocalSearchPathId: String? = null,
    val activeStartedAtMs: Long? = null,
    val accumulatedElapsedMs: Long = 0L
) {
    fun effectiveSearchPathId(serverActiveSearchPathId: String?): String? =
        activeLocalSearchPathId?.takeIf(String::isNotBlank)
            ?: serverActiveSearchPathId?.takeIf(String::isNotBlank)

    fun displayedLifecycle(
        baseLifecycleStatus: SearchLifecycleStatus,
        serverActiveSearchPathId: String?
    ): SearchLifecycleStatus {
        lifecycleOverride?.let { return it }
        if (baseLifecycleStatus == SearchLifecycleStatus.OpRequired ||
            baseLifecycleStatus == SearchLifecycleStatus.OpTransition
        ) {
            return baseLifecycleStatus
        }
        return if (effectiveSearchPathId(serverActiveSearchPathId) == null) {
            SearchLifecycleStatus.Stopped
        } else {
            baseLifecycleStatus
        }
    }

    fun shouldCollectGps(serverActiveSearchPathId: String?): Boolean =
        lifecycleOverride == SearchLifecycleStatus.Active ||
            (lifecycleOverride == null && effectiveSearchPathId(serverActiveSearchPathId) != null)

    fun elapsedMs(nowMs: Long): Long {
        val activeDelta =
            activeStartedAtMs
                ?.let { startedAt -> (nowMs - startedAt).coerceAtLeast(0L) }
                ?: 0L
        return (accumulatedElapsedMs + activeDelta).coerceAtLeast(0L)
    }

    fun elapsedLabel(nowMs: Long): String = formatSearchElapsed(elapsedMs(nowMs))

    fun start(searchPathId: String?, nowMs: Long): SearchRecordingSessionState =
        copy(
            lifecycleOverride = SearchLifecycleStatus.Active,
            activeLocalSearchPathId = searchPathId?.takeIf(String::isNotBlank),
            activeStartedAtMs = nowMs,
            accumulatedElapsedMs = 0L
        )

    fun ensureActiveStarted(
        searchPathId: String?,
        nowMs: Long,
        serverStartedAtMs: Long?
    ): SearchRecordingSessionState {
        val normalizedPathId = searchPathId?.takeIf(String::isNotBlank)
        val nextStartedAt = serverStartedAtMs ?: nowMs
        return if (
            activeStartedAtMs == null ||
            (normalizedPathId != null && activeLocalSearchPathId != null && activeLocalSearchPathId != normalizedPathId)
        ) {
            copy(
                activeLocalSearchPathId = normalizedPathId ?: activeLocalSearchPathId,
                activeStartedAtMs = nextStartedAt,
                accumulatedElapsedMs = 0L
            )
        } else {
            this
        }
    }

    fun pause(nowMs: Long): SearchRecordingSessionState =
        copy(
            lifecycleOverride = SearchLifecycleStatus.Paused,
            activeStartedAtMs = null,
            accumulatedElapsedMs = elapsedMs(nowMs)
        )

    fun resume(nowMs: Long): SearchRecordingSessionState =
        copy(
            lifecycleOverride = SearchLifecycleStatus.Active,
            activeStartedAtMs = nowMs
        )

    fun stop(nowMs: Long): SearchRecordingSessionState =
        copy(
            lifecycleOverride = SearchLifecycleStatus.Stopped,
            activeLocalSearchPathId = null,
            activeStartedAtMs = null,
            accumulatedElapsedMs = elapsedMs(nowMs)
        )
}

fun formatSearchElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs.coerceAtLeast(0L) / 1_000L).toInt()
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
