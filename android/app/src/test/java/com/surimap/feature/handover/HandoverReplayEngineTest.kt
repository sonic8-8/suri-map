package com.surimap.feature.handover

import com.surimap.feature.handover.domain.HandoverReplayCameraMode
import com.surimap.feature.handover.domain.HandoverReplayEngine
import com.surimap.feature.handover.domain.HandoverReplayPlaybackState
import com.surimap.feature.handover.domain.HandoverReplayPoint
import com.surimap.feature.handover.domain.HandoverReplaySpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandoverReplayEngineTest {

    @Test
    fun interpolatesSingleActorPositionBetweenAdjacentGpsPoints() {
        val engine =
            HandoverReplayEngine(
                points =
                listOf(
                    HandoverReplayPoint(sourceElapsedMs = 0L, lat = 35.0, lon = 126.0),
                    HandoverReplayPoint(sourceElapsedMs = 10_000L, lat = 35.01, lon = 126.02)
                )
            )

        val snapshot = engine.snapshotAt(displayElapsedMs = 5_000L)

        assertEquals(5_000L, snapshot.sourceElapsedMs)
        assertEquals(5_000L, snapshot.displayElapsedMs)
        assertEquals(35.005, snapshot.position!!.lat, 0.000001)
        assertEquals(126.01, snapshot.position!!.lon, 0.000001)
    }

    @Test
    fun compressesLongEmptyGpsGapsOnDisplayTimeline() {
        val engine =
            HandoverReplayEngine(
                points =
                listOf(
                    HandoverReplayPoint(sourceElapsedMs = 0L, lat = 35.0, lon = 126.0),
                    HandoverReplayPoint(sourceElapsedMs = 60_000L, lat = 35.01, lon = 126.0),
                    HandoverReplayPoint(sourceElapsedMs = 1_860_000L, lat = 35.31, lon = 126.0)
                ),
                gapCompressionThresholdMs = 300_000L,
                compressedGapMs = 60_000L
            )

        val snapshot = engine.snapshotAt(displayElapsedMs = 90_000L)

        assertEquals(120_000L, engine.displayDurationMs)
        assertEquals(960_000L, snapshot.sourceElapsedMs)
        assertEquals(90_000L, snapshot.displayElapsedMs)
        assertEquals(35.16, snapshot.position!!.lat, 0.000001)
    }

    @Test
    fun advancesPlayheadBySpeedAndKeepsCameraModeState() {
        val engine =
            HandoverReplayEngine(
                points =
                listOf(
                    HandoverReplayPoint(sourceElapsedMs = 0L, lat = 35.0, lon = 126.0),
                    HandoverReplayPoint(sourceElapsedMs = 120_000L, lat = 35.12, lon = 126.0)
                )
            )

        val state =
            HandoverReplayPlaybackState()
                .withSpeed(HandoverReplaySpeed.X16)
                .withCameraMode(HandoverReplayCameraMode.FollowPlayhead)
                .advanceBy(realElapsedMs = 1_000L, engine = engine)

        assertEquals(16_000L, state.displayPlayheadMs)
        assertEquals(HandoverReplaySpeed.X16, state.speed)
        assertEquals(HandoverReplayCameraMode.FollowPlayhead, state.cameraMode)
        assertEquals(16_000L, state.snapshot(engine).sourceElapsedMs)
        assertEquals(35.016, state.snapshot(engine).position!!.lat, 0.000001)
    }

    @Test
    fun emptyPathProducesStableZeroDurationSnapshot() {
        val engine = HandoverReplayEngine(points = emptyList())
        val snapshot = engine.snapshotAt(displayElapsedMs = 10_000L)

        assertEquals(0L, engine.displayDurationMs)
        assertEquals(0L, snapshot.sourceElapsedMs)
        assertEquals(0L, snapshot.displayElapsedMs)
        assertNull(snapshot.position)
    }
}
