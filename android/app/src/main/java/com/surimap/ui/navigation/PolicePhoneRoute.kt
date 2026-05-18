package com.surimap.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.surimap.core.network.AccessTokenProvider

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
    val currentOpId: String? = null,
    val currentOpLabel: String? = null,
    val currentDutyShiftId: String? = null
)

data class PolicePhoneContext(
    val policePhoneId: String,
    val apiBaseUrl: String,
    val tileBaseUrl: String,
    val objectStorageBaseUrl: String,
    val allowedHosts: Set<String> = emptySet(),
    val accessToken: String? = null
)

fun PolicePhoneContext?.accessTokenProvider(): AccessTokenProvider =
    AccessTokenProvider { this?.accessToken?.takeIf(String::isNotBlank) }

@Stable
class IncidentSessionState(
    initialIncidentContext: IncidentContext? = null,
    initialPolicePhoneContext: PolicePhoneContext? = null
) {
    var incidentContext by mutableStateOf(initialIncidentContext)
        private set
    var policePhoneContext by mutableStateOf(initialPolicePhoneContext)
        private set

    fun activatePolicePhoneContext(context: PolicePhoneContext) {
        policePhoneContext = context
    }

    fun activateIncidentContext(context: IncidentContext) {
        incidentContext = context
    }

    fun clearIncidentContext() {
        incidentContext = null
    }
}
