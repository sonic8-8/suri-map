package com.surimap.feature.search.data

import com.surimap.core.location.GpsLocationFix
import com.surimap.core.path.PathPoint
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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
                speedMps = fix.speedMps ?: estimatedSpeedMps(pending.lastOrNull(), fix),
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

    suspend fun flushAll(
        context: SearchPathWriteContext,
        searchPathId: String?
    ): List<SearchPathWriteResult> {
        val results = mutableListOf<SearchPathWriteResult>()
        while (true) {
            val result = flush(context, searchPathId) ?: break
            results += result
            if (result !is SearchPathWriteResult.Enqueued) {
                break
            }
        }
        return results
    }

    fun clear() {
        pending.clear()
        pendingPathId = null
    }

    fun pendingPointCount(): Int = pending.size

    private fun estimatedSpeedMps(previous: PathPoint?, fix: GpsLocationFix): Double? {
        if (previous == null) {
            return 0.0
        }
        val elapsedSeconds =
            java.time.Duration.between(previous.clientTs, fix.capturedAt).toMillis() / 1_000.0
        if (elapsedSeconds <= 0.0) {
            return 0.0
        }
        val meters = haversineMeters(previous.lat, previous.lon, fix.lat, fix.lon)
        val speed = meters / elapsedSeconds
        return speed.takeIf { it.isFinite() && it >= 0.0 }
    }

    private fun haversineMeters(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val fromLatRad = Math.toRadians(fromLat)
        val toLatRad = Math.toRadians(toLat)
        val deltaLat = Math.toRadians(toLat - fromLat)
        val deltaLon = Math.toRadians(toLon - fromLon)
        val a = sin(deltaLat / 2).pow(2) +
            cos(fromLatRad) * cos(toLatRad) * sin(deltaLon / 2).pow(2)
        return EARTH_RADIUS_METERS * 2 * asin(sqrt(a))
    }

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
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
