package com.surimap.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.surimap.R
import com.surimap.core.map.MapLibreRuntimeMapState
import com.surimap.core.map.MapLibreGeometryOverlay
import com.surimap.core.map.MapLibreGeometryOverlayKind
import com.surimap.core.map.MapLibreViewportBounds
import com.surimap.core.map.SuriMapLibreMap
import com.surimap.feature.alert.ui.IncidentAlertBanner
import com.surimap.feature.alert.ui.IncidentAlertUiState
import com.surimap.feature.handover.ui.HandoverPromptUiState
import com.surimap.ui.HandoverPromptBanner
import com.surimap.ui.components.PoliBanner
import com.surimap.ui.components.PoliBannerVariant
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonSize
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliRow
import com.surimap.ui.theme.PoliBgBase
import com.surimap.ui.theme.PoliBgInput
import com.surimap.ui.theme.PoliBgSurface
import com.surimap.ui.theme.PoliBorder
import com.surimap.ui.theme.PoliBorderStrong
import com.surimap.ui.theme.PoliCurrent
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliEmphasis
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgPrimary
import com.surimap.ui.theme.PoliFgSecondary
import com.surimap.ui.theme.PoliPrimaryBorder
import com.surimap.ui.theme.PoliWarning
import com.surimap.ui.theme.SuriMapTheme

enum class SearchMapSyncStatus {
    Idle,
    Offline,
    Sending,
    Synced
}

enum class SearchLifecycleStatus {
    Active,
    Paused,
    Stopped,
    OpRequired,
    OpTransition
}

enum class SearchLayerKind {
    Overall,
    Unit,
    Team,
    Path,
    Marker
}

data class SearchMapViewportBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
)

data class SearchMapLayerUiState(
    val label: String,
    val kind: SearchLayerKind,
    val highlighted: Boolean = false,
    val overlayId: String? = null,
    val geoJson: String? = null
)

