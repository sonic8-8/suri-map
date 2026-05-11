package com.surimap.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
