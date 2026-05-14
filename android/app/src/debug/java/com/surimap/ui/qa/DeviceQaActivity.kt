package com.surimap.ui.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.surimap.feature.alert.ui.IncidentAlertUiState
import com.surimap.feature.bootstrap.ui.AuthBootstrapFailureReason
import com.surimap.feature.bootstrap.ui.AuthBootstrapOutcome
import com.surimap.feature.bootstrap.ui.AuthBootstrapScreen
import com.surimap.feature.bootstrap.ui.AuthBootstrapUiState
import com.surimap.feature.handover.ui.DutyHandoverScreen
import com.surimap.feature.handover.ui.HandoverMemoScreen
import com.surimap.feature.handover.ui.sampleDutyHandoverState
import com.surimap.feature.handover.ui.sampleHandoverMemoState
import com.surimap.feature.incidents.ui.IncidentListScreen
import com.surimap.feature.incidents.ui.sampleIncidentListState
import com.surimap.feature.marker.ui.MarkerDetailScreen
import com.surimap.feature.marker.ui.sampleMarkerDetailState
import com.surimap.feature.offline.ui.OfflinePackageScreen
import com.surimap.feature.offline.ui.sampleOfflinePackageState
import com.surimap.feature.outbox.ui.BlockedOutboxScreen
import com.surimap.feature.outbox.ui.sampleBlockedOutboxUiState
import com.surimap.feature.search.ui.SearchMapScreen
import com.surimap.feature.search.ui.SearchMapSyncStatus
import com.surimap.feature.search.ui.SearchMapUiState
import com.surimap.feature.search.ui.sampleSearchMapState
import com.surimap.ui.theme.PoliBgBase
import com.surimap.ui.theme.SuriMapTheme

class DeviceQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val route = DeviceQaRoute.from(intent.getStringExtra(EXTRA_ROUTE))
        setContent {
            SuriMapTheme {
                Surface(color = PoliBgBase, modifier = Modifier.fillMaxSize()) {
                    DeviceQaScreen(route = route)
                }
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "qa_route"
    }
}

enum class DeviceQaRoute(val id: String) {
    AuthBootstrap("p1-auth-bootstrap"),
    IncidentList("p2-incident-list"),
    OfflinePackage("p3-offline-package"),
    SearchMapSynced("p5-synced"),
    SearchMap("p5-search-map"),
    SearchMapBlockedOutbox("p5-blocked-outbox"),
    HandoverSummary("p6-handover-summary"),
    HandoverMemo("p6-handover-memo"),
    IncidentAlert("p8-incident-alert"),
    MarkerDetail("p9-marker-detail"),
    BlockedOutbox("blocked-outbox");

    companion object {
        fun from(id: String?): DeviceQaRoute =
            entries.firstOrNull { route -> route.id == id } ?: AuthBootstrap
    }
}

@Composable
private fun DeviceQaScreen(route: DeviceQaRoute) {
    when (route) {
        DeviceQaRoute.AuthBootstrap ->
            AuthBootstrapScreen(
                state =
                AuthBootstrapUiState.fromOutcome(
                    outcome = AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ServerRejectedPhone),
                    apiBaseUrl = "https://suri-map.internal"
                )
            )

        DeviceQaRoute.IncidentList ->
            IncidentListScreen(
                state = sampleIncidentListState(),
                onOpenIncident = {},
                onRefresh = {},
                onDismissClosedDialog = {}
            )

        DeviceQaRoute.OfflinePackage ->
            OfflinePackageScreen(
                state = sampleOfflinePackageState(),
                onBack = {},
                onOpenSearchMap = {},
                onRetryFailedItems = {}
            )

        DeviceQaRoute.SearchMap ->
            SearchMapScreen(
                state = sampleSearchMapState(),
                onBack = {},
                onPrimaryLifecycleAction = {},
                onStopSearch = {},
                onCreateMarker = {},
                onOpenHandover = {},
                onOpenBlockedOutbox = {},
                onDismissIncidentAlert = {},
                onOpenIncidentAlertMarker = {},
                onOpenFocusedMarkerDetail = {}
            )

        DeviceQaRoute.SearchMapSynced ->
            SearchMapScreen(
                state = SearchMapUiState.active(syncStatus = SearchMapSyncStatus.Synced),
                onBack = {},
                onPrimaryLifecycleAction = {},
                onStopSearch = {},
                onCreateMarker = {},
                onOpenHandover = {},
                onOpenBlockedOutbox = {},
                onDismissIncidentAlert = {},
                onOpenIncidentAlertMarker = {},
                onOpenFocusedMarkerDetail = {}
            )

        DeviceQaRoute.SearchMapBlockedOutbox ->
            SearchMapScreen(
                state =
                SearchMapUiState.active(
                    syncStatus = SearchMapSyncStatus.Offline,
                    unsentCount = 4,
                    oldestPendingMinutes = 15,
                    blockedOutboxCount = 2
                ),
                onBack = {},
                onPrimaryLifecycleAction = {},
                onStopSearch = {},
                onCreateMarker = {},
                onOpenHandover = {},
                onOpenBlockedOutbox = {},
                onDismissIncidentAlert = {},
                onOpenIncidentAlertMarker = {},
                onOpenFocusedMarkerDetail = {}
            )

        DeviceQaRoute.HandoverSummary ->
            DutyHandoverScreen(
                state = sampleDutyHandoverState(),
                onBack = {},
                onWriteMemo = {},
                onOpenSearch = {}
            )

        DeviceQaRoute.HandoverMemo -> {
            var state by remember { mutableStateOf(sampleHandoverMemoState()) }
            HandoverMemoScreen(
                state = state,
                onBack = {},
                onSelectTarget = { target -> state = state.copy(selectedTarget = target) },
                onMemoChange = { memo -> state = state.copy(memoText = memo) },
                onSave = {}
            )
        }

        DeviceQaRoute.IncidentAlert ->
            SearchMapScreen(
                state =
                SearchMapUiState.active(
                    incidentAlert = IncidentAlertUiState.personFoundSample()
                ),
                onBack = {},
                onPrimaryLifecycleAction = {},
                onStopSearch = {},
                onCreateMarker = {},
                onOpenHandover = {},
                onOpenBlockedOutbox = {},
                onDismissIncidentAlert = {},
                onOpenIncidentAlertMarker = {},
                onOpenFocusedMarkerDetail = {}
            )

        DeviceQaRoute.MarkerDetail -> {
            var state by remember { mutableStateOf(sampleMarkerDetailState()) }
            MarkerDetailScreen(
                state = state,
                onBack = {},
                onMemoChange = { memo -> state = state.copy(memo = memo) },
                onSave = {},
                onRequestDelete = { state = state.copy(showDeleteConfirm = true) },
                onDismissDelete = { state = state.copy(showDeleteConfirm = false) },
                onConfirmDelete = {},
                onAddPhoto = {},
                onDeletePhoto = {}
            )
        }

        DeviceQaRoute.BlockedOutbox ->
            BlockedOutboxScreen(
                state = sampleBlockedOutboxUiState(),
                onBack = {},
                onOpenSupportGuide = {}
            )
    }
}
