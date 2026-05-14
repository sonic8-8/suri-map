package com.surimap.ui.navigation

import com.surimap.testing.dutyShiftIdFixture
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.opIdFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PolicePhoneNavigationContractTest {
    @Test
    fun routeRegistryContainsAuiT02RoutesInOrder() {
        assertEquals(
            listOf(
                "auth_bootstrap",
                "incident_list",
                "offline_package",
                "search_map",
                "handover_summary",
                "handover_memo",
                "marker_detail",
                "blocked_outbox"
            ),
            PolicePhoneRoutes.all.map { it.route }
        )
    }

    @Test
    fun incidentSessionHolderClearsIncidentContext() {
        val holder = IncidentSessionState()

        holder.activateIncidentContext(
            IncidentContext(
                incidentId = INCIDENT_ID,
                currentOpId = OP_ID,
                currentDutyShiftId = DUTY_SHIFT_ID
            )
        )
        assertEquals(INCIDENT_ID, holder.incidentContext?.incidentId)

        holder.clearIncidentContext()

        assertNull(holder.incidentContext)
    }

    @Test
    fun policePhoneContextCarriesBootstrapAccessTokenForProtectedAppRequests() {
        val context =
            PolicePhoneContext(
                policePhoneId = "00000000-0000-0000-0000-000000000101",
                apiBaseUrl = "https://suri-map.internal/api",
                tileBaseUrl = "https://suri-map.internal",
                objectStorageBaseUrl = "https://suri-map.internal",
                accessToken = "bootstrap-token-1"
            )

        assertEquals("bootstrap-token-1", context.accessTokenProvider().accessToken())
    }

    @Test
    fun searchMapMarkerFocusRoutePreservesMarkerIdAsDeeplinkArgument() {
        val route = SearchMapDeepLink.markerFocusRoute("mk-person/found 001")

        assertEquals("search_map?focusMarkerId=mk-person%2Ffound+001", route)
        assertEquals("mk-person/found 001", SearchMapDeepLink.focusMarkerIdFromRoute(route))
        assertNull(SearchMapDeepLink.focusMarkerIdFromRoute(PolicePhoneRoute.SearchMap.route))
    }

    @Test
    fun markerDetailRoutePreservesMarkerIdAsPathArgument() {
        val route = MarkerDetailDeepLink.route("mk-person/found 001")

        assertEquals("marker_detail/mk-person%2Ffound%20001", route)
        assertEquals("mk-person/found 001", MarkerDetailDeepLink.markerIdFromRoute(route))
        assertNull(MarkerDetailDeepLink.markerIdFromRoute(PolicePhoneRoute.MarkerDetail.route))
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("003")
        val DUTY_SHIFT_ID = dutyShiftIdFixture("014")
    }
}
