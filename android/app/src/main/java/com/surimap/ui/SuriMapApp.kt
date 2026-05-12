package com.surimap.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.surimap.BuildConfig
import com.surimap.core.database.OfflinePackageInstallationEntity
import com.surimap.core.database.OfflinePackageItemStatusEntity
import com.surimap.core.database.SuriMapDatabaseProvider
import com.surimap.core.incident.IncidentReadRepository
import com.surimap.core.map.MapLibreRuntimeMapState
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.offline.OfflinePackageDownloadScheduler
import com.surimap.core.offline.OfflinePackageDownloadWorkRequest
import com.surimap.core.offline.OfflinePackageInstallationStatus
import com.surimap.core.offline.OfflinePackageItemStatus
import com.surimap.core.offline.OfflinePackageManifestQuery
import com.surimap.core.offline.OfflinePackageRepository
import com.surimap.core.offline.toOfflinePackageItemStatusEntity
import com.surimap.core.operationalperiod.HandoverMemoRepository
import com.surimap.core.operationalperiod.SearchHistorySummaryReadRepository
import com.surimap.core.path.SearchPathRepository
import com.surimap.core.searcharea.SearchAreaReadRepository
import com.surimap.core.sync.RoomSyncClient
import com.surimap.feature.bootstrap.data.AndroidManagedConfigurationReader
import com.surimap.feature.bootstrap.data.AuthBootstrapCoordinator
import com.surimap.feature.bootstrap.data.AuthBootstrapServerCheck
import com.surimap.feature.bootstrap.data.ManagedPolicePhoneConfig
import com.surimap.feature.bootstrap.data.NetworkPolicePhoneBootstrapServerCheck
import com.surimap.feature.bootstrap.ui.AuthBootstrapScreen
import com.surimap.feature.bootstrap.ui.AuthBootstrapUiState
import com.surimap.feature.handover.data.DutyHandoverStateLoader
import com.surimap.feature.handover.data.HandoverMemoInput
import com.surimap.feature.handover.data.HandoverMemoLocalRecorder
import com.surimap.feature.handover.data.HandoverSessionContext
import com.surimap.feature.handover.data.HandoverWriteContext
import com.surimap.feature.handover.data.HandoverWriteResult
import com.surimap.feature.handover.ui.DutyHandoverScreen
import com.surimap.feature.handover.ui.HandoverMemoScreen
import com.surimap.feature.handover.ui.HandoverMemoTarget
import com.surimap.feature.handover.ui.HandoverMemoUiState
import com.surimap.feature.incidents.data.IncidentListStateLoader
import com.surimap.feature.incidents.ui.IncidentListScreen
import com.surimap.feature.incidents.ui.IncidentListUiState
import com.surimap.feature.marker.data.MarkerLocalRecorder
import com.surimap.feature.marker.data.MarkerLocation
import com.surimap.feature.marker.data.MarkerUpsertInput
import com.surimap.feature.marker.data.MarkerWriteContext
import com.surimap.feature.marker.data.MarkerWriteResult
import com.surimap.feature.marker.ui.MarkerCreateBottomSheet
import com.surimap.feature.marker.ui.MarkerDetailScreen
import com.surimap.feature.marker.ui.MarkerCreateSheetUiState
import com.surimap.feature.marker.ui.MarkerSaveStatus
import com.surimap.feature.marker.ui.MarkerType
import com.surimap.feature.marker.ui.sampleMarkerDetailState
import com.surimap.feature.marker.ui.sampleMarkerCreateSheetState
import com.surimap.feature.offline.data.OfflinePackageStateLoader
import com.surimap.feature.offline.ui.OfflinePackageScreen
import com.surimap.feature.offline.ui.OfflinePackageUiState
import com.surimap.feature.search.data.SearchMapSessionContext
import com.surimap.feature.search.data.SearchMapStateLoader
import com.surimap.feature.search.data.SearchPathLocalRecorder
import com.surimap.feature.search.data.SearchPathWriteContext
import com.surimap.feature.search.ui.SearchLayerKind
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.feature.search.ui.SearchMapScreen
import com.surimap.feature.search.ui.SearchMapUiState
import com.surimap.ui.navigation.BlockedOutboxRouteScreen
import com.surimap.ui.navigation.IncidentContext
import com.surimap.ui.navigation.IncidentSessionState
import com.surimap.ui.navigation.PolicePhoneContext
import com.surimap.ui.navigation.PolicePhoneRoute
import com.surimap.ui.theme.PoliBgBase
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SuriMapApp() {
    val navController = rememberNavController()
    val incidentSessionState = remember { IncidentSessionState() }
    var incidentClosed by remember { mutableStateOf<IncidentClosedOverlayState?>(null) }
    var blockedQueue by remember { mutableStateOf<BlockedQueueToastState?>(null) }
    var handoverMemoSaved by remember { mutableStateOf<HandoverMemoSavedToastState?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = PoliBgBase) {
        AppOverlayHost(
            state =
            AppOverlayState(
                incidentClosed = incidentClosed,
                blockedQueue = blockedQueue,
                handoverMemoSaved = handoverMemoSaved
            ),
            onDismissIncidentClosed = {
                incidentClosed = null
                incidentSessionState.clearIncidentContext()
                navController.navigateToIncidentListRoot()
            },
            onOpenBlockedQueue = {
                blockedQueue = null
                navController.navigateToSingleTop(PolicePhoneRoute.BlockedOutbox)
            },
            onDismissHandoverMemoSaved = { handoverMemoSaved = null }
        ) {
            NavHost(
                navController = navController,
                startDestination = PolicePhoneRoute.AuthBootstrap.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(PolicePhoneRoute.AuthBootstrap.route) {
                    AuthBootstrapRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController
                    )
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
                    OfflinePackageRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController
                    )
                }
                composable(PolicePhoneRoute.SearchMap.route) {
                    SearchMapRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController,
                        onOpenBlockedOutbox = {
                            blockedQueue = BlockedQueueToastState(blockedCount = 2)
                        }
                    )
                }
                composable(PolicePhoneRoute.HandoverSummary.route) {
                    HandoverSummaryRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController
                    )
                }
                composable(PolicePhoneRoute.HandoverMemo.route) {
                    HandoverMemoRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController,
                        onMemoSaved = { pendingSync ->
                            handoverMemoSaved = HandoverMemoSavedToastState(pendingSync = pendingSync)
                        }
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
private fun HandoverSummaryRoute(
    incidentSessionState: IncidentSessionState,
    navController: NavHostController
) {
    val incidentContext = incidentSessionState.incidentContext
    val policePhoneContext = incidentSessionState.policePhoneContext
    val sessionContext = incidentContext.toHandoverSessionContext(policePhoneContext)
    val apiBaseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
    val loader =
        remember(apiBaseUrl) {
            DutyHandoverStateLoader(
                handoverMemos = { query ->
                    HandoverMemoRepository(
                        apiClient = SuriMapApiClient(baseUrl = apiBaseUrl)
                    ).listHandoverMemos(query)
                },
                searchHistorySummaries = { operationalPeriodId, query ->
                    SearchHistorySummaryReadRepository(
                        apiClient = SuriMapApiClient(baseUrl = apiBaseUrl)
                    ).list(operationalPeriodId, query)
                }
            )
        }
    var handoverState by remember(loader, sessionContext) {
        mutableStateOf(loader.fallback(sessionContext))
    }

    LaunchedEffect(loader, sessionContext) {
        handoverState = loader.fallback(sessionContext)
        handoverState = loader.load(sessionContext)
    }

    DutyHandoverScreen(
        state = handoverState,
        onBack = { navController.popBackStack() },
        onWriteMemo = { navController.navigateToSingleTop(PolicePhoneRoute.HandoverMemo) },
        onOpenSearch = { navController.navigateToSingleTop(PolicePhoneRoute.SearchMap) }
    )
}

@Composable
private fun HandoverMemoRoute(
    incidentSessionState: IncidentSessionState,
    navController: NavHostController,
    onMemoSaved: (Boolean) -> Unit
) {
    val incidentContext = incidentSessionState.incidentContext
    val policePhoneContext = incidentSessionState.policePhoneContext
    val sessionContext = incidentContext.toHandoverSessionContext(policePhoneContext)
    val context = LocalContext.current.applicationContext
    val database = remember(context) { SuriMapDatabaseProvider.database(context) }
    val syncClient = remember(database) { RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()) }
    val recorder = remember(syncClient) { HandoverMemoLocalRecorder(syncClient = syncClient) }
    val coroutineScope = rememberCoroutineScope()
    var memoState by remember(sessionContext) {
        mutableStateOf(HandoverMemoUiState.default().withContext(sessionContext))
    }

    HandoverMemoScreen(
        state = memoState,
        onBack = { navController.popBackStack() },
        onSelectTarget = { target ->
            memoState = memoState.copy(selectedTarget = target).withTargetContext(sessionContext)
        },
        onMemoChange = { memo ->
            memoState = memoState.copy(memoText = memo)
        },
        onSave = {
            coroutineScope.launch {
                memoState = memoState.copy(saving = true)
                when (
                    recorder.createMemo(
                        context = sessionContext.toHandoverWriteContext(),
                        input = memoState.toHandoverMemoInput(sessionContext)
                    )
                ) {
                    HandoverWriteResult.Blocked -> {
                        memoState = memoState.copy(saving = false)
                    }

                    is HandoverWriteResult.Enqueued -> {
                        onMemoSaved(true)
                        navController.popBackStack()
                    }
                }
            }
        }
    )
}

