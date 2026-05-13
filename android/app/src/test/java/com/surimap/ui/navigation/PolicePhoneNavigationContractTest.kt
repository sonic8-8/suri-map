package com.surimap.ui.navigation

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
                incidentId = "inc-precinct-first-001",
                currentOpId = "op-003",
                currentDutyShiftId = "duty-shift-014"
            )
        )
        assertEquals("inc-precinct-first-001", holder.incidentContext?.incidentId)

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
}
