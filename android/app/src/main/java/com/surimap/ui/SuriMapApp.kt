package com.surimap.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.WorkManager
import com.surimap.BuildConfig
import com.surimap.core.database.OfflinePackageInstallationEntity
import com.surimap.core.database.OfflinePackageItemStatusEntity
import com.surimap.core.database.SuriMapDatabaseProvider
import com.surimap.core.fcm.FcmRegistrationCoordinator
import com.surimap.core.fcm.FcmTokenProvider
import com.surimap.core.fcm.FirebaseMessagingTokenProvider
import com.surimap.core.fcm.NoFcmTokenProvider
import com.surimap.core.fcm.SharedPreferencesFcmRegistrationStateStore
import com.surimap.core.incident.IncidentReadRepository
import com.surimap.core.location.AndroidLocationUpdates
import com.surimap.core.map.MapLibreRuntimeMapState
import com.surimap.core.marker.MarkerRepository
import com.surimap.core.network.AuthPhoneApiClient
import com.surimap.core.network.OutboxRequeueNetworkRequest
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.network.SyncApiClient
import com.surimap.core.offline.OfflinePackageDownloadScheduler
import com.surimap.core.offline.OfflinePackageDownloadWorkRequest
import com.surimap.core.offline.OfflinePackageInstallationStatus
import com.surimap.core.offline.OfflinePackageItemStatus
import com.surimap.core.offline.OfflinePackageManifestQuery
import com.surimap.core.offline.OfflinePackageRepository
import com.surimap.core.offline.toOfflinePackageItemStatusEntity
import com.surimap.core.operationalperiod.DutyShiftRepository
import com.surimap.core.operationalperiod.HandoverMemoRepository
import com.surimap.core.operationalperiod.OperationalPeriodReadRepository
import com.surimap.core.operationalperiod.SearchHistorySummaryReadRepository
import com.surimap.core.path.SearchPathRepository
import com.surimap.core.searcharea.SearchAreaReadRepository
import com.surimap.core.sync.ClockSyncState
import com.surimap.core.sync.OutboxReplayScheduler
import com.surimap.core.sync.OutboxReplayWorkRequest
import com.surimap.core.sync.RoomOutboxRequeue
import com.surimap.core.sync.RoomSyncClient
import com.surimap.core.sync.SchedulingSyncClient
import com.surimap.feature.bootstrap.data.AndroidManagedConfigurationReader
import com.surimap.feature.bootstrap.data.AuthBootstrapCoordinator
import com.surimap.feature.bootstrap.data.AuthBootstrapServerCheck
import com.surimap.feature.bootstrap.data.BuildConfigAuthBootstrapCredentialsProvider
import com.surimap.feature.bootstrap.data.ManagedPolicePhoneConfig
import com.surimap.feature.bootstrap.data.NetworkPolicePhoneBootstrapServerCheck
import com.surimap.feature.bootstrap.ui.AuthBootstrapOutcome
import com.surimap.feature.bootstrap.ui.AuthBootstrapScreen
import com.surimap.feature.bootstrap.ui.AuthBootstrapUiState
import com.surimap.feature.handover.data.DutyHandoverStateLoader
import com.surimap.feature.handover.data.DutyShiftLocalRecorder
import com.surimap.feature.handover.data.DutyShiftWriteContext
import com.surimap.feature.handover.data.DutyShiftWriteResult
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
import com.surimap.feature.incidents.data.IncidentSessionContextResolver
import com.surimap.feature.incidents.ui.IncidentListScreen
import com.surimap.feature.incidents.ui.IncidentListUiState
import com.surimap.feature.marker.data.HttpObjectStorageUploader
import com.surimap.feature.marker.data.MarkerLocalRecorder
import com.surimap.feature.marker.data.MarkerDetailSessionContext
import com.surimap.feature.marker.data.MarkerDetailStateLoader
import com.surimap.feature.marker.data.MarkerLocation
import com.surimap.feature.marker.data.MarkerPhotoUiUploadCoordinator
import com.surimap.feature.marker.data.MarkerPhotoUploadPayload
import com.surimap.feature.marker.data.MarkerPhotoUploadResult
import com.surimap.feature.marker.data.MarkerUpsertInput
import com.surimap.feature.marker.data.MarkerWriteContext
import com.surimap.feature.marker.data.MarkerWriteResult
import com.surimap.feature.marker.ui.MarkerCreateBottomSheet
import com.surimap.feature.marker.ui.MarkerDetailScreen
import com.surimap.feature.marker.ui.MarkerCreateSheetUiState
import com.surimap.feature.marker.ui.MarkerDetailPhotoStatus
import com.surimap.feature.marker.ui.MarkerDetailPhotoUiState
import com.surimap.feature.marker.ui.MarkerDetailUiState
import com.surimap.feature.marker.ui.MarkerSaveStatus
import com.surimap.feature.marker.ui.MarkerType
import com.surimap.feature.marker.ui.sampleMarkerCreateSheetState
import com.surimap.feature.marker.ui.withCurrentLocation
import com.surimap.feature.marker.ui.withManualLocation
import com.surimap.feature.offline.data.OfflinePackageStateLoader
import com.surimap.feature.offline.ui.OfflinePackageScreen
import com.surimap.feature.offline.ui.OfflinePackageUiState
import com.surimap.feature.outbox.data.BlockedOutboxQuery
import com.surimap.feature.outbox.data.BlockedOutboxStateLoader
import com.surimap.feature.outbox.ui.BlockedOutboxScreen
import com.surimap.feature.outbox.ui.BlockedOutboxUiState
import com.surimap.feature.search.data.SearchMapSessionContext
import com.surimap.feature.search.data.SearchMapStateLoader
import com.surimap.feature.search.data.SearchPathGpsBatchRecorder
import com.surimap.feature.search.data.SearchPathLocalRecorder
import com.surimap.feature.search.data.SearchPathWriteContext
import com.surimap.feature.search.data.SearchPathWriteResult
import com.surimap.feature.search.data.SearchRecordingSessionState
import com.surimap.feature.search.ui.SearchLayerKind
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.feature.search.ui.SearchMapScreen
import com.surimap.feature.search.ui.SearchMapUiState
import com.surimap.ui.navigation.IncidentContext
import com.surimap.ui.navigation.IncidentSessionState
import com.surimap.ui.navigation.MarkerDetailDeepLink
import com.surimap.ui.navigation.PolicePhoneContext
import com.surimap.ui.navigation.PolicePhoneRoute
import com.surimap.ui.navigation.SearchMapDeepLink
import com.surimap.ui.navigation.accessTokenProvider
import com.surimap.ui.theme.PoliBgBase
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun SuriMapApp() {
    val navController = rememberNavController()
    val incidentSessionState = remember { IncidentSessionState() }
    val clockSyncState = remember { ClockSyncState() }
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
            FcmRegistrationEffect(policePhoneContext = incidentSessionState.policePhoneContext)
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
                        clockSyncState = clockSyncState,
                        onClearClosedOverlay = { incidentClosed = null }
                    )
                }
                composable(PolicePhoneRoute.OfflinePackage.route) {
                    OfflinePackageRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController,
                        clockSyncState = clockSyncState
                    )
                }
                composable(
                    route = SearchMapDeepLink.RoutePattern,
                    arguments =
                    listOf(
                        navArgument(SearchMapDeepLink.FocusMarkerIdArg) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    SearchMapRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController,
                        focusMarkerId = backStackEntry.arguments?.getString(SearchMapDeepLink.FocusMarkerIdArg),
                        clockSyncState = clockSyncState,
                        onOpenBlockedOutbox = {
                            blockedQueue = BlockedQueueToastState(blockedCount = 2)
                        }
                    )
                }
                composable(PolicePhoneRoute.HandoverSummary.route) {
                    HandoverSummaryRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController,
                        clockSyncState = clockSyncState
                    )
                }
                composable(PolicePhoneRoute.HandoverMemo.route) {
                    HandoverMemoRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController,
                        clockSyncState = clockSyncState,
                        onMemoSaved = { pendingSync ->
                            handoverMemoSaved = HandoverMemoSavedToastState(pendingSync = pendingSync)
                        }
                    )
                }
                composable(PolicePhoneRoute.MarkerDetail.route) {
                    MarkerDetailRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController,
                        markerId = null,
                        clockSyncState = clockSyncState
                    )
                }
                composable(
                    route = MarkerDetailDeepLink.RoutePattern,
                    arguments =
                    listOf(
                        navArgument(MarkerDetailDeepLink.MarkerIdArg) {
                            type = NavType.StringType
                        }
                    )
                ) { backStackEntry ->
                    MarkerDetailRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController,
                        markerId = backStackEntry.arguments?.getString(MarkerDetailDeepLink.MarkerIdArg),
                        clockSyncState = clockSyncState
                    )
                }
                composable(PolicePhoneRoute.BlockedOutbox.route) {
                    BlockedOutboxRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController,
                        clockSyncState = clockSyncState
                    )
                }
            }
        }
    }
}