data class SearchMapUiState(
    val incidentTitle: String,
    val missingPersonSummary: String,
    val opLabel: String,
    val dutyShiftLabel: String,
    val assignmentLabel: String,
    val syncStatus: SearchMapSyncStatus,
    val lifecycleStatus: SearchLifecycleStatus,
    val unsentCount: Int,
    val oldestPendingMinutes: Int?,
    val blockedOutboxCount: Int,
    val elapsedLabel: String,
    val movementSummary: String,
    val layers: List<SearchMapLayerUiState>,
    val viewportBounds: SearchMapViewportBounds? = null,
    val handoverPrompt: HandoverPromptUiState?,
    val incidentAlert: IncidentAlertUiState? = null,
    val focusedMarkerId: String? = null,
    val bottomPanelExpanded: Boolean = true,
    val mapOverlaysVisible: Boolean = true
) {
    val canWritePath: Boolean = lifecycleStatus == SearchLifecycleStatus.Active
    val canCreateMarker: Boolean = lifecycleStatus == SearchLifecycleStatus.Active
    val canStopSearch: Boolean =
        lifecycleStatus == SearchLifecycleStatus.Active || lifecycleStatus == SearchLifecycleStatus.Paused
    val shouldOpenBlockedOutbox: Boolean = blockedOutboxCount > 0
    val showHandoverPrompt: Boolean = handoverPrompt?.shouldShow == true
    val focusedMarkerLayer: SearchMapLayerUiState? =
        focusedMarkerId
            ?.takeIf(String::isNotBlank)
            ?.let { markerId ->
                layers.firstOrNull { layer -> layer.kind == SearchLayerKind.Marker && layer.overlayId == markerId }
                    ?.copy(highlighted = true)
            }
    val markerDetailTargetId: String? =
        focusedMarkerId?.takeIf(String::isNotBlank)
            ?: layers.firstOrNull { layer -> layer.kind == SearchLayerKind.Marker && !layer.overlayId.isNullOrBlank() }
                ?.overlayId
    val markerFocusLabel: String? =
        focusedMarkerId
            ?.takeIf(String::isNotBlank)
            ?.let { markerId ->
                val label = focusedMarkerLayer?.label ?: markerId
                "마커 포커스 · $label"
            }
    val focusedMarkerViewportBounds: SearchMapViewportBounds? =
        focusedMarkerLayer?.geoJson?.pointViewportBounds()
    val canFocusOverallSearchArea: Boolean =
        layers.any { layer -> layer.kind == SearchLayerKind.Overall && !layer.geoJson.isNullOrBlank() }
    val canFocusUnitSearchArea: Boolean =
        layers.any { layer -> layer.kind == SearchLayerKind.Unit && !layer.geoJson.isNullOrBlank() }
    val canOpenMarkerDetail: Boolean = !markerDetailTargetId.isNullOrBlank()

    val syncLabel: String =
        when (syncStatus) {
            SearchMapSyncStatus.Idle -> "동기화 준비"
            SearchMapSyncStatus.Synced -> "동기화 정상"
            SearchMapSyncStatus.Offline -> {
                val minutes = oldestPendingMinutes ?: 0
                "미전송 $unsentCount · ${minutes}분"
            }
            SearchMapSyncStatus.Sending -> "전송 중 · $unsentCount"
        }

    val lifecycleTitle: String =
        when (lifecycleStatus) {
            SearchLifecycleStatus.Active -> "수색 기록 중"
            SearchLifecycleStatus.Paused -> "수색 일시정지"
            SearchLifecycleStatus.Stopped -> "수색 경로 종료"
            SearchLifecycleStatus.OpRequired -> "OP 확인 필요"
            SearchLifecycleStatus.OpTransition -> "OP 전환 확인 필요"
        }

    val lifecycleMessage: String =
        when (lifecycleStatus) {
            SearchLifecycleStatus.Active -> "GPS 5초 수집 · 서버 전송 10초 batch 기준"
            SearchLifecycleStatus.Paused -> "경로 batch 전송과 마커 생성이 일시 차단됩니다."
            SearchLifecycleStatus.Stopped -> "현재 경로는 종료되었습니다. 다시 시작하면 새 SearchPath가 생성됩니다."
            SearchLifecycleStatus.OpRequired -> "current OP 누락 또는 조회 실패입니다. 경로·마커 기록 차단 상태입니다."
            SearchLifecycleStatus.OpTransition -> "OP 전환 중입니다. 이전 OP 기록은 readonly로 유지됩니다."
        }

    val primaryActionLabel: String =
        when (lifecycleStatus) {
            SearchLifecycleStatus.Active -> "일시정지"
            SearchLifecycleStatus.Paused -> "재개"
            SearchLifecycleStatus.Stopped -> "수색 시작"
            SearchLifecycleStatus.OpRequired -> "OP 다시 확인"
            SearchLifecycleStatus.OpTransition -> "OP 변경 확인"
        }

    fun visibleText(): List<String> =
        buildList {
            add(incidentTitle)
            add(missingPersonSummary)
            add(opLabel)
            assignmentLabel.takeIf(String::isNotBlank)?.let(::add)
            add(syncLabel)
            add(lifecycleTitle)
            add(lifecycleMessage)
            add(if (canWritePath) "경로 기록 가능" else "경로 기록 차단")
            add(if (canCreateMarker) "마커 생성 가능" else "마커 생성 차단")
            add(if (bottomPanelExpanded) "지도 정보 펼침" else "지도 정보 접힘")
            add(if (mapOverlaysVisible) "지도 오버레이 표시" else "지도 오버레이 숨김")
            if (mapOverlaysVisible) {
                add("전체 수색구역")
                add("부대 수색구역")
                add("마커 상세")
            }
            if (bottomPanelExpanded) {
                add(primaryActionLabel)
                if (canStopSearch) {
                    add("종료")
                }
                add("인수인계")
                add("마커 생성")
            }
            if (showHandoverPrompt) {
                add("이전 근무 기록 있음")
            }
            markerFocusLabel?.let(::add)
            incidentAlert?.visibleText()?.forEach(::add)
            if (blockedOutboxCount > 0) {
                add("미전송 ${blockedOutboxCount}건 처리 불가")
            }
        }

    fun withFocusedMarker(markerId: String?): SearchMapUiState =
        copy(focusedMarkerId = markerId?.takeIf(String::isNotBlank))

    fun centerOnSearchLayer(kind: SearchLayerKind): SearchMapUiState {
        val bounds =
            layers.firstOrNull { layer -> layer.kind == kind && !layer.geoJson.isNullOrBlank() }
                ?.geoJson
                ?.geometryViewportBounds()
                ?: return this
        return copy(
            viewportBounds = bounds,
            focusedMarkerId = null
        )
    }

    companion object {
        fun active(
            syncStatus: SearchMapSyncStatus = SearchMapSyncStatus.Synced,
            unsentCount: Int = 0,
            oldestPendingMinutes: Int? = null,
            blockedOutboxCount: Int = 0,
            hasUnreadHandover: Boolean = false,
            incidentAlert: IncidentAlertUiState? = null
        ): SearchMapUiState =
            base(
                syncStatus = syncStatus,
                lifecycleStatus = SearchLifecycleStatus.Active,
                unsentCount = unsentCount,
                oldestPendingMinutes = oldestPendingMinutes,
                blockedOutboxCount = blockedOutboxCount,
                handoverPrompt = if (hasUnreadHandover) HandoverPromptUiState.unreadSample() else null,
                incidentAlert = incidentAlert
            )

        fun paused(): SearchMapUiState =
            base(lifecycleStatus = SearchLifecycleStatus.Paused, syncStatus = SearchMapSyncStatus.Idle)

        fun stopped(): SearchMapUiState =
            base(lifecycleStatus = SearchLifecycleStatus.Stopped, syncStatus = SearchMapSyncStatus.Synced)

        fun opRequired(): SearchMapUiState =
            base(lifecycleStatus = SearchLifecycleStatus.OpRequired, syncStatus = SearchMapSyncStatus.Idle)

        fun opTransition(): SearchMapUiState =
            base(lifecycleStatus = SearchLifecycleStatus.OpTransition, syncStatus = SearchMapSyncStatus.Idle)

        fun offline(unsentCount: Int, oldestPendingMinutes: Int): SearchMapUiState =
            active(
                syncStatus = SearchMapSyncStatus.Offline,
                unsentCount = unsentCount,
                oldestPendingMinutes = oldestPendingMinutes
            )

        fun sending(unsentCount: Int): SearchMapUiState =
            active(syncStatus = SearchMapSyncStatus.Sending, unsentCount = unsentCount)

        private fun base(
            syncStatus: SearchMapSyncStatus = SearchMapSyncStatus.Synced,
            lifecycleStatus: SearchLifecycleStatus,
            unsentCount: Int = 0,
            oldestPendingMinutes: Int? = null,
            blockedOutboxCount: Int = 0,
            handoverPrompt: HandoverPromptUiState? = null,
            incidentAlert: IncidentAlertUiState? = null
        ): SearchMapUiState =
            SearchMapUiState(
                incidentTitle = "광주 북구 무등산 증심사 계곡 일대 실종자 수색",
                missingPersonSummary = "60대 여성 · 회색 점퍼 · 검은 바지 · 치매 증상 · 보행 느림",
                opLabel = "OP 3차",
                dutyShiftLabel = "",
                assignmentLabel = "기동대 1부대 A팀 담당 구역",
                syncStatus = syncStatus,
                lifecycleStatus = lifecycleStatus,
                unsentCount = unsentCount,
                oldestPendingMinutes = oldestPendingMinutes,
                blockedOutboxCount = blockedOutboxCount,
                elapsedLabel = "04:21",
                movementSummary = "도보 1.2km · GPS 5초 / 전송 10초",
                layers =
                listOf(
                    SearchMapLayerUiState("전체 수색 구역", SearchLayerKind.Overall),
                    SearchMapLayerUiState("기동대 1부대", SearchLayerKind.Unit),
                    SearchMapLayerUiState("A팀 담당 구역", SearchLayerKind.Team, highlighted = true),
                    SearchMapLayerUiState("현재 경로", SearchLayerKind.Path, highlighted = true),
                    SearchMapLayerUiState("단서", SearchLayerKind.Marker, highlighted = true)
                ),
                handoverPrompt = handoverPrompt,
                incidentAlert = incidentAlert
            )
    }
}