@Composable
private fun SearchMapRoute(
    incidentSessionState: IncidentSessionState,
    navController: NavHostController,
    onOpenBlockedOutbox: () -> Unit
) {
    val incidentContext = incidentSessionState.incidentContext
    val policePhoneContext = incidentSessionState.policePhoneContext
    val context = LocalContext.current.applicationContext
    val database = remember(context) { SuriMapDatabaseProvider.database(context) }
    val outboxDao = remember(database) { database.outboxDao() }
    val sessionContext = incidentContext.toSearchMapSessionContext(policePhoneContext)
    val syncClient = remember(database) { RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()) }
    val searchPathRecorder = remember(syncClient) {
        SearchPathLocalRecorder(
            syncClient = syncClient
        )
    }
    val markerRecorder = remember(syncClient) {
        MarkerLocalRecorder(
            syncClient = syncClient
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val loader =
        remember(policePhoneContext?.apiBaseUrl, outboxDao) {
            SearchMapStateLoader(
                incidentDetail = { incidentId ->
                    IncidentReadRepository(
                        apiClient =
                        SuriMapApiClient(
                            baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                        )
                    ).detail(incidentId)
                },
                overallSearchArea = { incidentId ->
                    SearchAreaReadRepository(
                        apiClient =
                        SuriMapApiClient(
                            baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                        )
                    ).activeOverall(incidentId)
                },
                opSearchAreas = { incidentId, opId ->
                    SearchAreaReadRepository(
                        apiClient =
                        SuriMapApiClient(
                            baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                        )
                    ).list(incidentId = incidentId, opId = opId, status = "ACTIVE")
                },
                searchPaths = { query ->
                    SearchPathRepository(
                        apiClient =
                        SuriMapApiClient(
                            baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                        )
                    ).listSearchPaths(query)
                },
                initialMarkers = { incidentId, policePhoneId ->
                    OfflinePackageRepository(
                        apiClient =
                        SuriMapApiClient(
                            baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                        )
                    ).manifest(
                        OfflinePackageManifestQuery(
                            incidentId = incidentId,
                            policePhoneId = policePhoneId
                        )
                    )
                },
                outboxSummary = { incidentId, policePhoneId ->
                    outboxDao.statusSummary(incidentId = incidentId, policePhoneId = policePhoneId)
                }
            )
        }
    var searchMapState by remember {
        mutableStateOf(SearchMapStateLoader().fallbackForRemember(sessionContext))
    }
    var markerSheetOpen by remember { mutableStateOf(false) }
    var markerSheetState by remember { mutableStateOf(sampleMarkerCreateSheetState()) }

    LaunchedEffect(loader, sessionContext) {
        searchMapState = loader.fallback(sessionContext)
        searchMapState = loader.load(sessionContext)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SearchMapScreen(
            state = searchMapState,
            mapState = policePhoneContext.toMapLibreRuntimeMapState(),
            onBack = { navController.popBackStack() },
            onPrimaryLifecycleAction = {
                coroutineScope.launch {
                    if (searchMapState.lifecycleStatus == SearchLifecycleStatus.Stopped) {
                        searchPathRecorder.start(sessionContext.toSearchPathWriteContext())
                    }
                }
            },
            onStopSearch = {
                coroutineScope.launch {
                    searchPathRecorder.end(
                        context = sessionContext.toSearchPathWriteContext(),
                        searchPathId = searchMapState.activeSearchPathId()
                    )
                }
            },
            onCreateMarker = {
                markerSheetState = sampleMarkerCreateSheetState().withLocation(searchMapState.markerCreationLocation())
                markerSheetOpen = true
            },
            onOpenHandover = { navController.navigateToSingleTop(PolicePhoneRoute.HandoverSummary) },
            onOpenBlockedOutbox = onOpenBlockedOutbox,
            onDismissIncidentAlert = {},
            onOpenIncidentAlertMarker = {
                navController.navigateToSingleTop(PolicePhoneRoute.SearchMap)
            }
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
                onSave = {
                    coroutineScope.launch {
                        markerSheetState = markerSheetState.copy(saveStatus = MarkerSaveStatus.Saving)
                        when (
                            markerRecorder.createMarker(
                                context = sessionContext.toMarkerWriteContext(),
                                input = markerSheetState.toMarkerUpsertInput(searchMapState.markerCreationLocation())
                            )
                        ) {
                            MarkerWriteResult.Blocked -> {
                                markerSheetState = markerSheetState.copy(saveStatus = MarkerSaveStatus.Failed)
                            }
                            is MarkerWriteResult.Enqueued -> {
                                markerSheetState = markerSheetState.copy(saveStatus = MarkerSaveStatus.PendingOutbox)
                                markerSheetOpen = false
                                searchMapState = loader.load(sessionContext)
                            }
                        }
                    }
                },
                onAttachPhoto = {},
                onRetryPhoto = {}
            )
        }
    }
}