@Composable
private fun FcmRegistrationEffect(policePhoneContext: PolicePhoneContext?) {
    val context = LocalContext.current.applicationContext
    val stateStore = remember(context) {
        SharedPreferencesFcmRegistrationStateStore(context)
    }
    val tokenProvider: FcmTokenProvider = remember {
        if (BuildConfig.SURI_MAP_FIREBASE_MESSAGING_ENABLED) {
            FirebaseMessagingTokenProvider()
        } else {
            NoFcmTokenProvider
        }
    }

    LaunchedEffect(
        policePhoneContext?.policePhoneId,
        policePhoneContext?.apiBaseUrl,
        policePhoneContext?.accessToken,
        stateStore,
        tokenProvider
    ) {
        val readyContext = policePhoneContext ?: return@LaunchedEffect
        if (readyContext.accessToken.isNullOrBlank()) {
            return@LaunchedEffect
        }
        val authClient =
            AuthPhoneApiClient(
                apiClient = SuriMapApiClient(baseUrl = readyContext.apiBaseUrl),
                accessTokenProvider = readyContext.accessTokenProvider()
            )
        FcmRegistrationCoordinator(
            firebaseMessagingEnabled = BuildConfig.SURI_MAP_FIREBASE_MESSAGING_ENABLED,
            tokenProvider = tokenProvider,
            stateStore = stateStore,
            registerToken = authClient::registerFcmToken
        ).registerCurrentToken(readyContext.policePhoneId)
    }
}

