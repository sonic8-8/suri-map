package com.surimap.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.surimap.feature.bootstrap.ui.AuthBootstrapScreen
import com.surimap.feature.bootstrap.ui.sampleAuthBootstrapState
import com.surimap.feature.handover.ui.DutyHandoverScreen
import com.surimap.feature.handover.ui.HandoverMemoScreen
import com.surimap.feature.handover.ui.sampleDutyHandoverState
import com.surimap.feature.handover.ui.sampleHandoverMemoState
import com.surimap.feature.incidents.ui.IncidentListScreen
import com.surimap.feature.incidents.ui.sampleIncidentListState
import com.surimap.ui.theme.PoliBgBase
import kotlinx.coroutines.delay

private enum class PolicePhoneRoute {
    AuthBootstrap,
    IncidentList,
    DutyHandover,
    HandoverMemo
}

@Composable
fun SuriMapApp() {
    var route by remember { mutableStateOf(PolicePhoneRoute.AuthBootstrap) }
    var incidentClosed by remember { mutableStateOf<IncidentClosedOverlayState?>(null) }
    var blockedQueue by remember { mutableStateOf<BlockedQueueToastState?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = PoliBgBase) {
        AppOverlayHost(
            state = AppOverlayState(incidentClosed = incidentClosed, blockedQueue = blockedQueue),
            onDismissIncidentClosed = {
                incidentClosed = null
                route = PolicePhoneRoute.IncidentList
            },
            onOpenBlockedQueue = {
                blockedQueue = null
            }
        ) {
            when (route) {
                PolicePhoneRoute.AuthBootstrap -> {
                    LaunchedEffect(Unit) {
                        delay(1200)
                        route = PolicePhoneRoute.IncidentList
                    }
                    AuthBootstrapScreen(state = sampleAuthBootstrapState())
                }
                PolicePhoneRoute.IncidentList ->
                    IncidentListScreen(
                        state = sampleIncidentListState(),
                        onOpenIncident = { route = PolicePhoneRoute.DutyHandover },
                        onRefresh = {},
                        onDismissClosedDialog = { incidentClosed = null }
                    )
                PolicePhoneRoute.DutyHandover ->
                    DutyHandoverScreen(
                        state = sampleDutyHandoverState(),
                        onBack = { route = PolicePhoneRoute.IncidentList },
                        onWriteMemo = { route = PolicePhoneRoute.HandoverMemo },
                        onOpenSearch = { blockedQueue = BlockedQueueToastState(blockedCount = 2) }
                    )
                PolicePhoneRoute.HandoverMemo ->
                    HandoverMemoScreen(
                        state = sampleHandoverMemoState(),
                        onBack = { route = PolicePhoneRoute.DutyHandover },
                        onSave = { route = PolicePhoneRoute.DutyHandover }
                    )
            }
        }
    }
}