@Composable
fun SearchMapScreen(
    state: SearchMapUiState,
    mapState: MapLibreRuntimeMapState = MapLibreRuntimeMapState(),
    onBack: () -> Unit,
    onPrimaryLifecycleAction: () -> Unit,
    onStopSearch: () -> Unit,
    onCreateMarker: () -> Unit,
    onOpenHandover: () -> Unit,
    onOpenBlockedOutbox: () -> Unit,
    onDismissIncidentAlert: () -> Unit,
    onOpenIncidentAlertMarker: (String) -> Unit,
    onOpenFocusedMarkerDetail: (String) -> Unit,
    onCenterCurrentLocation: () -> Unit,
    onFocusOverallSearchArea: () -> Unit,
    onFocusUnitSearchArea: () -> Unit,
    onToggleBottomPanel: () -> Unit,
    onToggleMapOverlays: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(PoliBgBase)) {
        SearchMapHeader(state = state, onBack = onBack)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
        ) {
            state.incidentAlert?.let { alert ->
                IncidentAlertBanner(
                    state = alert,
                    onConfirm = onDismissIncidentAlert,
                    onOpenMap = onOpenIncidentAlertMarker,
                    modifier = Modifier.padding(horizontal = PoliDimens.SectionPadding)
                )
            }
            if (state.showHandoverPrompt) {
                HandoverPromptBanner(
                    onOpenHandover = onOpenHandover,
                    modifier = Modifier.padding(horizontal = PoliDimens.SectionPadding)
                )
            }
            if (state.lifecycleStatus == SearchLifecycleStatus.OpRequired ||
                state.lifecycleStatus == SearchLifecycleStatus.OpTransition
            ) {
                PoliBanner(
                    text = state.lifecycleMessage,
                    variant = PoliBannerVariant.Warn,
                    modifier = Modifier.padding(horizontal = PoliDimens.SectionPadding)
                )
            }
            if (state.shouldOpenBlockedOutbox) {
                BlockedOutboxNotice(state = state, onOpenBlockedOutbox = onOpenBlockedOutbox)
            }
            SearchMapShell(
                state = state,
                mapState = mapState,
                onOpenFocusedMarkerDetail = onOpenFocusedMarkerDetail,
                onCenterCurrentLocation = onCenterCurrentLocation,
                onFocusOverallSearchArea = onFocusOverallSearchArea,
                onFocusUnitSearchArea = onFocusUnitSearchArea,
                onToggleMapOverlays = onToggleMapOverlays,
                modifier = Modifier.weight(1f)
            )
        }

        SearchBottomPanel(
            state = state,
            onPrimaryLifecycleAction = onPrimaryLifecycleAction,
            onStopSearch = onStopSearch,
            onOpenHandover = onOpenHandover,
            onCreateMarker = onCreateMarker,
            onToggleBottomPanel = onToggleBottomPanel
        )
    }
}

