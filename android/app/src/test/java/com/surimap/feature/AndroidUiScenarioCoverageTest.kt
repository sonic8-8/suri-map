package com.surimap.feature

import com.surimap.feature.alert.ui.IncidentAlertType
import com.surimap.feature.alert.ui.IncidentAlertUiState
import com.surimap.feature.alert.ui.IncidentFcmPayload
import com.surimap.feature.alert.ui.IncidentFcmRoute
import com.surimap.feature.alert.ui.IncidentFcmRouteMapper
import com.surimap.feature.handover.ui.DutyHandoverUiState
import com.surimap.feature.handover.ui.HandoverMemoTarget
import com.surimap.feature.handover.ui.HandoverMemoUiState
import com.surimap.feature.marker.ui.MarkerCreateSheetUiState
import com.surimap.feature.marker.ui.MarkerType
import com.surimap.feature.offline.ui.OfflinePackageUiState
import com.surimap.feature.search.ui.SearchMapUiState
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.markerIdFixture
import com.surimap.ui.AppOverlayState
import com.surimap.ui.IncidentClosedOverlayState
import com.surimap.ui.SearchPathEndedToastState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUiScenarioCoverageTest {
    @Test
    fun androidUiStatesCloseFirstStepForHarnessScenarios() {
        val packageState = OfflinePackageUiState.downloading(
            incidentTitle = "광주 북구 산악 실종",
            manifestRevision = 18,
            completedItems = 4
        )
        assertFalse(packageState.readyForOfflineUse)
        assertTrue(packageState.visibleText().containsAll(listOf("사건 메타", "실종자", "OP", "전체 수색 구역", "타일")))

        val activeMap = SearchMapUiState.active()
        assertTrue(activeMap.canWritePath)
        assertTrue(activeMap.canCreateMarker)
        assertTrue(activeMap.visibleText().contains("GPS 5초 수집 · 서버 전송 10초 batch 기준"))

        val offlineMap = SearchMapUiState.offline(unsentCount = 4, oldestPendingMinutes = 15)
        assertEquals("미전송 4 · 15분", offlineMap.syncLabel)
        assertFalse(offlineMap.shouldOpenBlockedOutbox)

        val replayingMap = SearchMapUiState.sending(unsentCount = 2)
        assertEquals("전송 중 · 2", replayingMap.syncLabel)

        val markerSheet = MarkerCreateSheetUiState.default(selectedType = MarkerType.SUPPORT_REQUEST)
        assertTrue(markerSheet.requiresSupportRequestType)
        assertFalse(markerSheet.opensBlockedOutbox)
        assertEquals(
            listOf("CLUE", "PERSON_FOUND", "FIELD_CONDITION", "SUPPORT_REQUEST", "NOTE"),
            MarkerType.entries.map { it.apiValue }
        )

        val alertRoute = IncidentFcmRouteMapper.route(
            IncidentFcmPayload(
                eventId = "evt-s5-person-found-001",
                type = "PERSON_FOUND",
                incidentId = INCIDENT_ID,
                markerId = PERSON_FOUND_MARKER_ID
            )
        )
        assertTrue(alertRoute is IncidentFcmRoute.MarkerFocus)
        val alert = (alertRoute as IncidentFcmRoute.MarkerFocus).alert
        assertEquals(IncidentAlertType.PERSON_FOUND, alert.type)
        assertTrue(alert.visibleText().contains("지도 열기"))

        val memoState = HandoverMemoUiState.default()
        assertEquals(HandoverMemoTarget.entries.toList(), memoState.targetOptions)
        assertTrue(memoState.canSave)

        val handoverSummary = DutyHandoverUiState.unavailable()
        assertFalse(handoverSummary.canRequestSummaryGeneration)
        assertTrue(handoverSummary.visibleText().contains("summary_unavailable"))
        assertTrue(handoverSummary.visibleText().contains("원본 확인"))

        val terminalOverlay = AppOverlayState(incidentClosed = IncidentClosedOverlayState(hasDraft = true))
        assertTrue(terminalOverlay.incidentClosed!!.hasDraft)

        val searchPathEndedOverlay = AppOverlayState(searchPathEnded = SearchPathEndedToastState(pendingSync = true))
        assertTrue(searchPathEndedOverlay.searchPathEnded!!.pendingSync)
    }

    @Test
    fun incidentClosedFcmRoutesToTerminalOverlayInsteadOfAlertBanner() {
        val route = IncidentFcmRouteMapper.route(
            IncidentFcmPayload(
                eventId = "evt-s1-1-incident-closed-001",
                type = "INCIDENT_CLOSED",
                incidentId = INCIDENT_ID,
                markerId = null
            )
        )

        assertTrue(route is IncidentFcmRoute.IncidentClosed)
        assertFalse(route is IncidentFcmRoute.Alert)
    }

    @Test
    fun alertUiKeepsMarkerFocusActionForSupportRequestAndPersonFound() {
        val alerts =
            listOf(
                IncidentAlertUiState.personFound(PERSON_FOUND_MARKER_ID),
                IncidentAlertUiState.supportRequest(SUPPORT_MARKER_ID)
            )

        alerts.forEach { alert ->
            assertTrue(alert.visibleText().contains("확인"))
            assertTrue(alert.visibleText().contains("지도 열기"))
            assertTrue(alert.focusMarkerId.contains("-"))
        }
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val PERSON_FOUND_MARKER_ID = markerIdFixture("precinct-person-found-001")
        val SUPPORT_MARKER_ID = markerIdFixture("precinct-support-001")
    }
}
