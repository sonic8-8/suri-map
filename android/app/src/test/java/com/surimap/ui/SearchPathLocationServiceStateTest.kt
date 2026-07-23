package com.surimap.ui

import com.surimap.feature.bootstrap.data.OfflineStartupState
import com.surimap.feature.search.data.SearchRecordingSessionState
import com.surimap.ui.navigation.IncidentContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPathLocationServiceStateTest {
    @Test
    fun roomRecoveryMustFinishBeforeLocationServiceChanges() {
        assertFalse(canUpdateSearchPathLocationService(null, offlineStartupHandled = false))
        assertFalse(
            canUpdateSearchPathLocationService(
                OfflineStartupState.IncidentList,
                offlineStartupHandled = false
            )
        )
        assertFalse(
            canUpdateSearchPathLocationService(
                OfflineStartupState.SearchMap(
                    incidentContext =
                    IncidentContext(
                        incidentId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
                        currentOpId = "88888888-8888-8888-8888-888888880001",
                        currentOpLabel = null,
                        currentDutyShiftId = null
                    ),
                    recordingSession = SearchRecordingSessionState()
                ),
                offlineStartupHandled = false
            )
        )
    }

    @Test
    fun resolvedStartupStateCanChangeLocationService() {
        assertTrue(
            canUpdateSearchPathLocationService(
                OfflineStartupState.IncidentList,
                offlineStartupHandled = true
            )
        )
        assertTrue(
            canUpdateSearchPathLocationService(
                OfflineStartupState.RequiresConnection,
                offlineStartupHandled = false
            )
        )
    }
}