@Composable
private fun SearchMapHeader(state: SearchMapUiState, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PoliDimens.SectionPadding, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(PoliDimens.TouchMin).clickable(onClick = onBack),
                shape = MaterialTheme.shapes.medium,
                color = PoliBgInput,
                border = androidx.compose.foundation.BorderStroke(1.dp, PoliBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "<", style = MaterialTheme.typography.titleMedium, color = PoliFgPrimary)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = state.incidentTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = PoliFgPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.missingPersonSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PoliFgMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)
        ) {
            PoliChip(text = state.opLabel, variant = PoliChipVariant.Outbox)
            if (state.showSyncChip) {
                PoliChip(text = state.syncLabel, variant = state.syncVariant)
            }
        }
        if (state.assignmentLabel.isNotBlank()) {
            Text(
                text = state.assignmentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = PoliFgSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BlockedOutboxNotice(state: SearchMapUiState, onOpenBlockedOutbox: () -> Unit) {
    PoliCard(modifier = Modifier.padding(horizontal = PoliDimens.SectionPadding), strong = true) {
        PoliRow(
            title = "미전송 ${state.blockedOutboxCount}건 처리 불가",
            subtitle = "정상 대기 큐가 아니라 사용자 조치가 필요한 항목입니다."
        ) {
            PoliButton(
                text = "진단 확인",
                onClick = onOpenBlockedOutbox,
                size = PoliButtonSize.Small,
                variant = PoliButtonVariant.Secondary
            )
        }
    }
}

@Composable
private fun SearchMapShell(
    state: SearchMapUiState,
    mapState: MapLibreRuntimeMapState,
    onOpenFocusedMarkerDetail: (String) -> Unit,
    onCenterCurrentLocation: () -> Unit,
    onFocusOverallSearchArea: () -> Unit,
    onFocusUnitSearchArea: () -> Unit,
    onToggleMapOverlays: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mapLoadFailure by remember { mutableStateOf<String?>(null) }
    val runtimeMapState = state.toRuntimeMapState(mapState)

    Box(modifier = modifier.fillMaxWidth().background(PoliBgInput)) {
        SuriMapLibreMap(
            state = runtimeMapState,
            modifier = Modifier.fillMaxSize(),
            onLoadFailed = { reason -> mapLoadFailure = reason }
        )

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(PoliDimens.Space3),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)
        ) {
            MapOverlayToggle(
                visible = state.mapOverlaysVisible,
                onClick = onToggleMapOverlays
            )
            if (state.mapOverlaysVisible) {
                state.markerFocusLabel?.let { focusLabel ->
                    PoliChip(text = focusLabel, variant = PoliChipVariant.Bad)
                }
                MapActionButton(
                    text = "전체 수색구역",
                    onClick = onFocusOverallSearchArea,
                    enabled = state.canFocusOverallSearchArea
                )
                MapActionButton(
                    text = "부대 수색구역",
                    onClick = onFocusUnitSearchArea,
                    enabled = state.canFocusUnitSearchArea
                )
                MarkerDetailButton(
                    onClick =
                    state.markerDetailTargetId
                        ?.takeIf { state.canOpenMarkerDetail }
                        ?.let { markerId -> { onOpenFocusedMarkerDetail(markerId) } },
                    enabled = state.canOpenMarkerDetail
                )
            }
        }

        CurrentLocationButton(
            onClick = onCenterCurrentLocation,
            modifier = Modifier.align(Alignment.BottomEnd).padding(PoliDimens.Space4)
        )

        mapLoadFailure?.let {
            PoliBanner(
                text = "지도 로드 실패 · 기록은 계속 가능합니다.",
                variant = PoliBannerVariant.Warn,
                modifier = Modifier.align(Alignment.BottomStart).padding(PoliDimens.Space4)
            )
        }
    }
}

