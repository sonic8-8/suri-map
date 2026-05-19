package com.surimap.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
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
import com.surimap.core.auth.OidcSessionStateStore
import com.surimap.core.database.SuriMapDatabaseProvider
import com.surimap.core.fcm.FcmRegistrationCoordinator
import com.surimap.core.fcm.FcmTokenProvider
import com.surimap.core.fcm.FirebaseMessagingTokenProvider
import com.surimap.core.fcm.IncidentAssignmentRefreshSignal
import com.surimap.core.fcm.NoFcmTokenProvider
import com.surimap.core.fcm.SearchAreaBoundaryAlertNotification
import com.surimap.core.fcm.SharedPreferencesFcmRegistrationStateStore
import com.surimap.core.incident.IncidentReadRepository
import com.surimap.core.location.AndroidLocationUpdates
import com.surimap.core.location.GpsLocationFix
import com.surimap.core.map.MapLibreRuntimeMapState
import com.surimap.core.map.MapLibreViewportBounds
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
import com.surimap.feature.bootstrap.data.AndroidOidcLoginClient
import com.surimap.feature.bootstrap.data.AuthBootstrapCoordinator
import com.surimap.feature.bootstrap.data.AuthBootstrapEnvironmentCheck
import com.surimap.feature.bootstrap.data.AuthBootstrapServerCheck
import com.surimap.feature.bootstrap.data.ManagedPolicePhoneConfig
import com.surimap.feature.bootstrap.data.NetworkAuthBootstrapEnvironmentCheck
import com.surimap.feature.bootstrap.data.NetworkPolicePhoneBootstrapServerCheck
import com.surimap.feature.bootstrap.data.OidcLoginSession
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
import com.surimap.feature.incidents.ui.AssignedIncidentUiModel
import com.surimap.feature.incidents.ui.IncidentListScreen
import com.surimap.feature.incidents.ui.IncidentListUiState
import com.surimap.feature.marker.data.HttpObjectStorageUploader
import com.surimap.feature.marker.data.MarkerCreatePhotoInput
import com.surimap.feature.marker.data.MarkerLocalRecorder
import com.surimap.feature.marker.data.MarkerDetailSessionContext
import com.surimap.feature.marker.data.MarkerDetailStateLoader
import com.surimap.feature.marker.data.MarkerLocation
import com.surimap.feature.marker.data.MarkerCreatePhotoUploadResult
import com.surimap.feature.marker.data.MarkerPhotoUiUploadCoordinator
import com.surimap.feature.marker.data.MarkerPhotoUploadPayload
import com.surimap.feature.marker.data.MarkerPhotoUploadResult
import com.surimap.feature.marker.data.MarkerUpsertInput
import com.surimap.feature.marker.data.MarkerWriteContext
import com.surimap.feature.marker.data.MarkerWriteResult
import com.surimap.feature.marker.ui.MarkerCreateBottomSheet
import com.surimap.feature.marker.ui.MarkerDetailScreen
import com.surimap.feature.marker.ui.MarkerCreateSheetUiState
import com.surimap.feature.marker.ui.MarkerPhotoStage
import com.surimap.feature.marker.ui.MarkerPhotoUiState
import com.surimap.feature.marker.ui.MarkerDetailPhotoStatus
import com.surimap.feature.marker.ui.MarkerDetailPhotoUiState
import com.surimap.feature.marker.ui.MarkerDetailUiState
import com.surimap.feature.marker.ui.MarkerSaveStatus
import com.surimap.feature.marker.ui.MarkerType
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
import com.surimap.feature.search.data.SearchAreaBoundaryAlertLocalRecorder
import com.surimap.feature.search.data.SearchPathGpsBatchRecorder
import com.surimap.feature.search.data.SearchPathLocalRecorder
import com.surimap.feature.search.data.SearchPathWriteContext
import com.surimap.feature.search.data.SearchPathWriteResult
import com.surimap.feature.search.data.SearchRecordingSessionState
import com.surimap.feature.search.domain.AssignedSearchAreaBoundary
import com.surimap.feature.search.domain.SearchAreaBoundaryFix
import com.surimap.feature.search.domain.SearchAreaBoundaryMonitor
import com.surimap.feature.search.domain.SearchAreaBoundarySignal
import com.surimap.feature.search.ui.SearchLayerKind
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.feature.search.ui.SearchMapLayerUiState
import com.surimap.feature.search.ui.SearchMapScreen
import com.surimap.feature.search.ui.SearchMapUiState
import com.surimap.feature.search.ui.SearchMapViewportBounds
import com.surimap.feature.showcase.ui.ShowcaseScreen
import com.surimap.ui.navigation.IncidentContext
import com.surimap.ui.navigation.IncidentSessionState
import com.surimap.ui.navigation.MarkerDetailDeepLink
import com.surimap.ui.navigation.PolicePhoneContext
import com.surimap.ui.navigation.PolicePhoneRoute
import com.surimap.ui.navigation.SearchMapDeepLink
import com.surimap.ui.navigation.accessTokenProvider
import com.surimap.ui.session.SuriMapSessionSnapshotStore
import com.surimap.ui.theme.PoliBgBase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val AUTH_BOOTSTRAP_LOG_TAG = "AuthBootstrap"
private const val ACCESS_TOKEN_REFRESH_SKEW_MS = 60_000L
private const val ACCESS_TOKEN_REFRESH_FALLBACK_MS = 4 * 60 * 1_000L

