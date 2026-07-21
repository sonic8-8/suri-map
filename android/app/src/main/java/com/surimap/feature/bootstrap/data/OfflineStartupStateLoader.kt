package com.surimap.feature.bootstrap.data

import com.surimap.core.database.IncidentSummaryDao
import com.surimap.core.database.SearchRecordingStateDao
import com.surimap.feature.search.data.SearchRecordingSessionState
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.ui.navigation.IncidentContext

sealed interface OfflineStartupState {
    data object RequiresConnection : OfflineStartupState

    data object IncidentList : OfflineStartupState

    data class SearchMap(
        val incidentContext: IncidentContext,
        val recordingSession: SearchRecordingSessionState
    ) : OfflineStartupState
}

class OfflineStartupStateLoader(
    private val incidentSummaryDao: IncidentSummaryDao,
    private val searchRecordingStateDao: SearchRecordingStateDao
) {
    suspend fun clearRecoveredSearch(accountId: String, incidentId: String) {
        searchRecordingStateDao.delete(accountId, incidentId)
    }

    suspend fun load(accountId: String?): OfflineStartupState {
        val normalizedAccountId = accountId?.takeIf(String::isNotBlank)
            ?: return OfflineStartupState.RequiresConnection
        val incidents = incidentSummaryDao.findByAccountId(normalizedAccountId)
        val recording = searchRecordingStateDao.findRecoverableByAccountId(normalizedAccountId)

        if (recording != null) {
            val incident = incidents.firstOrNull { it.incidentId == recording.incidentId }
            val lifecycle =
                when (recording.lifecycleStatus) {
                    "ACTIVE" -> SearchLifecycleStatus.Active
                    "PAUSED" -> SearchLifecycleStatus.Paused
                    else -> return OfflineStartupState.RequiresConnection
                }
            return OfflineStartupState.SearchMap(
                incidentContext =
                    IncidentContext(
                        incidentId = recording.incidentId,
                        currentOpId = recording.opId,
                        currentOpLabel = incident?.currentOpLabel,
                        currentDutyShiftId = incident?.currentDutyShiftId
                    ),
                recordingSession =
                    SearchRecordingSessionState(
                        lifecycleOverride = lifecycle,
                        activeLocalSearchPathId = recording.searchPathId,
                        activeStartedAtMs = recording.activeStartedAt,
                        accumulatedElapsedMs = recording.accumulatedElapsed
                    )
            )
        }

        return if (incidents.isEmpty()) {
            OfflineStartupState.RequiresConnection
        } else {
            OfflineStartupState.IncidentList
        }
    }
}
