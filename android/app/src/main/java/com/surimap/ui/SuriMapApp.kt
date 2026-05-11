package com.surimap.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.surimap.BuildConfig
import com.surimap.core.network.SuriMapApiClient
import com.surimap.feature.bootstrap.data.AndroidManagedConfigurationReader
import com.surimap.feature.bootstrap.data.AuthBootstrapCoordinator
import com.surimap.feature.bootstrap.data.AuthBootstrapServerCheck
import com.surimap.feature.bootstrap.data.NetworkPolicePhoneBootstrapServerCheck
import com.surimap.feature.bootstrap.ui.AuthBootstrapScreen
import com.surimap.feature.bootstrap.ui.AuthBootstrapUiState
import com.surimap.feature.handover.ui.DutyHandoverScreen
import com.surimap.feature.handover.ui.HandoverMemoScreen
import com.surimap.feature.handover.ui.sampleDutyHandoverState
import com.surimap.feature.handover.ui.sampleHandoverMemoState
import com.surimap.feature.incidents.data.IncidentListStateLoader
import com.surimap.feature.incidents.ui.IncidentListScreen
import com.surimap.feature.incidents.ui.IncidentListUiState
import com.surimap.feature.marker.ui.MarkerCreateBottomSheet
import com.surimap.feature.marker.ui.MarkerDetailScreen
import com.surimap.feature.marker.ui.MarkerType
import com.surimap.feature.marker.ui.sampleMarkerDetailState
import com.surimap.feature.marker.ui.sampleMarkerCreateSheetState
import com.surimap.feature.offline.ui.OfflinePackageScreen
import com.surimap.feature.offline.ui.sampleOfflinePackageState
import com.surimap.feature.search.ui.SearchMapScreen
import com.surimap.feature.search.ui.sampleSearchMapState
import com.surimap.ui.navigation.BlockedOutboxRouteScreen
import com.surimap.ui.navigation.IncidentSessionState
import com.surimap.ui.navigation.PolicePhoneRoute
import com.surimap.ui.theme.PoliBgBase