@Composable
fun SuriMapApp() {
    if (BuildConfig.SURI_MAP_DEBUG_SHOWCASE) {
        ShowcaseScreen(modifier = Modifier.fillMaxSize())
        return
    }
    val context = LocalContext.current.applicationContext
    val navController = rememberNavController()
    var assignmentRefreshNonce by remember { mutableStateOf(0) }
    val sessionSnapshotStore = remember(context) { SuriMapSessionSnapshotStore(context) }
    val oidcSessionStateStore = remember(context) { OidcSessionStateStore(context) }
    var oidcAuthStateJson by remember(oidcSessionStateStore) {
        mutableStateOf(oidcSessionStateStore.load())
    }
    val persistedOidcSession = remember(oidcAuthStateJson) {
        AndroidOidcLoginClient.sessionFromAuthStateJson(oidcAuthStateJson)
    }
    val persistedSessionSnapshot = remember(sessionSnapshotStore) { sessionSnapshotStore.load() }
    val incidentSessionState =
        remember {
            IncidentSessionState(
                initialIncidentContext =
                persistedSessionSnapshot?.toIncidentContext() ?: debugMapOnlyIncidentContext(),
                initialPolicePhoneContext =
                persistedSessionSnapshot?.toPolicePhoneContext(
                    accessToken = persistedOidcSession?.accessToken,
                    accessTokenExpiresAtEpochMs = persistedOidcSession?.accessTokenExpiresAtEpochMs
                ) ?: debugMapOnlyPolicePhoneContext()
            )
        }
    val clockSyncState = remember { ClockSyncState() }
    var incidentClosed by remember { mutableStateOf<IncidentClosedOverlayState?>(null) }
    var blockedQueue by remember { mutableStateOf<BlockedQueueToastState?>(null) }
    var handoverMemoSaved by remember { mutableStateOf<HandoverMemoSavedToastState?>(null) }
    var searchPathEnded by remember { mutableStateOf<SearchPathEndedToastState?>(null) }

    LaunchedEffect(incidentSessionState.incidentContext, incidentSessionState.policePhoneContext) {
        sessionSnapshotStore.save(
            incidentContext = incidentSessionState.incidentContext,
            policePhoneContext = incidentSessionState.policePhoneContext
        )
    }

    OidcSessionRefreshEffect(
        policePhoneContext = incidentSessionState.policePhoneContext,
        authStateJson = oidcAuthStateJson,
        onSessionRefreshed = { oidcSession ->
            oidcSessionStateStore.save(oidcSession.authStateJson)
            oidcAuthStateJson = oidcSession.authStateJson
            incidentSessionState.policePhoneContext
                ?.withOidcSession(oidcSession)
                ?.let(incidentSessionState::activatePolicePhoneContext)
        },
        onSessionExpired = {
            oidcSessionStateStore.clear()
            oidcAuthStateJson = null
            incidentSessionState.clearPolicePhoneContext()
            navController.navigateToAuthBootstrapRoot()
        }
    )
    IncidentAssignmentRefreshEffect(onRefresh = { assignmentRefreshNonce += 1 })
    NotificationPermissionEffect()

    Surface(modifier = Modifier.fillMaxSize(), color = PoliBgBase) {
        AppOverlayHost(
            state =
            AppOverlayState(
                incidentClosed = incidentClosed,
                blockedQueue = blockedQueue,
                handoverMemoSaved = handoverMemoSaved,
                searchPathEnded = searchPathEnded
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
            onDismissHandoverMemoSaved = { handoverMemoSaved = null },
            onDismissSearchPathEnded = { searchPathEnded = null }
        ) {
            FcmRegistrationEffect(policePhoneContext = incidentSessionState.policePhoneContext)
            NavHost(
                navController = navController,
                startDestination = debugStartDestination(),
                modifier = Modifier.fillMaxSize()
            ) {
                composable(PolicePhoneRoute.AuthBootstrap.route) {
                    AuthBootstrapRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController,
                        assignmentRefreshNonce = assignmentRefreshNonce,
                        onOidcSessionChanged = { oidcSession ->
                            if (oidcSession == null) {
                                oidcSessionStateStore.clear()
                                oidcAuthStateJson = null
                            } else {
                                oidcSessionStateStore.save(oidcSession.authStateJson)
                                oidcAuthStateJson = oidcSession.authStateJson
                            }
                        }
                    )
                }
                composable(PolicePhoneRoute.IncidentList.route) {
                    IncidentListRoute(
                        incidentSessionState = incidentSessionState,
                        navController = navController,
                        incidentClosed = incidentClosed,
                        clockSyncState = clockSyncState,
                        assignmentRefreshNonce = assignmentRefreshNonce,
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
                        },
                        onSearchPathEnded = { pendingSync ->
                            searchPathEnded = SearchPathEndedToastState(pendingSync = pendingSync)
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
private fun OidcSessionRefreshEffect(
    policePhoneContext: PolicePhoneContext?,
    authStateJson: String?,
    onSessionRefreshed: (OidcLoginSession) -> Unit,
    onSessionExpired: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val oidcLoginClient = remember(context) { AndroidOidcLoginClient(context) }
    val currentContext by rememberUpdatedState(policePhoneContext)
    val currentOnSessionRefreshed by rememberUpdatedState(onSessionRefreshed)
    val currentOnSessionExpired by rememberUpdatedState(onSessionExpired)

    DisposableEffect(oidcLoginClient) {
        onDispose { oidcLoginClient.dispose() }
    }

    LaunchedEffect(
        oidcLoginClient,
        policePhoneContext?.apiBaseUrl,
        authStateJson,
        policePhoneContext?.accessTokenExpiresAtEpochMs
    ) {
        while (true) {
            val contextSnapshot = currentContext ?: return@LaunchedEffect
            val stateJson = authStateJson?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
            delay(accessTokenRefreshDelayMs(contextSnapshot.accessTokenExpiresAtEpochMs))

            if (currentContext == null) return@LaunchedEffect
            val refreshedSession = oidcLoginClient.refresh(stateJson)
            if (refreshedSession == null) {
                currentOnSessionExpired()
                return@LaunchedEffect
            }
            currentOnSessionRefreshed(refreshedSession)
        }
    }
}

private fun accessTokenRefreshDelayMs(accessTokenExpiresAtEpochMs: Long?): Long {
    val now = System.currentTimeMillis()
    val refreshAt =
        accessTokenExpiresAtEpochMs
            ?.minus(ACCESS_TOKEN_REFRESH_SKEW_MS)
            ?: now + ACCESS_TOKEN_REFRESH_FALLBACK_MS
    return (refreshAt - now).coerceAtLeast(0L)
}

@Composable
private fun IncidentAssignmentRefreshEffect(onRefresh: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == IncidentAssignmentRefreshSignal.Action) {
                        currentOnRefresh()
                    }
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(IncidentAssignmentRefreshSignal.Action),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}

@Composable
private fun NotificationPermissionEffect() {
    if (!BuildConfig.SURI_MAP_FIREBASE_MESSAGING_ENABLED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return
    }
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The app can still receive data messages without notification permission.
        }

    LaunchedEffect(context) {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                        apiBaseUrl = apiBaseUrl
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
                apiBaseUrl = apiBaseUrl
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
        remember(database, outboxReplayScheduler, policePhoneContext?.apiBaseUrl) {
            SchedulingSyncClient(
                delegate = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                scheduleReplay = outboxReplayScheduler::schedule,
                apiBaseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
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
    onOpenBlockedOutbox: () -> Unit,
    onSearchPathEnded: (pendingSync: Boolean) -> Unit
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
        remember(database, outboxReplayScheduler, policePhoneContext?.apiBaseUrl) {
            SchedulingSyncClient(
                delegate = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                scheduleReplay = outboxReplayScheduler::schedule,
                apiBaseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
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
    val boundaryAlertRecorder = remember(syncClient, clockSyncState) {
        SearchAreaBoundaryAlertLocalRecorder(
            syncClient = syncClient,
            clockOffsetMs = clockSyncState::clockOffsetMs,
            clockSyncedAt = clockSyncState::clockSyncedAt
        )
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
    val markerPhotoUploadCoordinator =
        remember(syncClient, policePhoneContext?.apiBaseUrl, policePhoneContext?.accessToken, clockSyncState) {
            MarkerPhotoUiUploadCoordinator(
                syncClient = syncClient,
                apiClient =
                SuriMapApiClient(
                    baseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
                ),
                accessTokenProvider = accessTokenProvider,
                uploader = HttpObjectStorageUploader(),
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
    var searchMapState by remember(
        sessionContext.incidentId,
        sessionContext.currentOpId,
        sessionContext.policePhoneId
    ) {
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
    var latestLocationFix by remember { mutableStateOf<GpsLocationFix?>(null) }
    val boundaryMonitor = remember(
        sessionContext.incidentId,
        sessionContext.currentOpId,
        sessionContext.policePhoneId
    ) { SearchAreaBoundaryMonitor() }
    var markerSheetOpen by remember { mutableStateOf(false) }
    var markerSheetState by remember { mutableStateOf(MarkerCreateSheetUiState.default()) }
    var createPhotoUriById by remember { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    var pendingCreateCameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCurrentLocationCenter by remember { mutableStateOf(false) }
    val currentPendingCurrentLocationCenter by rememberUpdatedState(pendingCurrentLocationCenter)

    fun centerMapOnCurrentLocation(fix: GpsLocationFix) {
        latestLocationFix = fix
        pendingCurrentLocationCenter = false
        searchMapState = searchMapState.withFocusedMarker(null).centerOnCurrentLocation(fix)
    }

    fun requestCurrentLocationCenter() {
        val lastKnownFix = latestLocationFix ?: locationUpdates.lastKnownFix()
        if (lastKnownFix != null) {
            centerMapOnCurrentLocation(lastKnownFix)
        } else {
            pendingCurrentLocationCenter = true
        }
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
            if (grantResults.values.any { it }) {
                requestCurrentLocationCenter()
            }
        }

    fun beginCreatePhotoUpload(uri: Uri, existingLocalId: String? = null) {
        val current = markerSheetState
        if (!current.canAttachPhoto && existingLocalId == null) {
            return
        }
        val localId = existingLocalId ?: "local-create-photo-${System.currentTimeMillis()}"
        val label =
            existingLocalId?.let { id ->
                current.photos.firstOrNull { photo -> photo.localId == id }?.fileName
            } ?: "사진 ${current.photos.size + 1}"
        createPhotoUriById = createPhotoUriById + (localId to uri)
        markerSheetState =
            current.upsertCreatePhoto(
                MarkerPhotoUiState(
                    localId = localId,
                    fileName = label,
                    stage = MarkerPhotoStage.Selected,
                    progress = 0.1f
                )
            )
        coroutineScope.launch {
            clockSyncState.syncClockForIncident(sessionContext.incidentId, policePhoneContext)
            val payload = context.markerPhotoUploadPayload(markerId = current.draftMarkerId, uri = uri)
            if (payload == null) {
                markerSheetState = markerSheetState.markCreatePhotoFailed(localId)
                return@launch
            }
            markerSheetState =
                markerSheetState.upsertCreatePhoto(
                    MarkerPhotoUiState(
                        localId = localId,
                        fileName = label,
                        stage = MarkerPhotoStage.ObjectStorageUpload,
                        progress = 0.55f,
                        sizeBytes = payload.sizeBytes,
                        contentType = payload.contentType
                    )
                )
            markerSheetState =
                when (
                    val upload =
                        markerPhotoUploadCoordinator.uploadForCreate(
                            context = sessionContext.toMarkerWriteContext(),
                            payload = payload
                        )
                ) {
                    MarkerCreatePhotoUploadResult.Blocked,
                    is MarkerCreatePhotoUploadResult.UploadFailed -> markerSheetState.markCreatePhotoFailed(localId)

                    is MarkerCreatePhotoUploadResult.Uploaded ->
                        markerSheetState.upsertCreatePhoto(
                            MarkerPhotoUiState(
                                localId = localId,
                                fileName = label,
                                stage = MarkerPhotoStage.Attach,
                                progress = 1f,
                                photoId = upload.photoId,
                                contentType = upload.contentType,
                                sizeBytes = upload.sizeBytes,
                                width = upload.width,
                                height = upload.height,
                                checksumSha256 = upload.checksumSha256
                            )
                        )
                }
        }
    }

    val createPhotoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { selected -> beginCreatePhotoUpload(selected) }
        }
    val createPhotoCapture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
            val capturedUri = pendingCreateCameraPhotoUri
            pendingCreateCameraPhotoUri = null
            if (captured && capturedUri != null) {
                beginCreatePhotoUpload(capturedUri)
            }
        }

    LaunchedEffect(
        sessionContext.incidentId,
        sessionContext.policePhoneId,
        policePhoneContext?.apiBaseUrl,
        policePhoneContext?.accessToken
    ) {
        clockSyncState.syncClockForIncident(sessionContext.incidentId, policePhoneContext)
        latestLocationFix = locationUpdates.lastKnownFix()
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
    val serverActiveSearchPathStartedAtMs = searchMapState.activeSearchPathStartedAtEpochMs
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
        ).withCurrentLocationViewport(latestLocationFix)
    val currentAssignedBoundaries by rememberUpdatedState(displayedSearchMapState.assignedTeamSearchAreaBoundaries())

    LaunchedEffect(displayedLifecycle, activeSearchPathId, serverActiveSearchPathStartedAtMs) {
        if (displayedLifecycle == SearchLifecycleStatus.Active && activeSearchPathId != null) {
            val now = System.currentTimeMillis()
            recordingSession =
                recordingSession.ensureActiveStarted(
                    searchPathId = activeSearchPathId,
                    nowMs = now,
                    serverStartedAtMs = serverActiveSearchPathStartedAtMs
                )
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
                    latestLocationFix = fix
                    if (currentPendingCurrentLocationCenter) {
                        centerMapOnCurrentLocation(fix)
                    }
                    when (
                        val signal = boundaryMonitor.evaluate(
                            boundaries = currentAssignedBoundaries,
                            fix = SearchAreaBoundaryFix(lon = fix.lon, lat = fix.lat),
                            nowMs = System.currentTimeMillis()
                        )
                    ) {
                        is SearchAreaBoundarySignal.Exited -> {
                            SearchAreaBoundaryAlertNotification.showLocalExit(
                                context,
                                signal.boundary.label,
                                signal.boundary.searchAreaId
                            )
                            Toast.makeText(
                                context,
                                "GPS 기준 현재 위치가 ${signal.boundary.label} 경계 밖으로 표시됩니다.",
                                Toast.LENGTH_LONG
                            ).show()
                            coroutineScope.launch {
                                boundaryAlertRecorder.outsideAssignedArea(
                                    context = sessionContext.toSearchPathWriteContext(),
                                    boundary = signal.boundary,
                                    searchPathId = activeSearchPathId,
                                    fix = fix
                                )
                            }
                        }

                        is SearchAreaBoundarySignal.Reentered,
                        null -> Unit
                    }
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

    DisposableEffect(locationUpdates, pendingCurrentLocationCenter, displayedLifecycle, activeSearchPathId) {
        if (!pendingCurrentLocationCenter || (displayedLifecycle == SearchLifecycleStatus.Active && activeSearchPathId != null)) {
            onDispose {}
        } else {
            val handle =
                locationUpdates.start { fix ->
                    centerMapOnCurrentLocation(fix)
                }
            onDispose {
                handle.stop()
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
                            val pauseResult =
                                searchPathRecorder.pause(
                                    context = sessionContext.toSearchPathWriteContext(),
                                    searchPathId = activeSearchPathId
                                )
                            if (pauseResult is SearchPathWriteResult.Enqueued) {
                                recordingSession = recordingSession.pause(now)
                                elapsedTickerNowMs = now
                            }
                        }
                        SearchLifecycleStatus.Paused -> {
                            val resumeResult =
                                searchPathRecorder.resume(
                                    context = sessionContext.toSearchPathWriteContext(),
                                    searchPathId = activeSearchPathId
                                )
                            if (resumeResult is SearchPathWriteResult.Enqueued) {
                                recordingSession = recordingSession.resume(now)
                                elapsedTickerNowMs = now
                            }
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
                    val endResult =
                        searchPathRecorder.end(
                            context = sessionContext.toSearchPathWriteContext(),
                            searchPathId = pathId
                        )
                    recordingSession = recordingSession.stop(now)
                    elapsedTickerNowMs = now
                    if (endResult is SearchPathWriteResult.Enqueued) {
                        onSearchPathEnded(true)
                    }
                }
            },
            onCreateMarker = {
                markerSheetState =
                    MarkerCreateSheetUiState.default()
                        .withCurrentLocation(displayedSearchMapState.markerCreationLocation())
                createPhotoUriById = emptyMap()
                pendingCreateCameraPhotoUri = null
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
            onCenterCurrentLocation = {
                if (context.hasLocationPermission()) {
                    requestCurrentLocationCenter()
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            onFocusSearchArea = { kind, overlayId ->
                searchMapState = searchMapState.centerOnSearchLayer(kind, overlayId)
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
                onCapturePhoto = {
                    context.createMarkerPhotoCaptureUri(markerSheetState.draftMarkerId)?.let { uri ->
                        pendingCreateCameraPhotoUri = uri
                        createPhotoCapture.launch(uri)
                    }
                },
                onPickPhoto = {
                    createPhotoPicker.launch("image/*")
                },
                onRetryPhoto = { photo ->
                    val uri = createPhotoUriById[photo.localId]
                    if (uri != null) {
                        beginCreatePhotoUpload(uri, existingLocalId = photo.localId)
                    } else {
                        createPhotoPicker.launch("image/*")
                    }
                }
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
        remember(database, outboxReplayScheduler, apiBaseUrl) {
            SchedulingSyncClient(
                delegate = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                scheduleReplay = outboxReplayScheduler::schedule,
                apiBaseUrl = apiBaseUrl
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
    navController: NavHostController,
    assignmentRefreshNonce: Int,
    onOidcSessionChanged: (OidcLoginSession?) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val managedConfigurationReader = remember(appContext) {
        AndroidManagedConfigurationReader(context = appContext)
    }
    var oidcSession by remember { mutableStateOf<OidcLoginSession?>(null) }
    var retryNonce by remember { mutableStateOf(0) }
    val oidcLoginClient = remember(appContext) { AndroidOidcLoginClient(appContext) }
    val coroutineScope = rememberCoroutineScope()
    val oidcLoginLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(
                AUTH_BOOTSTRAP_LOG_TAG,
                "login activity resultCode=${result.resultCode} hasData=${result.data != null} data=${result.data?.dataString}"
            )
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                coroutineScope.launch {
                    val completedSession = oidcLoginClient.completeLogin(result.data!!)
                    oidcSession = completedSession
                    onOidcSessionChanged(completedSession)
                    retryNonce += 1
                }
            } else {
                Log.w(AUTH_BOOTSTRAP_LOG_TAG, "login activity returned without a usable response")
            }
        }
    DisposableEffect(oidcLoginClient) {
        onDispose { oidcLoginClient.dispose() }
    }
    val bootstrapCoordinator = remember(managedConfigurationReader, oidcSession?.accessToken) {
        AuthBootstrapCoordinator(
            managedConfigurationReader = managedConfigurationReader,
            environmentCheck =
            AuthBootstrapEnvironmentCheck { config ->
                NetworkAuthBootstrapEnvironmentCheck(apiClient = SuriMapApiClient(baseUrl = config.apiBaseUrl))
                    .verify(config)
            },
            serverCheck =
            AuthBootstrapServerCheck { config ->
                NetworkPolicePhoneBootstrapServerCheck(
                    apiClient = SuriMapApiClient(baseUrl = config.apiBaseUrl),
                    accessTokenProvider = { oidcSession?.accessToken }
                ).verify(config)
            }
        )
    }
    var state by remember {
        mutableStateOf(AuthBootstrapUiState.checking(apiBaseUrl = BuildConfig.SURI_MAP_API_BASE_URL))
    }

    LaunchedEffect(retryNonce, assignmentRefreshNonce) {
        val config = bootstrapCoordinator.readConfig()
        state = AuthBootstrapUiState.checking(apiBaseUrl = config.apiBaseUrl)
        val outcome = bootstrapCoordinator.check(config)
        state = AuthBootstrapUiState.fromOutcome(outcome = outcome, apiBaseUrl = config.apiBaseUrl)
        if (outcome is AuthBootstrapOutcome.Ready && state.shouldEnterIncidentList) {
            incidentSessionState.activatePolicePhoneContext(config.toPolicePhoneContext(outcome, oidcSession))
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
        onRetry = {
            if (state.requiresAuthentication) {
                oidcLoginLauncher.launch(oidcLoginClient.createAuthorizationIntent(bootstrapCoordinator.readConfig().apiBaseUrl))
            } else {
                retryNonce += 1
            }
        },
        onExit = {
            context.findActivity()?.finish()
        }
    )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun IncidentListRoute(
    incidentSessionState: IncidentSessionState,
    navController: NavHostController,
    incidentClosed: IncidentClosedOverlayState?,
    clockSyncState: ClockSyncState,
    assignmentRefreshNonce: Int,
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
        remember(database, outboxReplayScheduler, policePhoneContext?.apiBaseUrl) {
            SchedulingSyncClient(
                delegate = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                scheduleReplay = outboxReplayScheduler::schedule,
                apiBaseUrl = policePhoneContext?.apiBaseUrl ?: BuildConfig.SURI_MAP_API_BASE_URL
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
    var manualRefreshNonce by remember { mutableStateOf(0) }
    var state by remember {
        mutableStateOf(IncidentListUiState.loading(policePhoneLabel = policePhoneLabel))
    }

    LaunchedEffect(assignmentRefreshNonce, manualRefreshNonce, incidentClosed, loader, policePhoneLabel) {
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
                openIncidentRoute(
                    incident = incident,
                    policePhoneContext = policePhoneContext,
                    contextResolver = contextResolver,
                    clockSyncState = clockSyncState,
                    dutyShiftRecorder = dutyShiftRecorder,
                    incidentSessionState = incidentSessionState,
                    navController = navController,
                    route = PolicePhoneRoute.SearchMap
                )
            }
        },
        onOpenOfflinePackage = { incident ->
            coroutineScope.launch {
                openIncidentRoute(
                    incident = incident,
                    policePhoneContext = policePhoneContext,
                    contextResolver = contextResolver,
                    clockSyncState = clockSyncState,
                    dutyShiftRecorder = dutyShiftRecorder,
                    incidentSessionState = incidentSessionState,
                    navController = navController,
                    route = PolicePhoneRoute.OfflinePackage
                )
            }
        },
        onRefresh = { manualRefreshNonce += 1 },
        onDismissClosedDialog = {
            onClearClosedOverlay()
            incidentSessionState.clearIncidentContext()
        }
    )
}

private suspend fun openIncidentRoute(
    incident: AssignedIncidentUiModel,
    policePhoneContext: PolicePhoneContext?,
    contextResolver: IncidentSessionContextResolver,
    clockSyncState: ClockSyncState,
    dutyShiftRecorder: DutyShiftLocalRecorder,
    incidentSessionState: IncidentSessionState,
    navController: NavHostController,
    route: PolicePhoneRoute
) {
    val resolvedContext =
        contextResolver.resolve(incident, policePhoneId = policePhoneContext?.policePhoneId)
    clockSyncState.syncClockForIncident(resolvedContext.incidentId, policePhoneContext)
    if (resolvedContext.currentDutyShiftId.isNullOrBlank()) {
        dutyShiftRecorder.start(resolvedContext.toDutyShiftWriteContext(policePhoneContext))
    }
    incidentSessionState.activateIncidentContext(resolvedContext)
    navController.navigateToSingleTop(route)
}

private fun ManagedPolicePhoneConfig.toPolicePhoneContext(
    outcome: AuthBootstrapOutcome.Ready,
    oidcSession: OidcLoginSession?
): PolicePhoneContext {
    return PolicePhoneContext(
        policePhoneId = outcome.policePhoneId,
        apiBaseUrl = apiBaseUrl,
        tileBaseUrl = tileBaseUrl,
        objectStorageBaseUrl = objectStorageBaseUrl,
        allowedHosts = allowedHosts,
        accessToken = outcome.accessToken,
        accessTokenExpiresAtEpochMs = oidcSession?.accessTokenExpiresAtEpochMs
    )
}

private fun PolicePhoneContext.withOidcSession(oidcSession: OidcLoginSession): PolicePhoneContext =
    copy(
        accessToken = oidcSession.accessToken,
        accessTokenExpiresAtEpochMs = oidcSession.accessTokenExpiresAtEpochMs
    )

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
        currentOpLabel = this?.currentOpLabel,
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

private fun MarkerCreateSheetUiState.upsertCreatePhoto(photo: MarkerPhotoUiState): MarkerCreateSheetUiState {
    val nextPhotos = photos.filterNot { it.localId == photo.localId } + photo
    return copy(photos = nextPhotos, photoCount = nextPhotos.size)
}

private fun MarkerCreateSheetUiState.markCreatePhotoFailed(localId: String): MarkerCreateSheetUiState {
    val nextPhotos =
        photos.map { photo ->
            if (photo.localId == localId) {
                photo.copy(stage = MarkerPhotoStage.ObjectStorageUpload, progress = 1f, retryAvailable = true)
            } else {
                photo
            }
        }
    return copy(photos = nextPhotos, photoCount = nextPhotos.size)
}

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
    activeSearchPathId?.takeIf(String::isNotBlank)
        ?: layers.firstOrNull { layer -> layer.kind == SearchLayerKind.Path && layer.highlighted }?.overlayId

private fun SearchMapUiState.withCurrentLocationViewport(fix: GpsLocationFix?): SearchMapUiState {
    val normalizedFix = fix ?: return this
    val currentLocationLayer =
        SearchMapLayerUiState(
            label =
            normalizedFix.bearingDegrees
                ?.let { bearing -> "현재 위치 · ${bearing.toInt()}°" }
                ?: "현재 위치",
            kind = SearchLayerKind.CurrentLocation,
            highlighted = true,
            overlayId = "current-location",
            geoJson = """{"type":"Point","coordinates":[${normalizedFix.lon},${normalizedFix.lat}]}"""
        )
    val nextLayers = layers.filterNot { layer -> layer.kind == SearchLayerKind.CurrentLocation } + currentLocationLayer
    if (viewportBounds != null) {
        return copy(layers = nextLayers)
    }
    return copy(
        layers = nextLayers,
        viewportBounds = normalizedFix.toSearchMapViewportBounds()
    )
}

internal fun SearchMapUiState.centerOnCurrentLocation(fix: GpsLocationFix): SearchMapUiState =
    withCurrentLocationViewport(fix).copy(
        viewportBounds = fix.toSearchMapViewportBounds(),
        focusedMarkerId = null
    )

private fun SearchMapUiState.assignedTeamSearchAreaBoundaries(): List<AssignedSearchAreaBoundary> =
    layers
        .filter { layer -> layer.kind == SearchLayerKind.Team && !layer.geoJson.isNullOrBlank() }
        .map { layer ->
            AssignedSearchAreaBoundary(
                searchAreaId = layer.overlayId.orEmpty(),
                label = layer.label.ifBlank { "담당 구역" },
                geoJson = layer.geoJson.orEmpty()
            )
        }

private fun GpsLocationFix.toSearchMapViewportBounds(): SearchMapViewportBounds {
    val delta = 0.003
    return SearchMapViewportBounds(
        south = lat - delta,
        west = lon - delta,
        north = lat + delta,
        east = lon + delta
    )
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun SearchMapUiState.markerCreationLocation(): MarkerLocation? =
    viewportBounds?.let { bounds ->
        MarkerLocation(
            lon = (bounds.west + bounds.east) / 2.0,
            lat = (bounds.south + bounds.north) / 2.0
        )
    }

private fun MarkerCreateSheetUiState.toMarkerUpsertInput(): MarkerUpsertInput =
    MarkerUpsertInput(
        markerId = draftMarkerId,
        type = selectedType.apiValue,
        location = selectedLocation?.let { MarkerLocation(lon = it.lon, lat = it.lat) },
        supportRequestType = supportRequestType?.apiValue,
        memo = memo,
        photos =
        photos.mapNotNull { photo ->
            val photoId = photo.photoId ?: return@mapNotNull null
            val contentType = photo.contentType ?: return@mapNotNull null
            val sizeBytes = photo.sizeBytes ?: return@mapNotNull null
            MarkerCreatePhotoInput(
                photoId = photoId,
                sizeBytes = sizeBytes,
                contentType = contentType,
                width = photo.width,
                height = photo.height,
                checksumSha256 = photo.checksumSha256
            )
        }
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
        accessToken = this?.accessToken,
        initialBounds =
        if (BuildConfig.SURI_MAP_DEBUG_MAP_ONLY) {
            DebugMapOnlyGwangjuBounds
        } else {
            null
        }
    )

private val DebugMapOnlyGwangjuBounds =
    MapLibreViewportBounds(
        south = 35.052595,
        west = 126.647507,
        north = 35.256837,
        east = 127.017482
    )

private fun debugStartDestination(): String =
    if (BuildConfig.SURI_MAP_DEBUG_MAP_ONLY) {
        PolicePhoneRoute.SearchMap.route
    } else {
        PolicePhoneRoute.AuthBootstrap.route
    }

private fun debugMapOnlyIncidentContext(): IncidentContext? {
    if (!BuildConfig.SURI_MAP_DEBUG_MAP_ONLY) return null
    val incidentId = BuildConfig.SURI_MAP_DEBUG_MAP_ONLY_INCIDENT_ID.takeIf(String::isNotBlank) ?: return null
    return IncidentContext(
        incidentId = incidentId,
        currentOpId = BuildConfig.SURI_MAP_DEBUG_MAP_ONLY_OP_ID.takeIf(String::isNotBlank),
        currentDutyShiftId = BuildConfig.SURI_MAP_DEBUG_MAP_ONLY_DUTY_SHIFT_ID.takeIf(String::isNotBlank)
    )
}

private fun debugMapOnlyPolicePhoneContext(): PolicePhoneContext? {
    if (!BuildConfig.SURI_MAP_DEBUG_MAP_ONLY) return null
    val policePhoneId = BuildConfig.SURI_MAP_DEBUG_MAP_ONLY_POLICE_PHONE_ID.takeIf(String::isNotBlank) ?: return null
    val apiBaseUrl = BuildConfig.SURI_MAP_API_BASE_URL
    return PolicePhoneContext(
        policePhoneId = policePhoneId,
        apiBaseUrl = apiBaseUrl,
        tileBaseUrl = apiBaseUrl,
        objectStorageBaseUrl = apiBaseUrl,
        accessToken = BuildConfig.SURI_MAP_DEBUG_MAP_ONLY_ACCESS_TOKEN.takeIf(String::isNotBlank)
    )
}

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

private fun NavHostController.navigateToAuthBootstrapRoot() {
    navigate(PolicePhoneRoute.AuthBootstrap.route) {
        popUpTo(graph.startDestinationId) {
            inclusive = true
        }
        launchSingleTop = true
    }
}
