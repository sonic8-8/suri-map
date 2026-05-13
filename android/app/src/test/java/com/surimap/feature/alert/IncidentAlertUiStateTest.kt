package com.surimap.feature.alert

import com.surimap.feature.alert.ui.IncidentAlertType
import com.surimap.feature.alert.ui.IncidentAlertUiState
import com.surimap.feature.alert.ui.IncidentFcmPayload
import com.surimap.feature.alert.ui.IncidentFcmRoute
import com.surimap.feature.alert.ui.IncidentFcmRouteMapper
import com.surimap.ui.navigation.SearchMapDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentAlertUiStateTest {

    @Test
    fun personFoundAndSupportRequestAlertsFocusMarkerOnMap() {
        val found = IncidentAlertUiState.personFound(markerId = "mk-person-found-001")
        val support = IncidentAlertUiState.supportRequest(markerId = "mk-support-001")

        assertEquals(IncidentAlertType.PERSON_FOUND, found.type)
        assertEquals("mk-person-found-001", found.focusMarkerId)
        assertTrue(found.visibleText().any { it.contains("실종자 발견") })
        assertTrue(found.visibleText().any { it.contains("지도 열기") })

        assertEquals(IncidentAlertType.SUPPORT_REQUEST, support.type)
        assertEquals("mk-support-001", support.focusMarkerId)
        assertTrue(support.visibleText().any { it.contains("지원 요청") })
        assertTrue(support.visibleText().any { it.contains("확인") })
    }

    @Test
    fun fcmRouteMapsMarkerAlertsAndIncidentClosedSeparately() {
        val markerRoute = IncidentFcmRouteMapper.route(
            IncidentFcmPayload(
                eventId = "evt-person-found-001",
                type = "PERSON_FOUND",
                incidentId = "inc-precinct-first-001",
                markerId = "mk-person-found-001"
            )
        )
        val terminalRoute = IncidentFcmRouteMapper.route(
            IncidentFcmPayload(
                eventId = "evt-incident-closed-001",
                type = "INCIDENT_CLOSED",
                incidentId = "inc-precinct-first-001",
                markerId = null
            )
        )

        assertTrue(markerRoute is IncidentFcmRoute.MarkerFocus)
        val markerFocus = markerRoute as IncidentFcmRoute.MarkerFocus
        assertEquals("mk-person-found-001", markerFocus.markerId)
        assertEquals(SearchMapDeepLink.markerFocusRoute("mk-person-found-001"), markerFocus.searchMapRoute)
        assertTrue(terminalRoute is IncidentFcmRoute.IncidentClosed)
        assertFalse(terminalRoute is IncidentFcmRoute.Alert)
    }

    @Test
    fun routeMapperDoesNotCreateAlertWhenMarkerIdIsMissing() {
        val route = IncidentFcmRouteMapper.route(
            IncidentFcmPayload(
                eventId = "evt-support-001",
                type = "SUPPORT_REQUEST_CREATED",
                incidentId = "inc-precinct-first-001",
                markerId = null
            )
        )

        assertEquals(IncidentFcmRoute.Ignore, route)
    }
}
