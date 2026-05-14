package com.surimap.feature.search.data

import com.surimap.core.location.GpsLocationFix
import com.surimap.core.path.PathPoint
import java.util.UUID

class SearchPathGpsBatchRecorder(
    private val localRecorder: SearchPathLocalRecorder,
    private val pointIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private val pending = mutableListOf<PathPoint>()
    private var pendingPathId: String? = null

    suspend fun recordFix(
        context: SearchPathWriteContext,
        searchPathId: String?,
        fix: GpsLocationFix
    ): SearchPathWriteResult? {
        val pathId = searchPathId?.takeIf(String::isNotBlank) ?: return null
        if (!fix.lon.isFinite() || !fix.lat.isFinite()) {
            return null
        }
        if (pendingPathId != pathId) {
            pending.clear()
            pendingPathId = pathId
        }
        pending +=
            PathPoint(
                pointId = pointIdFactory(),
                lon = fix.lon,
                lat = fix.lat,
                speedMps = fix.speedMps,
                horizontalAccuracyM = fix.horizontalAccuracyM,
                clientTs = fix.capturedAt
            )
        if (!shouldFlush(fix)) {
            return null
        }
        return flush(context, pathId)
    }

    suspend fun flush(
        context: SearchPathWriteContext,
        searchPathId: String?
    ): SearchPathWriteResult? {
        val pathId = searchPathId?.takeIf(String::isNotBlank) ?: return null
        if (pendingPathId != pathId) {
            return null
        }
        if (pending.size < MIN_BATCH_POINTS) {
            return null
        }
        val batch = pending.take(MAX_BATCH_POINTS)
        val result = localRecorder.appendBatch(context, pathId, batch)
        if (result is SearchPathWriteResult.Enqueued) {
            pending.subList(0, batch.size).clear()
            if (pending.isEmpty()) {
                pendingPathId = null
            }
        }
        return result
    }

    fun clear() {
        pending.clear()
        pendingPathId = null
    }

    fun pendingPointCount(): Int = pending.size

    private fun shouldFlush(fix: GpsLocationFix): Boolean {
        if (pending.size >= MAX_BATCH_POINTS) {
            return true
        }
        val first = pending.firstOrNull()?.clientTs ?: return false
        return pending.size >= MIN_BATCH_POINTS &&
            java.time.Duration.between(first, fix.capturedAt).toMillis() >= FLUSH_INTERVAL_MS
    }

    private companion object {
        const val MIN_BATCH_POINTS = 2
        const val MAX_BATCH_POINTS = 120
        const val FLUSH_INTERVAL_MS = 10_000L
    }
}
