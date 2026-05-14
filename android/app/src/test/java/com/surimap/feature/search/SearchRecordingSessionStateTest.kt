package com.surimap.feature.search

import com.surimap.feature.search.data.SearchRecordingSessionState
import com.surimap.feature.search.data.formatSearchElapsed
import com.surimap.feature.search.ui.SearchLifecycleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRecordingSessionStateTest {

    @Test
    fun currentOpWithoutActivePathIsDisplayedAsStopped() {
        val session = SearchRecordingSessionState()

        assertEquals(
            SearchLifecycleStatus.Stopped,
            session.displayedLifecycle(
                baseLifecycleStatus = SearchLifecycleStatus.Active,
                serverActiveSearchPathId = null
            )
        )
        assertFalse(session.shouldCollectGps(serverActiveSearchPathId = null))
    }

    @Test
    fun startPauseResumeAndStopKeepElapsedTimeDeterministic() {
        val started = SearchRecordingSessionState().start("path-1", nowMs = 1_000L)

        assertEquals(SearchLifecycleStatus.Active, started.displayedLifecycle(SearchLifecycleStatus.Stopped, null))
        assertEquals("00:03", started.elapsedLabel(nowMs = 4_000L))
        assertTrue(started.shouldCollectGps(serverActiveSearchPathId = null))

        val paused = started.pause(nowMs = 4_000L)
        assertEquals(SearchLifecycleStatus.Paused, paused.displayedLifecycle(SearchLifecycleStatus.Active, null))
        assertEquals("00:03", paused.elapsedLabel(nowMs = 9_000L))
        assertFalse(paused.shouldCollectGps(serverActiveSearchPathId = null))

        val resumed = paused.resume(nowMs = 9_000L)
        assertEquals(SearchLifecycleStatus.Active, resumed.displayedLifecycle(SearchLifecycleStatus.Paused, null))
        assertEquals("00:05", resumed.elapsedLabel(nowMs = 11_000L))

        val stopped = resumed.stop(nowMs = 11_000L)
        assertEquals(SearchLifecycleStatus.Stopped, stopped.displayedLifecycle(SearchLifecycleStatus.Active, null))
        assertEquals("00:05", stopped.elapsedLabel(nowMs = 20_000L))
        assertEquals(null, stopped.effectiveSearchPathId(serverActiveSearchPathId = null))
    }

    @Test
    fun elapsedFormatterSupportsHourBoundary() {
        assertEquals("00:00", formatSearchElapsed(0L))
        assertEquals("01:01", formatSearchElapsed(61_000L))
        assertEquals("01:02:03", formatSearchElapsed(3_723_000L))
    }
}
