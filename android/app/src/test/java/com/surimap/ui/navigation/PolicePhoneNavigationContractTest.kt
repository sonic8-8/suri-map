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
}