@Composable
private fun MapOverlayToggle(visible: Boolean, onClick: () -> Unit) {
    MapActionButton(
        text = if (visible) "정보 숨김" else "정보 표시",
        onClick = onClick
    )
}

@Composable
private fun MarkerDetailButton(onClick: (() -> Unit)?, enabled: Boolean) {
    MapActionButton(
        text = "마커 상세",
        onClick = onClick,
        accent = enabled,
        enabled = enabled
    )
}

@Composable
private fun CurrentLocationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(56.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_my_location),
            contentDescription = "내 위치",
            tint = Color.Unspecified,
            modifier = Modifier.size(38.dp)
        )
    }
}

@Composable
private fun MapActionButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Boolean = false,
    enabled: Boolean = true
) {
    val buttonModifier =
        modifier
            .heightIn(min = PoliDimens.TouchMin)
            .then(
                if (enabled && onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    Surface(
        modifier = buttonModifier,
        shape = MaterialTheme.shapes.small,
        color = PoliBgSurface,
        contentColor =
        when {
            !enabled -> PoliFgMuted
            accent -> PoliCurrent
            else -> PoliFgSecondary
        },
        border =
        androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                !enabled -> PoliBorder
                accent -> PoliCurrent
                else -> PoliBorderStrong
            }
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp).widthIn(min = 88.dp, max = 132.dp).heightIn(min = PoliDimens.TouchMin),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SearchBottomPanel(
    state: SearchMapUiState,
    onPrimaryLifecycleAction: () -> Unit,
    onStopSearch: () -> Unit,
    onOpenHandover: () -> Unit,
    onCreateMarker: () -> Unit,
    onToggleBottomPanel: () -> Unit
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (state.bottomPanelExpanded) 220.dp else 104.dp)
            .background(PoliBgSurface)
            .padding(PoliDimens.SectionPadding),
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
    ) {
        SearchStatusCard(state = state, onToggleBottomPanel = onToggleBottomPanel)
        if (state.bottomPanelExpanded) {
            WriteAvailabilityRow(state = state)
            Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
                PoliButton(
                    text = state.primaryActionLabel,
                    onClick = onPrimaryLifecycleAction,
                    modifier = Modifier.weight(1f),
                    variant = if (state.lifecycleStatus == SearchLifecycleStatus.OpRequired) {
                        PoliButtonVariant.Secondary
                    } else {
                        PoliButtonVariant.Primary
                    }
                )
                PoliButton(
                    text = "종료",
                    onClick = onStopSearch,
                    enabled = state.canStopSearch,
                    variant = PoliButtonVariant.Danger
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
                PoliButton(
                    text = "인수인계",
                    onClick = onOpenHandover,
                    modifier = Modifier.weight(1f),
                    variant = PoliButtonVariant.Secondary,
                    size = PoliButtonSize.Large
                )
                PoliButton(
                    text = "마커 생성",
                    onClick = onCreateMarker,
                    modifier = Modifier.weight(1.25f),
                    enabled = state.canCreateMarker,
                    size = PoliButtonSize.Large
                )
            }
        }
    }
}