@Composable
private fun OfflinePackageRoute(
    incidentSessionState: IncidentSessionState,
    navController: NavHostController
) {
    val incidentContext = incidentSessionState.incidentContext
    val policePhoneContext = incidentSessionState.policePhoneContext
    val context = LocalContext.current.applicationContext
    val database = remember(context) { SuriMapDatabaseProvider.database(context) }
    val offlinePackageInstallationDao = remember(database) { database.offlinePackageInstallationDao() }
    val offlinePackageItemStatusDao = remember(database) { database.offlinePackageItemStatusDao() }
    val offlinePackageDownloadScheduler = remember(context) {
        OfflinePackageDownloadScheduler(WorkManager.getInstance(context))
    }
    val loader =
        remember(
            incidentContext?.incidentId,
            policePhoneContext?.policePhoneId,
            policePhoneContext?.apiBaseUrl,
            offlinePackageInstallationDao,
            offlinePackageItemStatusDao,
            offlinePackageDownloadScheduler
        ) {
            if (incidentContext == null || policePhoneContext == null) {
                null
            } else {
                OfflinePackageStateLoader(
                    repository =
                    OfflinePackageRepository(
                        apiClient = SuriMapApiClient(baseUrl = policePhoneContext.apiBaseUrl)
                    ),
                    incidentId = incidentContext.incidentId,
                    policePhoneId = policePhoneContext.policePhoneId,
                    localInstallationStatus = {
                        offlinePackageInstallationDao.find(
                            incidentId = incidentContext.incidentId,
                            policePhoneId = policePhoneContext.policePhoneId
                        )?.toOfflinePackageInstallationStatus()
                    },
                    localPackageItems = { manifestId ->
                        offlinePackageItemStatusDao.findByManifest(
                            incidentId = incidentContext.incidentId,
                            policePhoneId = policePhoneContext.policePhoneId,
                            manifestId = manifestId
                        ).map(OfflinePackageItemStatusEntity::toOfflinePackageItemStatus)
                    },
                    onDownloadPlanAvailable = { plan ->
                        val existing = offlinePackageItemStatusDao.findByManifest(
                            incidentId = incidentContext.incidentId,
                            policePhoneId = policePhoneContext.policePhoneId,
                            manifestId = plan.manifestId
                        )
                        if (existing.isEmpty()) {
                            val updatedAt = System.currentTimeMillis()
                            offlinePackageItemStatusDao.upsertAll(
                                plan.initialItemStatuses(
                                    incidentId = incidentContext.incidentId,
                                    policePhoneId = policePhoneContext.policePhoneId
                                ).map { status -> status.toOfflinePackageItemStatusEntity(updatedAt) }
                            )
                        }
                        offlinePackageDownloadScheduler.schedule(
                            OfflinePackageDownloadWorkRequest(
                                incidentId = incidentContext.incidentId,
                                policePhoneId = policePhoneContext.policePhoneId,
                                manifestId = plan.manifestId,
                                apiBaseUrl = policePhoneContext.apiBaseUrl
                            )
                        )
                    }
                )
            }
        }
    val fallbackIncidentTitle = incidentContext?.incidentId ?: "선택한 사건"
    var retryNonce by remember { mutableStateOf(0) }
    var state by remember {
        mutableStateOf(OfflinePackageUiState.loading(incidentTitle = fallbackIncidentTitle))
    }

    LaunchedEffect(loader, fallbackIncidentTitle, retryNonce) {
        state = OfflinePackageUiState.loading(incidentTitle = fallbackIncidentTitle)
        state =
            loader?.load()
                ?: OfflinePackageUiState.permissionDenied(incidentTitle = fallbackIncidentTitle)
    }

    OfflinePackageScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onOpenSearchMap = { navController.navigateToSingleTop(PolicePhoneRoute.SearchMap) },
        onRetryFailedItems = { retryNonce += 1 }
    )
}

