package com.surimap.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class IncidentSessionStateTest {
    @Test
    fun incidentSessionActivatesAndClearsCurrentIncidentContext() {
        val state = IncidentSessionState()
        assertNull(state.incidentContext)

        val context = IncidentContext(
            incidentId = "inc-precinct-first-001",
            currentOpId = "op-precinct-001-op1",
            currentDutyShiftId = "shift-precinct-op1-001"
        )
        state.activateIncidentContext(context)
        assertEquals(context, state.incidentContext)

        state.clearIncidentContext()
        assertNull(state.incidentContext)
    }

    @Test
    fun incidentContextIsActivityScopedStateAndDoesNotRequirePersistenceFields() {
        val restoredAtActivityScope = IncidentSessionState(
            initialIncidentContext = IncidentContext(incidentId = "inc-precinct-first-001")
        )

        assertEquals("inc-precinct-first-001", restoredAtActivityScope.incidentContext?.incidentId)
        assertNull(restoredAtActivityScope.incidentContext?.currentOpId)
        assertNull(restoredAtActivityScope.incidentContext?.currentDutyShiftId)
    }

    @Test
    fun policePhoneContextSurvivesIncidentContextClearForApiWiring() {
        val phoneContext =
            PolicePhoneContext(
                policePhoneId = "police-phone-precinct-car-01",
                apiBaseUrl = "https://suri-map.internal/api",
                tileBaseUrl = "https://suri-map.internal/tiles",
                objectStorageBaseUrl = "https://suri-map.internal/objects",
                allowedHosts = setOf("suri-map.internal")
            )
        val state = IncidentSessionState()

        state.activatePolicePhoneContext(phoneContext)
        state.activateIncidentContext(IncidentContext(incidentId = "inc-precinct-first-001"))
        state.clearIncidentContext()

        assertNull(state.incidentContext)
        assertSame(phoneContext, state.policePhoneContext)
    }
}