@Composable
private fun SearchStatusCard(state: SearchMapUiState, onToggleBottomPanel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = PoliBgBase,
        contentColor = PoliFgPrimary,
        border = androidx.compose.foundation.BorderStroke(1.dp, PoliBorder)
    ) {
        Row(
            modifier = Modifier.padding(PoliDimens.Space4),
            horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchStatusDot(state.lifecycleStatus)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)) {
                Text(
                    text = state.lifecycleTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = state.movementSummary, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
            }
            Text(text = state.elapsedLabel, style = MaterialTheme.typography.titleMedium)
            PoliButton(
                text = if (state.bottomPanelExpanded) "접기" else "펼침",
                onClick = onToggleBottomPanel,
                size = PoliButtonSize.Small,
                variant = PoliButtonVariant.Secondary
            )
        }
    }
}

@Composable
private fun SearchStatusDot(status: SearchLifecycleStatus) {
    val color =
        when (status) {
            SearchLifecycleStatus.Active -> PoliEmphasis
            SearchLifecycleStatus.Paused,
            SearchLifecycleStatus.Stopped -> PoliWarning
            SearchLifecycleStatus.OpRequired,
            SearchLifecycleStatus.OpTransition -> PoliPrimaryBorder
        }
    Surface(modifier = Modifier.size(12.dp), shape = MaterialTheme.shapes.extraLarge, color = color) {}
}

@Composable
private fun WriteAvailabilityRow(state: SearchMapUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
        PoliChip(
            text = if (state.canWritePath) "경로 기록 가능" else "경로 기록 차단",
            variant = if (state.canWritePath) PoliChipVariant.Good else PoliChipVariant.Warn
        )
        PoliChip(
            text = if (state.canCreateMarker) "마커 생성 가능" else "마커 생성 차단",
            variant = if (state.canCreateMarker) PoliChipVariant.Good else PoliChipVariant.Warn
        )
    }
}

private val SearchMapUiState.syncVariant: PoliChipVariant
    get() =
        when (syncStatus) {
            SearchMapSyncStatus.Idle -> PoliChipVariant.Neutral
            SearchMapSyncStatus.Synced -> PoliChipVariant.Good
            SearchMapSyncStatus.Offline -> PoliChipVariant.Warn
            SearchMapSyncStatus.Sending -> PoliChipVariant.Outbox
        }

private val SearchMapUiState.showSyncChip: Boolean
    get() = syncStatus == SearchMapSyncStatus.Offline || syncStatus == SearchMapSyncStatus.Sending

