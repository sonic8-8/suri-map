package com.surimap.ui.qa

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.surimap.core.database.SuriMapDatabaseProvider
import com.surimap.core.incident.IncidentReadRepository
import com.surimap.core.map.MapLibreRuntimeMapState
import com.surimap.core.marker.MarkerRepository
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.offline.OfflinePackageManifestQuery
import com.surimap.core.offline.OfflinePackageRepository
import com.surimap.core.path.SearchPathRepository
import com.surimap.core.searcharea.SearchAreaReadRepository
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
import com.surimap.feature.marker.ui.MarkerDetailPhotoStatus
import com.surimap.feature.marker.ui.MarkerDetailPhotoUiState
import com.surimap.feature.marker.ui.MarkerDetailScreen
import com.surimap.feature.marker.ui.sampleMarkerDetailState
import com.surimap.feature.offline.ui.OfflinePackageScreen
import com.surimap.feature.offline.ui.sampleOfflinePackageState
import com.surimap.feature.outbox.ui.BlockedOutboxScreen
import com.surimap.feature.outbox.ui.sampleBlockedOutboxUiState
import com.surimap.feature.search.data.SearchMapStateLoader
import com.surimap.feature.search.ui.SearchMapScreen
import com.surimap.feature.search.ui.SearchMapSyncStatus
import com.surimap.feature.search.ui.SearchMapUiState
import com.surimap.ui.navigation.accessTokenProvider
import com.surimap.ui.theme.PoliBgBase
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.SuriMapTheme
import com.surimap.ui.session.SuriMapSessionSnapshot
import com.surimap.ui.session.SuriMapSessionSnapshotStore
import java.io.File

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
            LiveSearchMapQaScreen()

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
                onOpenFocusedMarkerDetail = {},
                onToggleBottomPanel = {},
                onToggleMapOverlays = {},
                showMapPreview = true
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
                onOpenFocusedMarkerDetail = {},
                onToggleBottomPanel = {},
                onToggleMapOverlays = {},
                showMapPreview = true
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
                onOpenFocusedMarkerDetail = {},
                onToggleBottomPanel = {},
                onToggleMapOverlays = {},
                showMapPreview = true
            )

        DeviceQaRoute.MarkerDetail -> {
            val context = LocalContext.current
            var state by remember { mutableStateOf(sampleMarkerDetailState()) }
            var pendingCameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
            fun addAttachedQaPhoto(labelPrefix: String) {
                state =
                    state.copy(
                        photos =
                            state.photos +
                                MarkerDetailPhotoUiState(
                                    photoId = "qa-photo-${System.currentTimeMillis()}",
                                    label = "$labelPrefix ${state.photos.size + 1}",
                                    status = MarkerDetailPhotoStatus.Attached
                                )
                    )
            }
            val photoCapture =
                rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
                    pendingCameraPhotoUri = null
                    if (captured) {
                        addAttachedQaPhoto("촬영 사진")
                    }
                }
            val photoPicker =
                rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) {
                        addAttachedQaPhoto("앨범 사진")
                    }
                }
            MarkerDetailScreen(
                state = state,
                onBack = {},
                onMemoChange = { memo -> state = state.copy(memo = memo) },
                onSave = {},
                onRequestDelete = { state = state.copy(showDeleteConfirm = true) },
                onDismissDelete = { state = state.copy(showDeleteConfirm = false) },
                onConfirmDelete = {},
                onCapturePhoto = {
                    context.createQaMarkerPhotoCaptureUri(state.markerId)?.let { uri ->
                        pendingCameraPhotoUri = uri
                        photoCapture.launch(uri)
                    }
                },
                onPickPhoto = { photoPicker.launch("image/*") },
                onRetryPhoto = {}
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

@Composable
private fun LiveSearchMapQaScreen() {
    val context = LocalContext.current.applicationContext
    val snapshotStore = remember(context) { SuriMapSessionSnapshotStore(context) }
    val snapshot = remember(snapshotStore) { snapshotStore.load() }
    var loadState by remember(snapshot) {
        mutableStateOf<SearchMapQaLoadState>(SearchMapQaLoadState.Loading)
    }

    LaunchedEffect(snapshot) {
        loadState =
            when {
                snapshot == null -> {
                    SearchMapQaLoadState.Unavailable(
                        "실제 앱에서 로그인하고 사건을 연 다음 이 QA 화면을 다시 열어줘."
                    )
                }

                snapshot.toSearchMapSessionContext() == null -> {
                    SearchMapQaLoadState.Unavailable(
                        "사건 세션이 아직 없어. 실제 앱에서 사건을 연 뒤 다시 열어줘."
                    )
                }

                snapshot.toPolicePhoneContext() == null -> {
                    SearchMapQaLoadState.Unavailable(
                        "폴리폰 세션이 아직 없어. 실제 앱에서 로그인한 뒤 다시 열어줘."
                    )
                }

                else -> {
                    runCatching { loadLiveSearchMapQaState(context, snapshot) }
                        .getOrElse { throwable ->
                            SearchMapQaLoadState.Unavailable(
                                "실제 데이터 로드 실패: ${throwable.message ?: throwable.javaClass.simpleName}"
                            )
                        }
                }
            }
    }

    when (val state = loadState) {
        SearchMapQaLoadState.Loading ->
            QaStatusScreen(
                title = "실제 앱 세션을 읽는 중",
                message = "잠시만 기다려줘."
            )

        is SearchMapQaLoadState.Unavailable ->
            QaStatusScreen(
                title = "실제 세션이 필요해",
                message = state.message
            )

        is SearchMapQaLoadState.Ready ->
            SearchMapScreen(
                state = state.searchMapState,
                mapState = state.mapState,
                onBack = {},
                onPrimaryLifecycleAction = {},
                onStopSearch = {},
                onCreateMarker = {},
                onOpenHandover = {},
                onOpenBlockedOutbox = {},
                onDismissIncidentAlert = {},
                onOpenIncidentAlertMarker = {},
                onOpenFocusedMarkerDetail = {},
                onToggleBottomPanel = {},
                onToggleMapOverlays = {},
                showMapPreview = false
            )
    }
}

@Composable
private fun QaStatusScreen(title: String, message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(PoliDimens.SectionPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
        ) {
            Text(text = title)
            Text(text = message)
        }
    }
}