@Composable
private fun AuthBootstrapRoute(
    incidentSessionState: IncidentSessionState,
    navController: NavHostController
) {
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
            incidentSessionState.activatePolicePhoneContext(config.toPolicePhoneContext())
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
    val policePhoneContext = incidentSessionState.policePhoneContext
    val policePhoneLabel = policePhoneContext?.policePhoneId ?: "관리 폴리폰"
    val loader = remember(policePhoneContext?.apiBaseUrl, policePhoneLabel) {
        IncidentListStateLoader(
            repository =
            IncidentReadRepository(
                apiClient =
                SuriMapApiClient(
                    baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                )
            ),
            policePhoneLabel = policePhoneLabel
        )
    }
    var refreshNonce by remember { mutableStateOf(0) }
    var state by remember {
        mutableStateOf(IncidentListUiState.loading(policePhoneLabel = policePhoneLabel))
    }

    LaunchedEffect(refreshNonce, incidentClosed, loader, policePhoneLabel) {
        state = IncidentListUiState.loading(policePhoneLabel = policePhoneLabel)
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

private fun ManagedPolicePhoneConfig.toPolicePhoneContext(): PolicePhoneContext {
    return PolicePhoneContext(
        policePhoneId = requireNotNull(policePhoneId),
        apiBaseUrl = apiBaseUrl,
        tileBaseUrl = tileBaseUrl,
        objectStorageBaseUrl = objectStorageBaseUrl,
        allowedHosts = allowedHosts
    )
}

private fun OfflinePackageInstallationEntity.toOfflinePackageInstallationStatus(): OfflinePackageInstallationStatus =
    OfflinePackageInstallationStatus(
        incidentId = incidentId,
        policePhoneId = policePhoneId,
        manifestId = manifestId,
        manifestVersion = manifestVersion,
        status = status,
        totalItems = totalItems,
        completedItems = completedItems,
        failedItems = failedItems,
        version = version,
        readyForOfflineUse = readyForOfflineUse
    )

private fun OfflinePackageItemStatusEntity.toOfflinePackageItemStatus(): OfflinePackageItemStatus =
    OfflinePackageItemStatus(
        incidentId = incidentId,
        policePhoneId = policePhoneId,
        manifestId = manifestId,
        manifestVersion = manifestVersion,
        itemKey = itemKey,
        itemType = itemType,
        status = status,
        sourceVersion = sourceVersion,
        sourceHash = sourceHash,
        bytesTotal = bytesTotal,
        bytesDownloaded = bytesDownloaded
    )

private fun IncidentContext?.toSearchMapSessionContext(policePhoneContext: PolicePhoneContext?): SearchMapSessionContext =
    SearchMapSessionContext(
        incidentId = this?.incidentId,
        currentOpId = this?.currentOpId,
        currentDutyShiftId = this?.currentDutyShiftId,
        policePhoneId = policePhoneContext?.policePhoneId
    )

private fun IncidentContext?.toHandoverSessionContext(policePhoneContext: PolicePhoneContext?): HandoverSessionContext =
    HandoverSessionContext(
        incidentId = this?.incidentId,
        opId = this?.currentOpId,
        dutyShiftId = this?.currentDutyShiftId,
        policePhoneId = policePhoneContext?.policePhoneId
    )

private fun SearchMapSessionContext.toSearchPathWriteContext(): SearchPathWriteContext =
    SearchPathWriteContext(
        incidentId = incidentId,
        opId = currentOpId,
        policePhoneId = policePhoneId
    )

private fun SearchMapSessionContext.toMarkerWriteContext(): MarkerWriteContext =
    MarkerWriteContext(
        incidentId = incidentId,
        opId = currentOpId,
        policePhoneId = policePhoneId
    )

private fun HandoverSessionContext.toHandoverWriteContext(): HandoverWriteContext =
    HandoverWriteContext(
        incidentId = incidentId,
        opId = opId,
        dutyShiftId = dutyShiftId,
        policePhoneId = policePhoneId
    )

private fun HandoverMemoUiState.withContext(context: HandoverSessionContext): HandoverMemoUiState =
    copy(
        subtitle = context.handoverMemoSubtitle(),
        memoText = "",
        offline = true
    ).withTargetContext(context)

private fun HandoverMemoUiState.withTargetContext(context: HandoverSessionContext): HandoverMemoUiState {
    val target = selectedTarget.toHandoverTargetContext(context)
    return copy(
        selectedTargetTitle = target.title,
        selectedTargetSubtitle = target.subtitle
    )
}

private fun HandoverMemoUiState.toHandoverMemoInput(context: HandoverSessionContext): HandoverMemoInput {
    val target = selectedTarget.toHandoverTargetContext(context)
    return HandoverMemoInput(
        memoTargetType = target.apiType,
        memoTargetId = target.targetId,
        content = memoText
    )
}

private fun HandoverSessionContext.handoverMemoSubtitle(): String {
    val incident = incidentId?.takeIf(String::isNotBlank) ?: "사건 미선택"
    val op = opId?.takeIf(String::isNotBlank) ?: "OP 미선택"
    val dutyShift = dutyShiftId?.takeIf(String::isNotBlank) ?: "DutyShift 미선택"
    return "$incident · $op · $dutyShift"
}

private fun HandoverMemoTarget.toHandoverTargetContext(context: HandoverSessionContext): HandoverTargetContext =
    when (this) {
        HandoverMemoTarget.OP ->
            HandoverTargetContext(
                apiType = "OPERATIONAL_PERIOD",
                targetId = context.opId?.takeIf(String::isNotBlank),
                title = context.opId?.let { "현재 OP $it" } ?: "OP 미선택",
                subtitle = "활성 운영 기간"
            )

        HandoverMemoTarget.Path ->
            HandoverTargetContext(
                apiType = "SEARCH_PATH",
                targetId = null,
                title = "현재 OP 경로",
                subtitle = context.opId?.let { "OP $it 기준 경로" } ?: "OP 기준 경로"
            )

        HandoverMemoTarget.Area ->
            HandoverTargetContext(
                apiType = "SEARCH_AREA",
                targetId = null,
                title = "현재 OP 구역",
                subtitle = context.opId?.let { "OP $it 기준 구역" } ?: "OP 기준 구역"
            )

        HandoverMemoTarget.DutyShift ->
            HandoverTargetContext(
                apiType = "DUTY_SHIFT",
                targetId = context.dutyShiftId?.takeIf(String::isNotBlank),
                title = context.dutyShiftId?.let { "현재 근무 $it" } ?: "근무 미선택",
                subtitle = "교대 인수인계"
            )

        HandoverMemoTarget.Marker ->
            HandoverTargetContext(
                apiType = "MARKER",
                targetId = null,
                title = "현재 OP 마커",
                subtitle = context.opId?.let { "OP $it 기준 마커" } ?: "OP 기준 마커"
            )
    }

private data class HandoverTargetContext(
    val apiType: String,
    val targetId: String?,
    val title: String,
    val subtitle: String
)

private fun com.surimap.feature.search.ui.SearchMapUiState.activeSearchPathId(): String? =
    layers.firstOrNull { layer -> layer.kind == SearchLayerKind.Path && layer.highlighted }?.overlayId

private fun SearchMapUiState.markerCreationLocation(): MarkerLocation? =
    viewportBounds?.let { bounds ->
        MarkerLocation(
            lon = (bounds.west + bounds.east) / 2.0,
            lat = (bounds.south + bounds.north) / 2.0
        )
    }

private fun MarkerCreateSheetUiState.toMarkerUpsertInput(location: MarkerLocation?): MarkerUpsertInput =
    MarkerUpsertInput(
        type = selectedType.apiValue,
        location = location,
        supportRequestType = supportRequestType?.apiValue,
        memo = memo
    )

private fun MarkerCreateSheetUiState.withLocation(location: MarkerLocation?): MarkerCreateSheetUiState =
    copy(
        locationLabel =
        if (location == null) {
            "지도 기준 위치 확인 필요"
        } else {
            String.format(Locale.US, "지도 중심 · %.6f, %.6f", location.lat, location.lon)
        }
    )

private fun PolicePhoneContext?.toMapLibreRuntimeMapState(): MapLibreRuntimeMapState =
    MapLibreRuntimeMapState(
        apiBaseUrl = this?.tileBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL,
        policePhoneId = this?.policePhoneId
    )

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
