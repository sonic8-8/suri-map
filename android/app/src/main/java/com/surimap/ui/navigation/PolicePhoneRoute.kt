package com.surimap.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class PolicePhoneRoute(val route: String) {
    AuthBootstrap("auth_bootstrap"),
    IncidentList("incident_list"),
    OfflinePackage("offline_package"),
    SearchMap("search_map"),
    HandoverSummary("handover_summary"),
    HandoverMemo("handover_memo"),
    MarkerDetail("marker_detail"),
    BlockedOutbox("blocked_outbox")
}

object PolicePhoneRoutes {
    val all: List<PolicePhoneRoute> =
        listOf(
            PolicePhoneRoute.AuthBootstrap,
            PolicePhoneRoute.IncidentList,
            PolicePhoneRoute.OfflinePackage,
            PolicePhoneRoute.SearchMap,
            PolicePhoneRoute.HandoverSummary,
            PolicePhoneRoute.HandoverMemo,
            PolicePhoneRoute.MarkerDetail,
            PolicePhoneRoute.BlockedOutbox
        )
}

data class IncidentContext(
    val incidentId: String,
    val currentOpId: String,
    val currentDutyShiftId: String
)

@Stable
class IncidentSessionState(initialIncidentContext: IncidentContext? = null) {
    var incidentContext by mutableStateOf(initialIncidentContext)
        private set

    fun activateIncidentContext(context: IncidentContext) {
        incidentContext = context
    }

    fun clearIncidentContext() {
        incidentContext = null
    }
}