@Composable
fun SuriMapApp() {
    val navController = rememberNavController()
    val incidentSessionState = remember { IncidentSessionState() }
    var incidentClosed by remember { mutableStateOf<IncidentClosedOverlayState?>(null) }
    var blockedQueue by remember { mutableStateOf<BlockedQueueToastState?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = PoliBgBase) {
        AppOverlayHost(
            state = AppOverlayState(incidentClosed = incidentClosed, blockedQueue = blockedQueue),
            onDismissIncidentClosed = {
                incidentClosed = null
                incidentSessionState.clearIncidentContext()
                navController.navigateToIncidentListRoot()
            },
            onOpenBlockedQueue = {
                blockedQueue = null
                navController.navigateToSingleTop(PolicePhoneRoute.BlockedOutbox)
            }
        ) {
            NavHost(
                navController = navController,
                startDestination = PolicePhoneRoute.AuthBootstrap.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(PolicePhoneRoute.AuthBootstrap.route) {
                    AuthBootstrapRoute(navController = navController)
                }
                composable(PolicePhoneRoute.IncidentList.route) {
                    IncidentListRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController,
                        incidentClosed = incidentClosed,
                        onClearClosedOverlay = { incidentClosed = null }
                    )
                }
                composable(PolicePhoneRoute.OfflinePackage.route) {
                    OfflinePackageScreen(
                        state = sampleOfflinePackageState(),
                        onBack = { navController.popBackStack() },
                        onOpenSearchMap = { navController.navigateToSingleTop(PolicePhoneRoute.SearchMap) },
                        onRetryFailedItems = {}
                    )
                }
                composable(PolicePhoneRoute.SearchMap.route) {
                    var markerSheetOpen by remember { mutableStateOf(false) }
                    var markerSheetState by remember { mutableStateOf(sampleMarkerCreateSheetState()) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        SearchMapScreen(
                            state = sampleSearchMapState(),
                            onBack = { navController.popBackStack() },
                            onPrimaryLifecycleAction = {},
                            onStopSearch = {},
                            onCreateMarker = {
                                markerSheetState = sampleMarkerCreateSheetState()
                                markerSheetOpen = true
                            },
                            onOpenHandover = { navController.navigateToSingleTop(PolicePhoneRoute.HandoverSummary) },
                            onOpenBlockedOutbox = { blockedQueue = BlockedQueueToastState(blockedCount = 2) }
                        )
                        if (markerSheetOpen) {
                            MarkerCreateBottomSheet(
                                state = markerSheetState,
                                onDismiss = { markerSheetOpen = false },
                                onSelectMarkerType = { type ->
                                    markerSheetState =
                                        markerSheetState.copy(
                                            selectedType = type,
                                            supportRequestType =
                                            if (type == MarkerType.SUPPORT_REQUEST) {
                                                markerSheetState.supportRequestType
                                            } else {
                                                null
                                            }
                                        )
                                },
                                onSelectSupportRequestType = { type ->
                                    markerSheetState = markerSheetState.copy(supportRequestType = type)
                                },
                                onMemoChange = { memo ->
                                    markerSheetState = markerSheetState.copy(memo = memo)
                                },
                                onSave = { markerSheetOpen = false },
                                onAttachPhoto = {},
                                onRetryPhoto = {}
                            )
                        }
                    }
                }
                composable(PolicePhoneRoute.HandoverSummary.route) {
                    DutyHandoverScreen(
                        state = sampleDutyHandoverState(),
                        onBack = { navController.popBackStack() },
                        onWriteMemo = { navController.navigateToSingleTop(PolicePhoneRoute.HandoverMemo) },
                        onOpenSearch = { navController.navigateToSingleTop(PolicePhoneRoute.SearchMap) }
                    )
                }
                composable(PolicePhoneRoute.HandoverMemo.route) {
                    HandoverMemoScreen(
                        state = sampleHandoverMemoState(),
                        onBack = { navController.popBackStack() },
                        onSave = { navController.popBackStack() }
                    )
                }
                composable(PolicePhoneRoute.MarkerDetail.route) {
                    var markerDetailState by remember { mutableStateOf(sampleMarkerDetailState()) }
                    MarkerDetailScreen(
                        state = markerDetailState,
                        onBack = { navController.popBackStack() },
                        onMemoChange = { memo -> markerDetailState = markerDetailState.copy(memo = memo) },
                        onSave = { navController.popBackStack() },
                        onRequestDelete = {
                            markerDetailState = markerDetailState.copy(showDeleteConfirm = true)
                        },
                        onDismissDelete = {
                            markerDetailState = markerDetailState.copy(showDeleteConfirm = false)
                        },
                        onConfirmDelete = { navController.popBackStack() },
                        onAddPhoto = {},
                        onDeletePhoto = {}
                    )
                }
                composable(PolicePhoneRoute.BlockedOutbox.route) {
                    BlockedOutboxRouteScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun AuthBootstrapRoute(navController: NavHostController) {
    val context = LocalContext.current.applicationContext
    val managedConfigurationReader = remember(context) {
        AndroidManagedConfigurationReader(context = context)
    }
    val bootstrapCoordinator = remember(managedConfigurationReader) {
        AuthBootstrapCoordinator(
            managedConfigurationReader = managedConfigurationReader,
            serverCheck =
            AuthBootstrapServerCheck { config ->
                NetworkPolicePhoneBootstrapServerCheck(
                    apiClient = SuriMapApiClient(baseUrl = config.apiBaseUrl)
                ).verify(config)
            }
        )
    }
    var retryNonce by remember { mutableStateOf(0) }
    var state by remember {
        mutableStateOf(AuthBootstrapUiState.checking(apiBaseUrl = BuildConfig.SURI_MAP_API_BASE_URL))
    }

    LaunchedEffect(retryNonce) {
        val config = bootstrapCoordinator.readConfig()
        state = AuthBootstrapUiState.checking(apiBaseUrl = config.apiBaseUrl)
        val outcome = bootstrapCoordinator.check(config)
        state = AuthBootstrapUiState.fromOutcome(outcome = outcome, apiBaseUrl = config.apiBaseUrl)
        if (state.shouldEnterIncidentList) {
            navController.navigate(PolicePhoneRoute.IncidentList.route) {
                popUpTo(PolicePhoneRoute.AuthBootstrap.route) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    AuthBootstrapScreen(
        state = state,
        onRetry = { retryNonce += 1 }
    )
}

@Composable
private fun IncidentListRoute(
    incidentSessionState: IncidentSessionState,
    navController: NavHostController,
    incidentClosed: IncidentClosedOverlayState?,
    onClearClosedOverlay: () -> Unit
) {
    val loader = remember {
        IncidentListStateLoader(
            policePhoneLabel = "관리 폴리폰"
        )
    }
    var refreshNonce by remember { mutableStateOf(0) }
    var state by remember {
        mutableStateOf(IncidentListUiState.loading(policePhoneLabel = "관리 폴리폰"))
    }

    LaunchedEffect(refreshNonce, incidentClosed) {
        state = IncidentListUiState.loading(policePhoneLabel = "관리 폴리폰")
        state = loader.load().copy(showClosedDialog = incidentClosed != null)
        if (state.shouldClearIncidentContext || incidentClosed != null) {
            incidentSessionState.clearIncidentContext()
        }
    }

    IncidentListScreen(
        state = state,
        onOpenIncident = { incident ->
            incidentSessionState.activateIncidentContext(incident.toIncidentContext())
            navController.navigateToSingleTop(PolicePhoneRoute.OfflinePackage)
        },
        onRefresh = { refreshNonce += 1 },
        onDismissClosedDialog = {
            onClearClosedOverlay()
            incidentSessionState.clearIncidentContext()
        }
    )
}

private fun NavHostController.navigateToSingleTop(route: PolicePhoneRoute) {
    navigate(route.route) {
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToIncidentListRoot() {
    navigate(PolicePhoneRoute.IncidentList.route) {
        popUpTo(PolicePhoneRoute.IncidentList.route) {
            inclusive = true
        }
        launchSingleTop = true
    }
}
