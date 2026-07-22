package com.surimap.ui.navigation

import com.surimap.testing.dutyShiftIdFixture
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.policePhoneIdFixture
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
            incidentId = INCIDENT_ID,
            currentOpId = OP_ID,
            currentDutyShiftId = DUTY_SHIFT_ID
        )
        state.activateIncidentContext(context)
        assertEquals(context, state.incidentContext)

        state.clearIncidentContext()
        assertNull(state.incidentContext)
    }

    @Test
    fun incidentContextIsActivityScopedStateAndDoesNotRequirePersistenceFields() {
        val restoredAtActivityScope = IncidentSessionState(
            initialIncidentContext = IncidentContext(incidentId = INCIDENT_ID)
        )

        assertEquals(INCIDENT_ID, restoredAtActivityScope.incidentContext?.incidentId)
        assertNull(restoredAtActivityScope.incidentContext?.currentOpId)
        assertNull(restoredAtActivityScope.incidentContext?.currentDutyShiftId)
    }

    @Test
    fun policePhoneContextSurvivesIncidentContextClearForApiWiring() {
        val phoneContext =
            PolicePhoneContext(
                policePhoneId = POLICE_PHONE_ID,
                apiBaseUrl = "https://suri-map.internal/api",
                tileBaseUrl = "https://suri-map.internal/tiles",
                objectStorageBaseUrl = "https://suri-map.internal/objects",
                allowedHosts = setOf("suri-map.internal")
            )
        val state = IncidentSessionState()

        state.activatePolicePhoneContext(phoneContext)
        state.activateIncidentContext(IncidentContext(incidentId = INCIDENT_ID))
        state.clearIncidentContext()

        assertNull(state.incidentContext)
        assertSame(phoneContext, state.policePhoneContext)
    }

    @Test
    fun oidcSessionExpiryKeepsIncidentAndAccountButRemovesToken() {
        val incidentContext = IncidentContext(incidentId = INCIDENT_ID)
        val state =
            IncidentSessionState(
                initialIncidentContext = incidentContext,
                initialPolicePhoneContext = phoneContext(accountId = "account-1")
            )

        state.clearOidcSession()

        assertSame(incidentContext, state.incidentContext)
        assertEquals("account-1", state.policePhoneContext?.accountId)
        assertNull(state.policePhoneContext?.accessToken)
        assertNull(state.policePhoneContext?.accessTokenExpiresAtEpochMs)
    }

    @Test
    fun sameAccountLoginKeepsCurrentIncident() {
        val incidentContext = IncidentContext(incidentId = INCIDENT_ID)
        val state =
            IncidentSessionState(
                initialIncidentContext = incidentContext,
                initialPolicePhoneContext = phoneContext(accountId = "account-1")
            )

        state.activatePolicePhoneContext(phoneContext(accountId = "account-1"))

        assertSame(incidentContext, state.incidentContext)
    }

    @Test
    fun differentAccountLoginClearsCurrentIncident() {
        val state =
            IncidentSessionState(
                initialIncidentContext = IncidentContext(incidentId = INCIDENT_ID),
                initialPolicePhoneContext = phoneContext(accountId = "account-1")
            )

        state.activatePolicePhoneContext(phoneContext(accountId = "account-2"))

        assertNull(state.incidentContext)
    }

    @Test
    fun offlineStartupUsesOnlyAContextWithAuthentication() {
        val authenticated = phoneContext(accountId = "account-1")

        assertEquals("account-1", authenticated.offlineStartupAccountId())
        assertNull(authenticated.copy(accessToken = null).offlineStartupAccountId())
    }

    private fun phoneContext(accountId: String): PolicePhoneContext =
        PolicePhoneContext(
            policePhoneId = POLICE_PHONE_ID,
            apiBaseUrl = "https://suri-map.internal/api",
            tileBaseUrl = "https://suri-map.internal/tiles",
            objectStorageBaseUrl = "https://suri-map.internal/objects",
            accessToken = "access-token",
            accessTokenExpiresAtEpochMs = 1_000L,
            accountId = accountId
        )

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-001-op1")
        val DUTY_SHIFT_ID = dutyShiftIdFixture("precinct-op1-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-car-01")
    }
}