@Composable
private fun BlockedOutboxRoute(
    incidentSessionState: IncidentSessionState,
    navController: NavHostController,
    clockSyncState: ClockSyncState
) {
    val incidentContext = incidentSessionState.incidentContext
    val policePhoneContext = incidentSessionState.policePhoneContext
    val context = LocalContext.current.applicationContext
    val database = remember(context) { SuriMapDatabaseProvider.database(context) }
    val outboxDao = remember(database) { database.outboxDao() }
    val outboxRequeue = remember(outboxDao) { RoomOutboxRequeue(outboxDao) }
    val outboxReplayScheduler = remember(context) {
        OutboxReplayScheduler(WorkManager.getInstance(context))
    }
    val apiBaseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
    val accessTokenProvider = policePhoneContext.accessTokenProvider()
    val requeueClient =
        remember(apiBaseUrl, policePhoneContext?.accessToken) {
            SyncApiClient(
                apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
                accessTokenProvider = accessTokenProvider
            )
        }
    val loader = remember(outboxDao) {
        BlockedOutboxStateLoader(
            rowsByIncident = outboxDao::findByIncidentId,
            statusSummary = outboxDao::statusSummary
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var refreshNonce by remember { mutableStateOf(0) }
    var state by remember {
        mutableStateOf(
            BlockedOutboxUiState(
                title = "미전송 진단",
                subtitle = "사건 선택 필요",
                blockedItems = emptyList(),
                pendingSummary = null
            )
        )
    }

    LaunchedEffect(incidentContext?.incidentId, policePhoneContext?.policePhoneId, refreshNonce, loader) {
        state =
            loader.load(
                BlockedOutboxQuery(
                    incidentId = incidentContext?.incidentId,
                    policePhoneId = policePhoneContext?.policePhoneId
                )
            )
    }

    BlockedOutboxScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onOpenSupportGuide = {},
        onRetry = { item ->
            coroutineScope.launch {
                val row = outboxDao.findByOperationId(item.operationId) ?: return@launch
                val incidentId = row.incidentId?.takeIf(String::isNotBlank) ?: return@launch
                clockSyncState.syncClockForIncident(incidentId, policePhoneContext)
                val clockSnapshot = clockSyncState.snapshot()
                runCatching {
                    requeueClient.requeue(
                        OutboxRequeueNetworkRequest(
                            operationId = row.operationId,
                            incidentId = incidentId,
                            reason = "USER_RETRY",
                            clientTs = java.time.Instant.now().toString(),
                            clockOffsetMs = clockSnapshot.clockOffsetMs ?: 0L,
                            clockSyncedAt =
                            clockSnapshot.clockSyncedAt
                                ?.toString()
                                ?: java.time.Instant.now().toString(),
                            attemptCount = row.attemptCount,
                            policePhoneId = row.policePhoneId
                        )
                    )
                }
                outboxRequeue.requeue(operationId = row.operationId, reason = "USER_RETRY")
                outboxReplayScheduler.schedule(
                    OutboxReplayWorkRequest(
                        incidentId = incidentId,
                        policePhoneId = row.policePhoneId,
                        apiBaseUrl = apiBaseUrl,
                        accessToken = policePhoneContext?.accessToken
                    )
                )
                refreshNonce += 1
            }
        }
    )
}

@Composable
private fun HandoverSummaryRoute(
    incidentSessionState: IncidentSessionState,
    navController: NavHostController,
    clockSyncState: ClockSyncState
) {
    val incidentContext = incidentSessionState.incidentContext
    val policePhoneContext = incidentSessionState.policePhoneContext
    val sessionContext = incidentContext.toHandoverSessionContext(policePhoneContext)
    val context = LocalContext.current.applicationContext
    val database = remember(context) { SuriMapDatabaseProvider.database(context) }
    val outboxReplayScheduler = remember(context) {
        OutboxReplayScheduler(WorkManager.getInstance(context))
    }
    val apiBaseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
    val accessTokenProvider = policePhoneContext.accessTokenProvider()
    val syncClient =
        remember(database, outboxReplayScheduler, apiBaseUrl, policePhoneContext?.accessToken) {
            SchedulingSyncClient(
                delegate = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                scheduleReplay = outboxReplayScheduler::schedule,
                apiBaseUrl = apiBaseUrl,
                accessToken = policePhoneContext?.accessToken
            )
        }
    val dutyShiftRecorder = remember(syncClient, clockSyncState) {
        DutyShiftLocalRecorder(
            syncClient = syncClient,
            clockOffsetMs = clockSyncState::clockOffsetMs,
            clockSyncedAt = clockSyncState::clockSyncedAt
        )
    }
    val loader =
        remember(apiBaseUrl, policePhoneContext?.accessToken) {
            DutyHandoverStateLoader(
                handoverMemos = { query ->
                    HandoverMemoRepository(
                        apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
                        accessTokenProvider = accessTokenProvider
                    ).listHandoverMemos(query)
                },
                searchHistorySummaries = { operationalPeriodId, query ->
                    SearchHistorySummaryReadRepository(
                        apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
                        accessTokenProvider = accessTokenProvider
                    ).list(operationalPeriodId, query)
                }
            )
        }
    var handoverState by remember(loader, sessionContext) {
        mutableStateOf(loader.fallback(sessionContext))
    }
    var endingDutyShift by remember(sessionContext) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(loader, sessionContext) {
        handoverState = loader.fallback(sessionContext)
        handoverState = loader.load(sessionContext)
    }
    LaunchedEffect(
        sessionContext.incidentId,
        sessionContext.policePhoneId,
        policePhoneContext?.apiBaseUrl,
        policePhoneContext?.accessToken
    ) {
        clockSyncState.syncClockForIncident(sessionContext.incidentId, policePhoneContext)
    }

    DutyHandoverScreen(
        state =
        handoverState.copy(
            canEndDutyShift = !sessionContext.dutyShiftId.isNullOrBlank(),
            endingDutyShift = endingDutyShift
        ),
        onBack = { navController.popBackStack() },
        onWriteMemo = { navController.navigateToSingleTop(PolicePhoneRoute.HandoverMemo) },
        onOpenSearch = { navController.navigateToSingleTop(PolicePhoneRoute.SearchMap) },
        onEndDutyShift = {
            coroutineScope.launch {
                if (endingDutyShift) {
                    return@launch
                }
                endingDutyShift = true
                clockSyncState.syncClockForIncident(sessionContext.incidentId, policePhoneContext)
                when (
                    dutyShiftRecorder.end(
                        context = incidentContext.toDutyShiftWriteContext(policePhoneContext)
                    )
                ) {
                    DutyShiftWriteResult.Blocked -> {
                        endingDutyShift = false
                    }

                    is DutyShiftWriteResult.Enqueued -> {
                        incidentContext?.let {
                            incidentSessionState.activateIncidentContext(it.copy(currentDutyShiftId = null))
                        }
                        endingDutyShift = false
                        navController.navigateToSingleTop(PolicePhoneRoute.IncidentList)
                    }
                }
            }
        }
    )
}

@Composable
private fun HandoverMemoRoute(
    incidentSessionState: IncidentSessionState,
    navController: NavHostController,
    clockSyncState: ClockSyncState,
    onMemoSaved: (Boolean) -> Unit
) {
    val incidentContext = incidentSessionState.incidentContext
    val policePhoneContext = incidentSessionState.policePhoneContext
    val sessionContext = incidentContext.toHandoverSessionContext(policePhoneContext)
    val context = LocalContext.current.applicationContext
    val database = remember(context) { SuriMapDatabaseProvider.database(context) }
    val outboxReplayScheduler = remember(context) {
        OutboxReplayScheduler(WorkManager.getInstance(context))
    }
    val syncClient =
        remember(database, outboxReplayScheduler, policePhoneContext?.apiBaseUrl, policePhoneContext?.accessToken) {
            SchedulingSyncClient(
                delegate = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                scheduleReplay = outboxReplayScheduler::schedule,
                apiBaseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL,
                accessToken = policePhoneContext?.accessToken
            )
        }
    val recorder = remember(syncClient, clockSyncState) {
        HandoverMemoLocalRecorder(
            syncClient = syncClient,
            clockOffsetMs = clockSyncState::clockOffsetMs,
            clockSyncedAt = clockSyncState::clockSyncedAt
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var memoState by remember(sessionContext) {
        mutableStateOf(HandoverMemoUiState.default().withContext(sessionContext))
    }

    LaunchedEffect(
        sessionContext.incidentId,
        policePhoneContext?.policePhoneId,
        policePhoneContext?.apiBaseUrl,
        policePhoneContext?.accessToken
    ) {
        clockSyncState.syncClockForIncident(sessionContext.incidentId, policePhoneContext)
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
                clockSyncState.syncClockForIncident(sessionContext.incidentId, policePhoneContext)
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
    focusMarkerId: String? = null,
    clockSyncState: ClockSyncState,
    onOpenBlockedOutbox: () -> Unit
) {
    val incidentContext = incidentSessionState.incidentContext
    val policePhoneContext = incidentSessionState.policePhoneContext
    val context = LocalContext.current.applicationContext
    val database = remember(context) { SuriMapDatabaseProvider.database(context) }
    val outboxDao = remember(database) { database.outboxDao() }
    val sessionContext = incidentContext.toSearchMapSessionContext(policePhoneContext)
    val accessTokenProvider = policePhoneContext.accessTokenProvider()
    val outboxReplayScheduler = remember(context) {
        OutboxReplayScheduler(WorkManager.getInstance(context))
    }
    val syncClient =
        remember(database, outboxReplayScheduler, policePhoneContext?.apiBaseUrl, policePhoneContext?.accessToken) {
            SchedulingSyncClient(
                delegate = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                scheduleReplay = outboxReplayScheduler::schedule,
                apiBaseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL,
                accessToken = policePhoneContext?.accessToken
            )
        }
    val searchPathRecorder = remember(syncClient, clockSyncState) {
        SearchPathLocalRecorder(
            syncClient = syncClient,
            clockOffsetMs = clockSyncState::clockOffsetMs,
            clockSyncedAt = clockSyncState::clockSyncedAt
        )
    }
    val gpsBatchRecorder = remember(searchPathRecorder) {
        SearchPathGpsBatchRecorder(searchPathRecorder)
    }
    val locationUpdates = remember(context) {
        AndroidLocationUpdates(context)
    }
    val markerRecorder = remember(syncClient, database, clockSyncState) {
        MarkerLocalRecorder(
            syncClient = syncClient,
            localMarkerDao = database.localMarkerDao(),
            clockOffsetMs = clockSyncState::clockOffsetMs,
            clockSyncedAt = clockSyncState::clockSyncedAt
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val loader =
        remember(policePhoneContext?.apiBaseUrl, policePhoneContext?.accessToken, database, outboxDao) {
            SearchMapStateLoader(
                incidentDetail = { incidentId ->
                    IncidentReadRepository(
                        apiClient =
                        SuriMapApiClient(
                            baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                        ),
                        accessTokenProvider = accessTokenProvider
                    ).detail(incidentId)
                },
                overallSearchArea = { incidentId ->
                    SearchAreaReadRepository(
                        apiClient =
                        SuriMapApiClient(
                            baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                        ),
                        accessTokenProvider = accessTokenProvider
                    ).activeOverall(incidentId)
                },
                opSearchAreas = { incidentId, opId ->
                    SearchAreaReadRepository(
                        apiClient =
                        SuriMapApiClient(
                            baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                        ),
                        accessTokenProvider = accessTokenProvider
                    ).list(incidentId = incidentId, opId = opId, status = "ACTIVE")
                },
                searchPaths = { query ->
                    SearchPathRepository(
                        apiClient =
                        SuriMapApiClient(
                            baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                        ),
                        accessTokenProvider = accessTokenProvider
                    ).listSearchPaths(query)
                },
                liveMarkers = { query ->
                    MarkerRepository(
                        apiClient =
                        SuriMapApiClient(
                            baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                        ),
                        accessTokenProvider = accessTokenProvider
                    ).listMarkers(query)
                },
                initialMarkers = { incidentId, policePhoneId ->
                    OfflinePackageRepository(
                        apiClient =
                        SuriMapApiClient(
                            baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                        ),
                        accessTokenProvider = accessTokenProvider
                    ).manifest(
                        OfflinePackageManifestQuery(
                            incidentId = incidentId,
                            policePhoneId = policePhoneId
                        )
                    )
                },
                outboxSummary = { incidentId, policePhoneId ->
                    outboxDao.statusSummary(incidentId = incidentId, policePhoneId = policePhoneId)
                },
                pendingMarkers = { incidentId, policePhoneId ->
                    database.localMarkerDao().findPendingByIncidentAndPolicePhone(incidentId, policePhoneId)
                }
            )
        }
    var searchMapState by remember {
        mutableStateOf(SearchMapStateLoader().fallbackForRemember(sessionContext))
    }
    var recordingSession by remember(
        sessionContext.incidentId,
        sessionContext.currentOpId,
        sessionContext.policePhoneId
    ) {
        mutableStateOf(SearchRecordingSessionState())
    }
    var elapsedTickerNowMs by remember(
        sessionContext.incidentId,
        sessionContext.currentOpId,
        sessionContext.policePhoneId
    ) {
        mutableStateOf(System.currentTimeMillis())
    }
    var bottomPanelExpanded by remember { mutableStateOf(false) }
    var mapOverlaysVisible by remember { mutableStateOf(true) }
    var markerSheetOpen by remember { mutableStateOf(false) }
    var markerSheetState by remember { mutableStateOf(sampleMarkerCreateSheetState()) }

    LaunchedEffect(
        sessionContext.incidentId,
        sessionContext.policePhoneId,
        policePhoneContext?.apiBaseUrl,
        policePhoneContext?.accessToken
    ) {
        clockSyncState.syncClockForIncident(sessionContext.incidentId, policePhoneContext)
    }

    LaunchedEffect(loader, sessionContext, focusMarkerId) {
        searchMapState = loader.fallback(sessionContext).withFocusedMarker(focusMarkerId)
        val incidentId = sessionContext.incidentId?.takeIf(String::isNotBlank)
        val policePhoneId = sessionContext.policePhoneId?.takeIf(String::isNotBlank)
        if (incidentId == null || policePhoneId == null) {
            searchMapState = loader.load(sessionContext).withFocusedMarker(focusMarkerId)
            return@LaunchedEffect
        }
        outboxDao.observeStatusSummary(incidentId = incidentId, policePhoneId = policePhoneId).collect {
            searchMapState = loader.load(sessionContext).withFocusedMarker(focusMarkerId)
        }
    }

    val serverActiveSearchPathId = searchMapState.activeSearchPathId()
    val displayedLifecycle = recordingSession.displayedLifecycle(
        baseLifecycleStatus = searchMapState.lifecycleStatus,
        serverActiveSearchPathId = serverActiveSearchPathId
    )
    val activeSearchPathId = recordingSession.effectiveSearchPathId(serverActiveSearchPathId)
    val displayedSearchMapState =
        searchMapState.copy(
            lifecycleStatus = displayedLifecycle,
            elapsedLabel = recordingSession.elapsedLabel(elapsedTickerNowMs),
            bottomPanelExpanded = bottomPanelExpanded,
            mapOverlaysVisible = mapOverlaysVisible
        )

    LaunchedEffect(displayedLifecycle, activeSearchPathId) {
        if (displayedLifecycle == SearchLifecycleStatus.Active && activeSearchPathId != null) {
            val now = System.currentTimeMillis()
            recordingSession = recordingSession.ensureActiveStarted(now)
            elapsedTickerNowMs = now
            while (true) {
                delay(1_000L)
                elapsedTickerNowMs = System.currentTimeMillis()
            }
        }
    }

    DisposableEffect(
        locationUpdates,
        activeSearchPathId,
        displayedLifecycle,
        sessionContext
    ) {
        if (displayedLifecycle != SearchLifecycleStatus.Active || activeSearchPathId == null) {
            onDispose {}
        } else {
            val handle =
                locationUpdates.start { fix ->
                    coroutineScope.launch {
                        gpsBatchRecorder.recordFix(
                            context = sessionContext.toSearchPathWriteContext(),
                            searchPathId = activeSearchPathId,
                            fix = fix
                        )
                    }
                }
            onDispose {
                handle.stop()
                coroutineScope.launch {
                    gpsBatchRecorder.flush(
                        context = sessionContext.toSearchPathWriteContext(),
                        searchPathId = activeSearchPathId
                    )
                    gpsBatchRecorder.clear()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SearchMapScreen(
            state = displayedSearchMapState,
            mapState = policePhoneContext.toMapLibreRuntimeMapState(),
            onBack = { navController.popBackStack() },
            onPrimaryLifecycleAction = {
                coroutineScope.launch {
                    val now = System.currentTimeMillis()
                    when (displayedLifecycle) {
                        SearchLifecycleStatus.Stopped -> {
                            val result = searchPathRecorder.start(sessionContext.toSearchPathWriteContext())
                            if (result is SearchPathWriteResult.Enqueued) {
                                recordingSession = recordingSession.start(result.entityId, now)
                                elapsedTickerNowMs = now
                            }
                        }
                        SearchLifecycleStatus.Active -> {
                            gpsBatchRecorder.flush(
                                context = sessionContext.toSearchPathWriteContext(),
                                searchPathId = activeSearchPathId
                            )
                            gpsBatchRecorder.clear()
                            recordingSession = recordingSession.pause(now)
                            elapsedTickerNowMs = now
                        }
                        SearchLifecycleStatus.Paused -> {
                            recordingSession = recordingSession.resume(now)
                            elapsedTickerNowMs = now
                        }
                        SearchLifecycleStatus.OpRequired,
                        SearchLifecycleStatus.OpTransition -> {
                            searchMapState = loader.load(sessionContext).withFocusedMarker(focusMarkerId)
                        }
                    }
                }
            },
            onStopSearch = {
                coroutineScope.launch {
                    val now = System.currentTimeMillis()
                    val pathId = activeSearchPathId
                    gpsBatchRecorder.flush(
                        context = sessionContext.toSearchPathWriteContext(),
                        searchPathId = pathId
                    )
                    gpsBatchRecorder.clear()
                    searchPathRecorder.end(
                        context = sessionContext.toSearchPathWriteContext(),
                        searchPathId = pathId
                    )
                    recordingSession = recordingSession.stop(now)
                    elapsedTickerNowMs = now
                }
            },
            onCreateMarker = {
                markerSheetState =
                    sampleMarkerCreateSheetState()
                        .withCurrentLocation(displayedSearchMapState.markerCreationLocation())
                markerSheetOpen = true
            },
            onOpenHandover = { navController.navigateToSingleTop(PolicePhoneRoute.HandoverSummary) },
            onOpenBlockedOutbox = onOpenBlockedOutbox,
            onDismissIncidentAlert = {
                searchMapState = searchMapState.copy(incidentAlert = null)
            },
            onOpenIncidentAlertMarker = { markerId ->
                searchMapState = searchMapState.withFocusedMarker(markerId)
                navController.navigateToSingleTop(SearchMapDeepLink.markerFocusRoute(markerId))
            },
            onOpenFocusedMarkerDetail = { markerId ->
                navController.navigateToSingleTop(MarkerDetailDeepLink.route(markerId))
            },
            onToggleBottomPanel = { bottomPanelExpanded = !bottomPanelExpanded },
            onToggleMapOverlays = { mapOverlaysVisible = !mapOverlaysVisible }
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
                onAdjustLocation = {
                    markerSheetState =
                        markerSheetState.withManualLocation(displayedSearchMapState.markerCreationLocation())
                },
                onSave = {
                    coroutineScope.launch {
                        markerSheetState = markerSheetState.copy(saveStatus = MarkerSaveStatus.Saving)
                        when (
                            markerRecorder.createMarker(
                                context = sessionContext.toMarkerWriteContext(),
                                input = markerSheetState.toMarkerUpsertInput()
                            )
                        ) {
                            MarkerWriteResult.Blocked -> {
                                markerSheetState = markerSheetState.copy(saveStatus = MarkerSaveStatus.Failed)
                            }
                            is MarkerWriteResult.Enqueued -> {
                                markerSheetState = markerSheetState.copy(saveStatus = MarkerSaveStatus.PendingOutbox)
                                markerSheetOpen = false
                                searchMapState = loader.load(sessionContext).withFocusedMarker(focusMarkerId)
                            }
                        }
                    }
                },
                onRetryPhoto = {}
            )
        }
    }
}

@Composable
private fun MarkerDetailRoute(
    incidentSessionState: IncidentSessionState,
    navController: NavHostController,
    markerId: String?,
    clockSyncState: ClockSyncState
) {
    val incidentContext = incidentSessionState.incidentContext
    val policePhoneContext = incidentSessionState.policePhoneContext
    val sessionContext = incidentContext.toMarkerDetailSessionContext(policePhoneContext, markerId)
    val context = LocalContext.current.applicationContext
    val database = remember(context) { SuriMapDatabaseProvider.database(context) }
    val outboxReplayScheduler = remember(context) {
        OutboxReplayScheduler(WorkManager.getInstance(context))
    }
    val apiBaseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
    val accessTokenProvider = policePhoneContext.accessTokenProvider()
    val syncClient =
        remember(database, outboxReplayScheduler, apiBaseUrl, policePhoneContext?.accessToken) {
            SchedulingSyncClient(
                delegate = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                scheduleReplay = outboxReplayScheduler::schedule,
                apiBaseUrl = apiBaseUrl,
                accessToken = policePhoneContext?.accessToken
            )
        }
    val markerRecorder = remember(syncClient, clockSyncState) {
        MarkerLocalRecorder(
            syncClient = syncClient,
            clockOffsetMs = clockSyncState::clockOffsetMs,
            clockSyncedAt = clockSyncState::clockSyncedAt
        )
    }
    val photoUploadCoordinator =
        remember(syncClient, apiBaseUrl, policePhoneContext?.accessToken, clockSyncState) {
            MarkerPhotoUiUploadCoordinator(
                syncClient = syncClient,
                apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
                accessTokenProvider = accessTokenProvider,
                uploader = HttpObjectStorageUploader(),
                clockOffsetMs = clockSyncState::clockOffsetMs,
                clockSyncedAt = clockSyncState::clockSyncedAt
            )
        }
    val loader =
        remember(apiBaseUrl, policePhoneContext?.accessToken) {
            MarkerDetailStateLoader(
                markerRead = { query ->
                    MarkerRepository(
                        apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
                        accessTokenProvider = accessTokenProvider
                    ).listMarkers(query)
                }
            )
    }
    val coroutineScope = rememberCoroutineScope()
    var markerDetailState by remember(markerId) {
        mutableStateOf(MarkerDetailUiState.loading(markerId ?: "marker-id-missing"))
    }
    var retryPhotoId by remember(markerId) { mutableStateOf<String?>(null) }
    var photoUriById by remember(markerId) { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    var pendingCameraPhotoUri by remember(markerId) { mutableStateOf<Uri?>(null) }
    fun beginPhotoUpload(uri: Uri, existingPhotoId: String? = null) {
        val current = markerDetailState
        if (!current.canEdit) {
            return
        }
        val localPhotoId = existingPhotoId ?: "local-photo-${System.currentTimeMillis()}"
        val label = existingPhotoId?.let { id ->
            current.photos.firstOrNull { photo -> photo.photoId == id }?.label
        } ?: "사진 ${current.photos.size + 1}"
        photoUriById = photoUriById + (localPhotoId to uri)
        markerDetailState =
            current.upsertPhoto(
                MarkerDetailPhotoUiState(
                    photoId = localPhotoId,
                    label = label,
                    status = MarkerDetailPhotoStatus.Attaching,
                    progress = 0.2f
                )
            )
        coroutineScope.launch {
            clockSyncState.syncClockForIncident(sessionContext.incidentId, policePhoneContext)
            val payload = context.markerPhotoUploadPayload(markerId = current.markerId, uri = uri)
            if (payload == null) {
                markerDetailState = markerDetailState.markPhotoFailed(localPhotoId)
                return@launch
            }
            markerDetailState = markerDetailState.markPhotoAttaching(localPhotoId, progress = 0.55f)
            markerDetailState =
                when (
                    photoUploadCoordinator.upload(
                        context = sessionContext.toMarkerWriteContext(),
                        payload = payload
                    )
                ) {
                    MarkerPhotoUploadResult.Blocked,
                    is MarkerPhotoUploadResult.UploadFailed -> markerDetailState.markPhotoFailed(localPhotoId)

                    is MarkerPhotoUploadResult.UploadUrlEnqueued,
                    is MarkerPhotoUploadResult.AttachedEnqueued -> markerDetailState.markPhotoAttaching(localPhotoId, progress = 0.9f)
                }
        }
    }
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val targetPhotoId = retryPhotoId
            retryPhotoId = null
            uri?.let { selected -> beginPhotoUpload(selected, targetPhotoId) }
        }
    val photoCapture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
            val capturedUri = pendingCameraPhotoUri
            pendingCameraPhotoUri = null
            if (captured && capturedUri != null) {
                beginPhotoUpload(capturedUri)
            }
        }

    LaunchedEffect(
        sessionContext.incidentId,
        sessionContext.policePhoneId,
        policePhoneContext?.apiBaseUrl,
        policePhoneContext?.accessToken
    ) {
        clockSyncState.syncClockForIncident(sessionContext.incidentId, policePhoneContext)
    }
    LaunchedEffect(loader, sessionContext) {
        markerDetailState = MarkerDetailUiState.loading(sessionContext.markerId ?: "marker-id-missing")
        markerDetailState = loader.load(sessionContext)
    }

    MarkerDetailScreen(
        state = markerDetailState,
        onBack = { navController.popBackStack() },
        onMemoChange = { memo ->
            if (markerDetailState.canEdit) {
                markerDetailState = markerDetailState.copy(memo = memo, mutationStatus = MarkerSaveStatus.Editing)
            }
        },
        onSave = {
            val current = markerDetailState
            coroutineScope.launch {
                markerDetailState = current.copy(mutationStatus = MarkerSaveStatus.Saving)
                val input = markerDetailState.toMarkerUpsertInput()
                markerDetailState =
                    when (
                        markerRecorder.updateMarker(
                            context = sessionContext.toMarkerWriteContext(),
                            markerId = current.markerId,
                            version = current.version,
                            input = input
                        )
                    ) {
                        MarkerWriteResult.Blocked -> current.copy(mutationStatus = MarkerSaveStatus.Failed)
                        is MarkerWriteResult.Enqueued -> current.copy(mutationStatus = MarkerSaveStatus.PendingOutbox)
                    }
            }
        },
        onRequestDelete = {
            if (markerDetailState.canDelete) {
                markerDetailState = markerDetailState.copy(showDeleteConfirm = true)
            }
        },
        onDismissDelete = {
            markerDetailState = markerDetailState.copy(showDeleteConfirm = false)
        },
        onConfirmDelete = {
            val current = markerDetailState.copy(showDeleteConfirm = false)
            coroutineScope.launch {
                markerDetailState = current.copy(mutationStatus = MarkerSaveStatus.Saving)
                markerDetailState =
                    when (
                        markerRecorder.deleteMarker(
                            context = sessionContext.toMarkerWriteContext(),
                            markerId = current.markerId,
                            version = current.version,
                            reason = "field_deleted"
                        )
                    ) {
                        MarkerWriteResult.Blocked -> current.copy(mutationStatus = MarkerSaveStatus.Failed)
                        is MarkerWriteResult.Enqueued -> current.copy(mutationStatus = MarkerSaveStatus.PendingOutbox)
                    }
            }
        },
        onCapturePhoto = {
            retryPhotoId = null
            context.createMarkerPhotoCaptureUri(markerDetailState.markerId)?.let { uri ->
                pendingCameraPhotoUri = uri
                photoCapture.launch(uri)
            }
        },
        onPickPhoto = {
            retryPhotoId = null
            photoPicker.launch("image/*")
        },
        onRetryPhoto = { photo ->
            val uri = photoUriById[photo.photoId]
            if (uri != null) {
                beginPhotoUpload(uri, existingPhotoId = photo.photoId)
            } else {
                retryPhotoId = photo.photoId
                photoPicker.launch("image/*")
            }
        }
    )
}

@Composable
private fun OfflinePackageRoute(
    incidentSessionState: IncidentSessionState,
    navController: NavHostController,
    clockSyncState: ClockSyncState
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
    val installationRefreshSignal by remember(
        incidentContext?.incidentId,
        policePhoneContext?.policePhoneId,
        offlinePackageInstallationDao
    ) {
        if (incidentContext == null || policePhoneContext == null) {
            flowOf<OfflinePackageInstallationEntity?>(null)
        } else {
            offlinePackageInstallationDao.observe(
                incidentId = incidentContext.incidentId,
                policePhoneId = policePhoneContext.policePhoneId
            )
        }
    }.collectAsState(initial = null)
    val accessTokenProvider = policePhoneContext.accessTokenProvider()
    LaunchedEffect(
        incidentContext?.incidentId,
        policePhoneContext?.policePhoneId,
        policePhoneContext?.apiBaseUrl,
        policePhoneContext?.accessToken
    ) {
        clockSyncState.syncClockForIncident(incidentContext?.incidentId, policePhoneContext)
    }
    val loader =
        remember(
            incidentContext?.incidentId,
            policePhoneContext?.policePhoneId,
            policePhoneContext?.apiBaseUrl,
            policePhoneContext?.accessToken,
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
                        apiClient = SuriMapApiClient(baseUrl = policePhoneContext.apiBaseUrl),
                        accessTokenProvider = accessTokenProvider
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
                        val clockSnapshot = clockSyncState.snapshot()
                        offlinePackageDownloadScheduler.schedule(
                            OfflinePackageDownloadWorkRequest(
                                incidentId = incidentContext.incidentId,
                                policePhoneId = policePhoneContext.policePhoneId,
                                manifestId = plan.manifestId,
                                apiBaseUrl = policePhoneContext.apiBaseUrl,
                                accessToken = policePhoneContext.accessToken,
                                clockOffsetMs = clockSnapshot.clockOffsetMs,
                                clockSyncedAt = clockSnapshot.clockSyncedAt?.toString()
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

    LaunchedEffect(loader, fallbackIncidentTitle, retryNonce, installationRefreshSignal) {
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
                    apiClient = SuriMapApiClient(baseUrl = config.apiBaseUrl),
                    credentialsProvider = BuildConfigAuthBootstrapCredentialsProvider
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
        if (outcome is AuthBootstrapOutcome.Ready && state.shouldEnterIncidentList) {
            incidentSessionState.activatePolicePhoneContext(config.toPolicePhoneContext(outcome))
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
    clockSyncState: ClockSyncState,
    onClearClosedOverlay: () -> Unit
) {
    val policePhoneContext = incidentSessionState.policePhoneContext
    val policePhoneLabel = policePhoneContext?.policePhoneId ?: "관리 폴리폰"
    val accessTokenProvider = policePhoneContext.accessTokenProvider()
    val context = LocalContext.current.applicationContext
    val database = remember(context) { SuriMapDatabaseProvider.database(context) }
    val outboxReplayScheduler = remember(context) {
        OutboxReplayScheduler(WorkManager.getInstance(context))
    }
    val syncClient =
        remember(database, outboxReplayScheduler, policePhoneContext?.apiBaseUrl, policePhoneContext?.accessToken) {
            SchedulingSyncClient(
                delegate = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                scheduleReplay = outboxReplayScheduler::schedule,
                apiBaseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL,
                accessToken = policePhoneContext?.accessToken
            )
        }
    val dutyShiftRecorder = remember(syncClient, clockSyncState) {
        DutyShiftLocalRecorder(
            syncClient = syncClient,
            clockOffsetMs = clockSyncState::clockOffsetMs,
            clockSyncedAt = clockSyncState::clockSyncedAt
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val loader = remember(policePhoneContext?.apiBaseUrl, policePhoneContext?.accessToken, policePhoneLabel) {
        IncidentListStateLoader(
            repository =
            IncidentReadRepository(
                apiClient =
                SuriMapApiClient(
                    baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                ),
                accessTokenProvider = accessTokenProvider
            ),
            policePhoneLabel = policePhoneLabel
        )
    }
    val contextResolver = remember(policePhoneContext?.apiBaseUrl, policePhoneContext?.accessToken) {
        IncidentSessionContextResolver(
            operationalPeriods = { incidentId ->
                OperationalPeriodReadRepository(
                    apiClient =
                    SuriMapApiClient(
                        baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                    ),
                    accessTokenProvider = accessTokenProvider
                ).list(incidentId)
            },
            dutyShifts = { query ->
                DutyShiftRepository(
                    apiClient =
                    SuriMapApiClient(
                        baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                    ),
                    accessTokenProvider = accessTokenProvider
                ).listDutyShifts(query)
            }
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
            coroutineScope.launch {
                val resolvedContext =
                    contextResolver.resolve(incident, policePhoneId = policePhoneContext?.policePhoneId)
                clockSyncState.syncClockForIncident(resolvedContext.incidentId, policePhoneContext)
                if (resolvedContext.currentDutyShiftId.isNullOrBlank()) {
                    dutyShiftRecorder.start(resolvedContext.toDutyShiftWriteContext(policePhoneContext))
                }
                incidentSessionState.activateIncidentContext(resolvedContext)
                navController.navigateToSingleTop(PolicePhoneRoute.OfflinePackage)
            }
        },
        onRefresh = { refreshNonce += 1 },
        onDismissClosedDialog = {
            onClearClosedOverlay()
            incidentSessionState.clearIncidentContext()
        }
    )
}

private fun ManagedPolicePhoneConfig.toPolicePhoneContext(outcome: AuthBootstrapOutcome.Ready): PolicePhoneContext {
    return PolicePhoneContext(
        policePhoneId = outcome.policePhoneId,
        apiBaseUrl = apiBaseUrl,
        tileBaseUrl = tileBaseUrl,
        objectStorageBaseUrl = objectStorageBaseUrl,
        allowedHosts = allowedHosts,
        accessToken = outcome.accessToken
    )
}

private suspend fun ClockSyncState.syncClockForIncident(
    incidentId: String?,
    policePhoneContext: PolicePhoneContext?
) {
    val apiBaseUrl = policePhoneContext?.apiBaseUrl?.takeIf(String::isNotBlank) ?: return
    sync(
        client = SyncApiClient(
            apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
            accessTokenProvider = policePhoneContext.accessTokenProvider()
        ),
        incidentId = incidentId,
        policePhoneId = policePhoneContext.policePhoneId
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

private fun IncidentContext?.toMarkerDetailSessionContext(
    policePhoneContext: PolicePhoneContext?,
    markerId: String?
): MarkerDetailSessionContext =
    MarkerDetailSessionContext(
        incidentId = this?.incidentId,
        opId = this?.currentOpId,
        policePhoneId = policePhoneContext?.policePhoneId,
        markerId = markerId
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

private fun MarkerDetailSessionContext.toMarkerWriteContext(): MarkerWriteContext =
    MarkerWriteContext(
        incidentId = incidentId,
        opId = opId,
        policePhoneId = policePhoneId
    )

private fun MarkerDetailUiState.toMarkerUpsertInput(): MarkerUpsertInput =
    MarkerUpsertInput(
        type = markerType.apiValue,
        location =
        if (lon != null && lat != null) {
            MarkerLocation(lon = lon, lat = lat)
        } else {
            null
        },
        memo = memo
    )

private fun Context.createMarkerPhotoCaptureUri(markerId: String): Uri? =
    runCatching {
        val imageDir = File(cacheDir, "marker-photos").apply { mkdirs() }
        val safeMarkerId =
            markerId
                .filter { char -> char.isLetterOrDigit() || char == '-' || char == '_' }
                .ifBlank { "marker" }
        val imageFile = File.createTempFile("marker-$safeMarkerId-", ".jpg", imageDir)
        FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
    }.getOrNull()

private fun Context.markerPhotoUploadPayload(markerId: String, uri: Uri): MarkerPhotoUploadPayload? =
    runCatching {
        val bytes = contentResolver.openInputStream(uri)?.use { input -> input.readBytes() } ?: return null
        MarkerPhotoUploadPayload(
            markerId = markerId,
            contentType = contentResolver.getType(uri) ?: "image/jpeg",
            bytes = bytes
        )
    }.getOrNull()

private fun MarkerDetailUiState.upsertPhoto(photo: MarkerDetailPhotoUiState): MarkerDetailUiState =
    copy(photos = photos.filterNot { it.photoId == photo.photoId } + photo)

private fun MarkerDetailUiState.markPhotoAttaching(photoId: String, progress: Float): MarkerDetailUiState =
    copy(
        photos =
        photos.map { photo ->
            if (photo.photoId == photoId) {
                photo.copy(status = MarkerDetailPhotoStatus.Attaching, progress = progress)
            } else {
                photo
            }
        }
    )

private fun MarkerDetailUiState.markPhotoFailed(photoId: String): MarkerDetailUiState =
    copy(
        photos =
        photos.map { photo ->
            if (photo.photoId == photoId) {
                photo.copy(status = MarkerDetailPhotoStatus.Failed, progress = 1f)
            } else {
                photo
            }
        }
    )

private fun HandoverSessionContext.toHandoverWriteContext(): HandoverWriteContext =
    HandoverWriteContext(
        incidentId = incidentId,
        opId = opId,
        dutyShiftId = dutyShiftId,
        policePhoneId = policePhoneId
    )

private fun IncidentContext?.toDutyShiftWriteContext(policePhoneContext: PolicePhoneContext?): DutyShiftWriteContext =
    DutyShiftWriteContext(
        incidentId = this?.incidentId,
        opId = this?.currentOpId,
        dutyShiftId = this?.currentDutyShiftId,
        policePhoneId = policePhoneContext?.policePhoneId
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

private fun MarkerCreateSheetUiState.toMarkerUpsertInput(): MarkerUpsertInput =
    MarkerUpsertInput(
        type = selectedType.apiValue,
        location = selectedLocation?.let { MarkerLocation(lon = it.lon, lat = it.lat) },
        supportRequestType = supportRequestType?.apiValue,
        memo = memo
    )

private fun MarkerCreateSheetUiState.withCurrentLocation(location: MarkerLocation?): MarkerCreateSheetUiState =
    if (location == null) {
        copy(selectedLocation = null, locationLabel = "지도 기준 위치 확인 필요")
    } else {
        withCurrentLocation(lon = location.lon, lat = location.lat)
    }

private fun MarkerCreateSheetUiState.withManualLocation(location: MarkerLocation?): MarkerCreateSheetUiState =
    if (location == null) {
        copy(selectedLocation = null, locationLabel = "지도 기준 위치 확인 필요")
    } else {
        withManualLocation(lon = location.lon, lat = location.lat)
    }

private fun PolicePhoneContext?.toMapLibreRuntimeMapState(): MapLibreRuntimeMapState =
    MapLibreRuntimeMapState(
        apiBaseUrl = this?.tileBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL,
        policePhoneId = this?.policePhoneId,
        accessToken = this?.accessToken
    )

private fun NavHostController.navigateToSingleTop(route: PolicePhoneRoute) {
    navigateToSingleTop(route.route)
}

private fun NavHostController.navigateToSingleTop(route: String) {
    navigate(route) {
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