private sealed interface SearchMapQaLoadState {
    object Loading : SearchMapQaLoadState

    data class Ready(
        val searchMapState: SearchMapUiState,
        val mapState: MapLibreRuntimeMapState
    ) : SearchMapQaLoadState

    data class Unavailable(
        val message: String
    ) : SearchMapQaLoadState
}

private suspend fun loadLiveSearchMapQaState(
    context: android.content.Context,
    snapshot: SuriMapSessionSnapshot
): SearchMapQaLoadState.Ready {
    val policePhoneContext = snapshot.toPolicePhoneContext() ?: error("missing police phone context")
    val searchMapSessionContext = snapshot.toSearchMapSessionContext() ?: error("missing incident context")
    val apiBaseUrl = policePhoneContext.apiBaseUrl
    val accessTokenProvider = policePhoneContext.accessTokenProvider()
    val database = SuriMapDatabaseProvider.database(context)
    val outboxDao = database.outboxDao()
    val loader =
        SearchMapStateLoader(
            incidentDetail = { incidentId ->
                IncidentReadRepository(
                    apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
                    accessTokenProvider = accessTokenProvider
                ).detail(incidentId)
            },
            overallSearchArea = { incidentId ->
                SearchAreaReadRepository(
                    apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
                    accessTokenProvider = accessTokenProvider
                ).activeOverall(incidentId)
            },
            opSearchAreas = { incidentId, opId ->
                SearchAreaReadRepository(
                    apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
                    accessTokenProvider = accessTokenProvider
                ).list(incidentId = incidentId, opId = opId, status = "ACTIVE")
            },
            searchPaths = { query ->
                SearchPathRepository(
                    apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
                    accessTokenProvider = accessTokenProvider
                ).listSearchPaths(query)
            },
            liveMarkers = { query ->
                MarkerRepository(
                    apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
                    accessTokenProvider = accessTokenProvider
                ).listMarkers(query)
            },
            initialMarkers = { incidentId, policePhoneId ->
                OfflinePackageRepository(
                    apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
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
    val searchMapState = loader.load(searchMapSessionContext)
    val mapState =
        MapLibreRuntimeMapState(
            apiBaseUrl = policePhoneContext.tileBaseUrl,
            policePhoneId = policePhoneContext.policePhoneId,
            accessToken = policePhoneContext.accessToken
        )
    return SearchMapQaLoadState.Ready(
        searchMapState = searchMapState,
        mapState = mapState
    )
}

private fun android.content.Context.createQaMarkerPhotoCaptureUri(markerId: String): Uri? =
    runCatching {
        val imageDir = File(cacheDir, "marker-photos").apply { mkdirs() }
        val safeMarkerId =
            markerId
                .filter { char -> char.isLetterOrDigit() || char == '-' || char == '_' }
                .ifBlank { "qa-marker" }
        val imageFile = File.createTempFile("qa-marker-$safeMarkerId-", ".jpg", imageDir)
        FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
    }.getOrNull()
