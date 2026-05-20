package com.surimap.feature.handover.domain

import kotlin.math.roundToLong

data class HandoverReplayPoint(
    val sourceElapsedMs: Long,
    val lat: Double,
    val lon: Double
)

data class HandoverReplayPosition(
    val lat: Double,
    val lon: Double
)

data class HandoverReplaySnapshot(
    val displayElapsedMs: Long,
    val sourceElapsedMs: Long,
    val position: HandoverReplayPosition?
)

enum class HandoverReplaySpeed(val multiplier: Int) {
    X1(1),
    X4(4),
    X16(16),
    X60(60)
}

data class HandoverReplayPlaybackState(
    val displayPlayheadMs: Long = 0L,
    val speed: HandoverReplaySpeed = HandoverReplaySpeed.X1
) {
    fun withSpeed(speed: HandoverReplaySpeed): HandoverReplayPlaybackState =
        copy(speed = speed)

    fun advanceBy(realElapsedMs: Long, engine: HandoverReplayEngine): HandoverReplayPlaybackState {
        val scaledDeltaMs = realElapsedMs.coerceAtLeast(0L) * speed.multiplier
        return copy(displayPlayheadMs = (displayPlayheadMs + scaledDeltaMs).coerceIn(0L, engine.displayDurationMs))
    }

    fun snapshot(engine: HandoverReplayEngine): HandoverReplaySnapshot =
        engine.snapshotAt(displayPlayheadMs)
}

class HandoverReplayEngine(
    points: List<HandoverReplayPoint>,
    gapCompressionThresholdMs: Long = DefaultGapCompressionThresholdMs,
    compressedGapMs: Long = DefaultCompressedGapMs
) {
    private val sourcePoints: List<HandoverReplayPoint> =
        points
            .filter { it.sourceElapsedMs >= 0L && it.lat.isFinite() && it.lon.isFinite() }
            .sortedBy(HandoverReplayPoint::sourceElapsedMs)
            .distinctBy(HandoverReplayPoint::sourceElapsedMs)

    private val compressionThresholdMs = gapCompressionThresholdMs.coerceAtLeast(1L)
    private val compressedGapDurationMs = compressedGapMs.coerceAtLeast(1L)
    private val timeline: List<TimelinePoint> = buildTimeline()

    val displayDurationMs: Long =
        timeline.lastOrNull()?.displayElapsedMs ?: 0L

    fun snapshotAt(displayElapsedMs: Long): HandoverReplaySnapshot {
        if (timeline.isEmpty()) {
            return HandoverReplaySnapshot(displayElapsedMs = 0L, sourceElapsedMs = 0L, position = null)
        }
        val clampedDisplayElapsedMs = displayElapsedMs.coerceIn(0L, displayDurationMs)
        val sourceElapsedMs = sourceElapsedAt(clampedDisplayElapsedMs)
        return HandoverReplaySnapshot(
            displayElapsedMs = clampedDisplayElapsedMs,
            sourceElapsedMs = sourceElapsedMs,
            position = positionAt(sourceElapsedMs)
        )
    }

    private fun buildTimeline(): List<TimelinePoint> {
        val first = sourcePoints.firstOrNull() ?: return emptyList()
        var displayElapsedMs = 0L
        return buildList {
            add(TimelinePoint(sourceElapsedMs = first.sourceElapsedMs, displayElapsedMs = displayElapsedMs))
            sourcePoints.zipWithNext { previous, current ->
                val sourceDeltaMs = current.sourceElapsedMs - previous.sourceElapsedMs
                val displayDeltaMs =
                    if (sourceDeltaMs > compressionThresholdMs) {
                        compressedGapDurationMs
                    } else {
                        sourceDeltaMs
                    }
                displayElapsedMs += displayDeltaMs.coerceAtLeast(0L)
                add(TimelinePoint(sourceElapsedMs = current.sourceElapsedMs, displayElapsedMs = displayElapsedMs))
            }
        }
    }

    private fun sourceElapsedAt(displayElapsedMs: Long): Long {
        val first = timeline.first()
        if (displayElapsedMs <= first.displayElapsedMs) {
            return first.sourceElapsedMs
        }
        val last = timeline.last()
        if (displayElapsedMs >= last.displayElapsedMs) {
            return last.sourceElapsedMs
        }
        val nextIndex = timeline.indexOfFirst { it.displayElapsedMs >= displayElapsedMs }
        val previous = timeline[nextIndex - 1]
        val next = timeline[nextIndex]
        val displayDeltaMs = next.displayElapsedMs - previous.displayElapsedMs
        if (displayDeltaMs <= 0L) {
            return next.sourceElapsedMs
        }
        val progress = (displayElapsedMs - previous.displayElapsedMs).toDouble() / displayDeltaMs.toDouble()
        val sourceDeltaMs = next.sourceElapsedMs - previous.sourceElapsedMs
        return previous.sourceElapsedMs + (sourceDeltaMs * progress).roundToLong()
    }

    private fun positionAt(sourceElapsedMs: Long): HandoverReplayPosition? {
        val first = sourcePoints.firstOrNull() ?: return null
        if (sourceElapsedMs <= first.sourceElapsedMs) {
            return first.toPosition()
        }
        val last = sourcePoints.last()
        if (sourceElapsedMs >= last.sourceElapsedMs) {
            return last.toPosition()
        }
        val nextIndex = sourcePoints.indexOfFirst { it.sourceElapsedMs >= sourceElapsedMs }
        val previous = sourcePoints[nextIndex - 1]
        val next = sourcePoints[nextIndex]
        val sourceDeltaMs = next.sourceElapsedMs - previous.sourceElapsedMs
        if (sourceDeltaMs <= 0L) {
            return next.toPosition()
        }
        val progress = (sourceElapsedMs - previous.sourceElapsedMs).toDouble() / sourceDeltaMs.toDouble()
        return HandoverReplayPosition(
            lat = previous.lat + ((next.lat - previous.lat) * progress),
            lon = previous.lon + ((next.lon - previous.lon) * progress)
        )
    }

    private fun HandoverReplayPoint.toPosition(): HandoverReplayPosition =
        HandoverReplayPosition(lat = lat, lon = lon)

    private data class TimelinePoint(
        val sourceElapsedMs: Long,
        val displayElapsedMs: Long
    )

    private companion object {
        const val DefaultGapCompressionThresholdMs = 10 * 60 * 1_000L
        const val DefaultCompressedGapMs = 60 * 1_000L
    }
}