private fun SearchMapUiState.toRuntimeMapState(base: MapLibreRuntimeMapState): MapLibreRuntimeMapState {
    return base.copy(
        initialBounds =
        (focusedMarkerViewportBounds ?: viewportBounds)?.let { bounds ->
            MapLibreViewportBounds(
                south = bounds.south,
                west = bounds.west,
                north = bounds.north,
                east = bounds.east
            )
        } ?: base.initialBounds,
        geometryOverlays =
        layers.mapNotNull { layer ->
            val geoJson = layer.geoJson?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val focused = focusedMarkerId != null && layer.kind == SearchLayerKind.Marker && layer.overlayId == focusedMarkerId
            MapLibreGeometryOverlay(
                id = layer.overlayId ?: layer.label,
                kind = layer.kind.toMapLibreGeometryOverlayKind(),
                geoJson = geoJson,
                highlighted = layer.highlighted || focused,
                label = layer.label
            )
        }
    )
}

private fun SearchLayerKind.toMapLibreGeometryOverlayKind(): MapLibreGeometryOverlayKind =
    when (this) {
        SearchLayerKind.Overall -> MapLibreGeometryOverlayKind.Overall
        SearchLayerKind.Unit -> MapLibreGeometryOverlayKind.Unit
        SearchLayerKind.Team -> MapLibreGeometryOverlayKind.Team
        SearchLayerKind.Path -> MapLibreGeometryOverlayKind.Path
        SearchLayerKind.Marker -> MapLibreGeometryOverlayKind.Marker
    }

private fun String.pointViewportBounds(): SearchMapViewportBounds? {
    val match = COORDINATE_PAIR.find(this) ?: return null
    val lon = match.groupValues[1].toDoubleOrNull() ?: return null
    val lat = match.groupValues[2].toDoubleOrNull() ?: return null
    val delta = MARKER_FOCUS_BOUNDS_DELTA
    return SearchMapViewportBounds(
        south = lat - delta,
        west = lon - delta,
        north = lat + delta,
        east = lon + delta
    )
}

private fun String.geometryViewportBounds(): SearchMapViewportBounds? {
    return COORDINATE_PAIR.findAll(this)
        .mapNotNull { match ->
            val longitude = match.groupValues[1].toDoubleOrNull()
            val latitude = match.groupValues[2].toDoubleOrNull()
            if (longitude != null && latitude != null && longitude.isFinite() && latitude.isFinite()) {
                longitude to latitude
            } else {
                null
            }
        }
        .toList()
        .toViewportBounds()
}

private fun List<Pair<Double, Double>>.toViewportBounds(): SearchMapViewportBounds? {
    if (isEmpty()) {
        return null
    }

    val longitudes = map { it.first }
    val latitudes = map { it.second }
    val south = latitudes.minOrNull() ?: return null
    val west = longitudes.minOrNull() ?: return null
    val north = latitudes.maxOrNull() ?: return null
    val east = longitudes.maxOrNull() ?: return null
    val latDelta = if (south == north) MARKER_FOCUS_BOUNDS_DELTA else 0.0
    val lonDelta = if (west == east) MARKER_FOCUS_BOUNDS_DELTA else 0.0
    return SearchMapViewportBounds(
        south = south - latDelta,
        west = west - lonDelta,
        north = north + latDelta,
        east = east + lonDelta
    ).takeIf { bounds ->
        bounds.south.isFinite() &&
            bounds.west.isFinite() &&
            bounds.north.isFinite() &&
            bounds.east.isFinite()
    }
}

private val COORDINATE_PAIR = Regex("""\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*]""")
private const val MARKER_FOCUS_BOUNDS_DELTA = 0.001

fun sampleSearchMapState(): SearchMapUiState =
    SearchMapUiState.active(
        syncStatus = SearchMapSyncStatus.Offline,
        unsentCount = 1,
        oldestPendingMinutes = 2,
        hasUnreadHandover = true,
        incidentAlert = IncidentAlertUiState.personFoundSample()
    )

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun SearchMapScreenPreview() {
    SuriMapTheme {
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
            onOpenFocusedMarkerDetail = {},
            onCenterCurrentLocation = {},
            onFocusOverallSearchArea = {},
            onFocusUnitSearchArea = {},
            onToggleBottomPanel = {},
            onToggleMapOverlays = {}
        )
    }
}
