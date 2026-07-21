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
                speedMps = fix.speedMps,
                horizontalAccuracyM = fix.horizontalAccuracyM,
                clientTs = fix.capturedAt,
                locationProvider = fix.locationProvider,
                elapsedRealtimeNanos = fix.elapsedRealtimeNanos
            )
        if (!shouldFlush()) {
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
        sortPendingByCaptureOrder()
        val batch = withEstimatedSpeeds(pending.take(MAX_BATCH_POINTS))
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

    private fun withEstimatedSpeeds(points: List<PathPoint>): List<PathPoint> =
        points.mapIndexed { index, point ->
            if (point.speedMps != null) {
                point
            } else {
                point.copy(speedMps = estimatedSpeedMps(points.getOrNull(index - 1), point))
            }
        }

    private fun estimatedSpeedMps(previous: PathPoint?, point: PathPoint): Double? {
        if (previous == null) {
            return 0.0
        }
        val elapsedSeconds = elapsedMillis(previous, point) / 1_000.0
        if (elapsedSeconds <= 0.0) {
            return 0.0
        }
        val meters = haversineMeters(previous.lat, previous.lon, point.lat, point.lon)
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

    private fun shouldFlush(): Boolean {
        if (pending.size >= MAX_BATCH_POINTS) {
            return true
        }
        return pending.size >= MIN_BATCH_POINTS && captureSpanMillis() >= FLUSH_INTERVAL_MS
    }

    private fun captureSpanMillis(): Long {
        val elapsedTimes = pending.mapNotNull(PathPoint::elapsedRealtimeNanos)
        if (elapsedTimes.size == pending.size) {
            return ((elapsedTimes.maxOrNull() ?: 0L) - (elapsedTimes.minOrNull() ?: 0L)) / 1_000_000L
        }
        return java.time.Duration.between(pending.first().clientTs, pending.last().clientTs).toMillis()
    }

    private fun sortPendingByCaptureOrder() {
        if (pending.all { it.elapsedRealtimeNanos != null }) {
            pending.sortBy(PathPoint::elapsedRealtimeNanos)
        }
    }

    private fun elapsedMillis(previous: PathPoint, current: PathPoint): Long {
        val previousElapsed = previous.elapsedRealtimeNanos
        val currentElapsed = current.elapsedRealtimeNanos
        return if (previousElapsed != null && currentElapsed != null) {
            (currentElapsed - previousElapsed) / 1_000_000L
        } else {
            java.time.Duration.between(previous.clientTs, current.clientTs).toMillis()
        }
    }

    private companion object {
        const val MIN_BATCH_POINTS = 2
        const val MAX_BATCH_POINTS = 120
        const val FLUSH_INTERVAL_MS = 10_000L
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
