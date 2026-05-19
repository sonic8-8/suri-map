package com.surimap.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.surimap.ui.components.PoliAppBar
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
import com.surimap.ui.theme.PoliSuccess
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
    Marker,
    CurrentLocation
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

data class SearchMapAreaFocusTarget(
    val label: String,
    val kind: SearchLayerKind,
    val overlayId: String?
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
    val topHeaderExpanded: Boolean = false,
    val bottomPanelExpanded: Boolean = true,
    val mapOverlaysVisible: Boolean = true,
    val activeSearchPathId: String? = null,
    val activeSearchPathStartedAtEpochMs: Long? = null
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
    val canFocusTeamSearchArea: Boolean =
        layers.any { layer -> layer.kind == SearchLayerKind.Team && !layer.geoJson.isNullOrBlank() }
    val canOpenMarkerDetail: Boolean = !markerDetailTargetId.isNullOrBlank()
    val overallSearchAreaTargets: List<SearchMapAreaFocusTarget> = areaFocusTargets(SearchLayerKind.Overall)
    val unitSearchAreaTargets: List<SearchMapAreaFocusTarget> = areaFocusTargets(SearchLayerKind.Unit)
    val teamSearchAreaTargets: List<SearchMapAreaFocusTarget> = areaFocusTargets(SearchLayerKind.Team)

    val syncLabel: String =
        when (syncStatus) {
            SearchMapSyncStatus.Idle -> "대기"
            SearchMapSyncStatus.Synced -> "동기화"
            SearchMapSyncStatus.Offline -> {
                val minutes = oldestPendingMinutes ?: 0
                "미전송 $unsentCount · ${minutes}분"
            }
            SearchMapSyncStatus.Sending -> "전송 중 · $unsentCount"
        }
    val assignmentDisplayLabel: String =
        assignmentLabel.takeIf(String::isNotBlank) ?: "담당구역 미배정"

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
            add(assignmentDisplayLabel)
            add(syncLabel)
            add(if (topHeaderExpanded) "상단 정보 펼침" else "상단 정보 접힘")
            add(lifecycleTitle)
            add(lifecycleMessage)
            add(if (canWritePath) "경로 기록 가능" else "경로 기록 차단")
            add(if (canCreateMarker) "마커 생성 가능" else "마커 생성 차단")
            add(if (bottomPanelExpanded) "지도 정보 펼침" else "지도 정보 접힘")
            add(if (mapOverlaysVisible) "지도 오버레이 표시" else "지도 오버레이 숨김")
            if (mapOverlaysVisible) {
                add("전체 수색구역")
                add("부대 수색구역")
                add("팀 담당구역")
                add("마커 상세")
                unitSearchAreaTargets.forEach { add(it.label) }
                teamSearchAreaTargets.forEach { add(it.label) }
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

    fun centerOnSearchLayer(kind: SearchLayerKind, overlayId: String? = null): SearchMapUiState {
        val bounds =
            layers.firstOrNull { layer ->
                layer.kind == kind &&
                    !layer.geoJson.isNullOrBlank() &&
                    (overlayId == null || layer.overlayId == overlayId)
            }
                ?.geoJson
                ?.geometryViewportBounds()
                ?: return this
        return copy(
            viewportBounds = bounds,
            focusedMarkerId = null
        )
    }

    private fun areaFocusTargets(kind: SearchLayerKind): List<SearchMapAreaFocusTarget> =
        layers
            .filter { layer -> layer.kind == kind && !layer.geoJson.isNullOrBlank() }
            .mapIndexed { index, layer ->
                SearchMapAreaFocusTarget(
                    label = layer.label.takeIf(String::isNotBlank) ?: "${kind.areaLabel()} ${index + 1}",
                    kind = kind,
                    overlayId = layer.overlayId
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
    showMapPreview: Boolean = false,
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
    onFocusSearchArea: (SearchLayerKind, String?) -> Unit,
    onToggleHeaderPanel: () -> Unit,
    onToggleBottomPanel: () -> Unit,
    onToggleMapOverlays: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(PoliBgBase)) {
        SearchMapHeader(state = state, onBack = onBack, onToggleExpanded = onToggleHeaderPanel)

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
            SearchMapShell(
                state = state,
                mapState = mapState,
                showMapPreview = showMapPreview,
                onOpenFocusedMarkerDetail = onOpenFocusedMarkerDetail,
                onOpenBlockedOutbox = onOpenBlockedOutbox,
                onCenterCurrentLocation = onCenterCurrentLocation,
                onFocusSearchArea = onFocusSearchArea,
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
private fun SearchMapHeader(state: SearchMapUiState, onBack: () -> Unit, onToggleExpanded: () -> Unit) {
    Column(
        modifier =
            Modifier
                .statusBarsPadding()
                .padding(bottom = PoliDimens.Space4),
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
    ) {
        PoliAppBar(
            title = state.incidentTitle,
            subtitle = state.missingPersonSummary,
            showBack = true,
            onBack = onBack
        )
        Surface(
            modifier =
                Modifier
                    .padding(horizontal = PoliDimens.SectionPadding)
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
            shape = MaterialTheme.shapes.medium,
            color = PoliBgSurface,
            contentColor = PoliFgPrimary,
            border = BorderStroke(1.dp, PoliBorderStrong)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = PoliDimens.Space4, vertical = PoliDimens.Space2),
                verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.opLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = PoliFgPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    PoliChip(text = state.syncLabel, variant = state.syncVariant)
                }
                Text(
                    text = "담당 · ${state.assignmentDisplayLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PoliFgPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.topHeaderExpanded) {
                    Text(
                        text = state.lifecycleTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = PoliFgMuted
                    )
                    Text(
                        text = state.lifecycleMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = PoliFgMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchMapShell(
    state: SearchMapUiState,
    mapState: MapLibreRuntimeMapState,
    showMapPreview: Boolean,
    onOpenFocusedMarkerDetail: (String) -> Unit,
    onOpenBlockedOutbox: () -> Unit,
    onCenterCurrentLocation: () -> Unit,
    onFocusSearchArea: (SearchLayerKind, String?) -> Unit,
    onToggleMapOverlays: () -> Unit,
    modifier: Modifier = Modifier
) {
    val runtimeMapState = state.toRuntimeMapState(mapState)
    var expandedAreaKind by remember { mutableStateOf<SearchLayerKind?>(null) }

    Box(modifier = modifier.fillMaxWidth().background(PoliBgInput)) {
        if (showMapPreview) {
            SearchMapPreviewScene(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SuriMapLibreMap(
                state = runtimeMapState,
                modifier = Modifier.fillMaxSize(),
                onLoadFailed = {}
            )
        }

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
                AreaFocusGroup(
                    title = "전체 수색구역",
                    targets = state.overallSearchAreaTargets,
                    expanded = expandedAreaKind == SearchLayerKind.Overall,
                    onToggleExpanded = {
                        expandedAreaKind =
                            if (expandedAreaKind == SearchLayerKind.Overall) null else SearchLayerKind.Overall
                    },
                    onFocus = { target ->
                        expandedAreaKind = null
                        onFocusSearchArea(target.kind, target.overlayId)
                    }
                )
                AreaFocusGroup(
                    title = "부대 수색구역",
                    targets = state.unitSearchAreaTargets,
                    expanded = expandedAreaKind == SearchLayerKind.Unit,
                    onToggleExpanded = {
                        expandedAreaKind =
                            if (expandedAreaKind == SearchLayerKind.Unit) null else SearchLayerKind.Unit
                    },
                    onFocus = { target ->
                        expandedAreaKind = null
                        onFocusSearchArea(target.kind, target.overlayId)
                    }
                )
                AreaFocusGroup(
                    title = "팀 담당구역",
                    targets = state.teamSearchAreaTargets,
                    expanded = expandedAreaKind == SearchLayerKind.Team,
                    onToggleExpanded = {
                        expandedAreaKind =
                            if (expandedAreaKind == SearchLayerKind.Team) null else SearchLayerKind.Team
                    },
                    onFocus = { target ->
                        expandedAreaKind = null
                        onFocusSearchArea(target.kind, target.overlayId)
                    }
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

        if (state.shouldOpenBlockedOutbox) {
            PoliChip(
                text = "처리불가 ${state.blockedOutboxCount}건",
                variant = PoliChipVariant.Bad,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(PoliDimens.Space3)
                        .clickable(onClick = onOpenBlockedOutbox)
            )
        }

        CurrentLocationButton(
            onClick = onCenterCurrentLocation,
            modifier = Modifier.align(Alignment.BottomEnd).padding(PoliDimens.Space4)
        )
    }
}

@Composable
private fun SearchMapPreviewScene(
    state: SearchMapUiState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val minDimension = size.minDimension

            drawRect(
                brush =
                Brush.verticalGradient(
                    colors =
                    listOf(
                        Color(0xFF0B1421),
                        Color(0xFF13263B),
                        Color(0xFF0A1220)
                    )
                )
            )

            drawCircle(
                color = PoliCurrent.copy(alpha = 0.12f),
                radius = minDimension * 0.34f,
                center = Offset(width * 0.82f, height * 0.18f)
            )
            drawCircle(
                color = PoliSuccess.copy(alpha = 0.08f),
                radius = minDimension * 0.26f,
                center = Offset(width * 0.22f, height * 0.72f)
            )
            drawCircle(
                color = PoliWarning.copy(alpha = 0.08f),
                radius = minDimension * 0.20f,
                center = Offset(width * 0.60f, height * 0.66f)
            )

            val gridColor = Color.White.copy(alpha = 0.05f)
            val verticalStep = (width / 8f).coerceAtLeast(72f)
            var x = 0f
            while (x <= width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
                x += verticalStep
            }
            val horizontalStep = (height / 7f).coerceAtLeast(72f)
            var y = 0f
            while (y <= height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
                y += horizontalStep
            }

            drawRoundRect(
                color = Color(0x1122D3EE),
                topLeft = Offset(width * 0.08f, height * 0.20f),
                size = Size(width * 0.32f, height * 0.18f),
                cornerRadius = CornerRadius(36f, 36f)
            )
            drawRoundRect(
                color = Color(0x0C22C55E),
                topLeft = Offset(width * 0.50f, height * 0.30f),
                size = Size(width * 0.28f, height * 0.20f),
                cornerRadius = CornerRadius(32f, 32f)
            )
            drawRoundRect(
                color = Color(0x10F59E0B),
                topLeft = Offset(width * 0.18f, height * 0.54f),
                size = Size(width * 0.18f, height * 0.12f),
                cornerRadius = CornerRadius(24f, 24f)
            )

            val streetA =
                Path().apply {
                    moveTo(width * 0.05f, height * 0.28f)
                    cubicTo(
                        width * 0.20f, height * 0.24f,
                        width * 0.34f, height * 0.30f,
                        width * 0.48f, height * 0.26f
                    )
                    cubicTo(
                        width * 0.60f, height * 0.22f,
                        width * 0.70f, height * 0.18f,
                        width * 0.95f, height * 0.24f
                    )
                }
            drawPath(
                path = streetA,
                color = Color.White.copy(alpha = 0.20f),
                style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = streetA,
                color = Color(0xFF9FB2C8).copy(alpha = 0.12f),
                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val streetB =
                Path().apply {
                    moveTo(width * 0.18f, height * 0.06f)
                    cubicTo(
                        width * 0.25f, height * 0.20f,
                        width * 0.22f, height * 0.34f,
                        width * 0.30f, height * 0.48f
                    )
                    cubicTo(
                        width * 0.38f, height * 0.63f,
                        width * 0.42f, height * 0.75f,
                        width * 0.36f, height * 0.94f
                    )
                }
            drawPath(
                path = streetB,
                color = Color.White.copy(alpha = 0.16f),
                style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val route =
                Path().apply {
                    moveTo(width * 0.12f, height * 0.76f)
                    cubicTo(
                        width * 0.22f, height * 0.67f,
                        width * 0.30f, height * 0.58f,
                        width * 0.42f, height * 0.62f
                    )
                    cubicTo(
                        width * 0.55f, height * 0.66f,
                        width * 0.63f, height * 0.50f,
                        width * 0.72f, height * 0.42f
                    )
                    cubicTo(
                        width * 0.79f, height * 0.35f,
                        width * 0.87f, height * 0.30f,
                        width * 0.93f, height * 0.28f
                    )
                }
            drawPath(
                path = route,
                color = PoliCurrent.copy(alpha = 0.22f),
                style = Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = route,
                color = PoliCurrent.copy(alpha = 0.92f),
                style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val markerPoints =
                listOf(
                    Offset(width * 0.18f, height * 0.72f),
                    Offset(width * 0.43f, height * 0.62f),
                    Offset(width * 0.72f, height * 0.42f),
                    Offset(width * 0.93f, height * 0.28f)
                )
            markerPoints.forEachIndexed { index, point ->
                val accent =
                    when (index) {
                        0 -> PoliSuccess
                        1 -> PoliCurrent
                        2 -> PoliWarning
                        else -> PoliEmphasis
                    }
                drawCircle(color = Color.Black.copy(alpha = 0.28f), radius = 9f, center = point)
                drawCircle(color = accent.copy(alpha = 0.95f), radius = 6.2f, center = point)
                drawCircle(color = Color.White.copy(alpha = 0.72f), radius = 2.1f, center = point)
            }

            drawCircle(
                color = PoliCurrent.copy(alpha = 0.22f),
                radius = 26f,
                center = Offset(width * 0.72f, height * 0.42f),
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = PoliCurrent.copy(alpha = 0.10f),
                radius = 52f,
                center = Offset(width * 0.72f, height * 0.42f),
                style = Stroke(width = 2f)
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(PoliDimens.Space3),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)
        ) {
            PoliChip(text = "지도 미리보기", variant = PoliChipVariant.Good)
            PoliChip(text = "실제 타일 지도 비율", variant = PoliChipVariant.Neutral)
        }

        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(PoliDimens.Space3),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2),
            horizontalAlignment = Alignment.End
        ) {
            PoliChip(text = state.syncLabel, variant = state.syncVariant)
            if (state.mapOverlaysVisible) {
                PoliChip(
                    text = "오버레이 ${state.layers.count { it.highlighted }}개",
                    variant = PoliChipVariant.Outbox
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(PoliDimens.Space4),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)
        ) {
            Text(
                text = "여기에 실제 지도 타일이 들어간다",
                style = MaterialTheme.typography.labelLarge,
                color = PoliFgPrimary
            )
            Text(
                text = "경로 선, 마커, 경계선은 이 영역 위에 겹쳐진다",
                style = MaterialTheme.typography.labelMedium,
                color = PoliFgMuted
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
private fun AreaFocusGroup(
    title: String,
    targets: List<SearchMapAreaFocusTarget>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onFocus: (SearchMapAreaFocusTarget) -> Unit
) {
    val enabled = targets.isNotEmpty()
    val singleTarget = targets.singleOrNull()
    MapActionButton(
        text =
        when {
            targets.size > 1 -> "$title ${targets.size}"
            else -> title
        },
        onClick =
        when {
            !enabled -> null
            singleTarget != null -> { { onFocus(singleTarget) } }
            else -> onToggleExpanded
        },
        enabled = enabled
    )
    if (expanded && targets.size > 1) {
        targets.forEachIndexed { index, target ->
            MapActionButton(
                text = "${index + 1}. ${target.label}",
                onClick = { onFocus(target) },
                accent = true
            )
        }
    }
}

@Composable
private fun CurrentLocationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier =
            modifier
                .size(52.dp)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = Color.White,
        contentColor = PoliFgPrimary,
        border = BorderStroke(1.dp, PoliBorderStrong)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = R.drawable.ic_my_location),
                contentDescription = "내 위치",
                tint = Color.Unspecified,
                modifier = Modifier.size(34.dp)
            )
        }
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
            .heightIn(min = 34.dp)
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
            modifier = Modifier.padding(horizontal = 10.dp).widthIn(min = 72.dp, max = 122.dp).height(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
            .navigationBarsPadding()
            .padding(PoliDimens.SectionPadding),
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
    ) {
        SearchStatusCard(state = state, onToggleBottomPanel = onToggleBottomPanel)
        if (state.bottomPanelExpanded) {
            WriteAvailabilityRow(state = state)
        }

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
        if (state.bottomPanelExpanded) {
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = state.elapsedLabel, style = MaterialTheme.typography.titleMedium)
                PoliButton(
                    text = if (state.bottomPanelExpanded) "접기" else "펼치기",
                    onClick = onToggleBottomPanel,
                    size = PoliButtonSize.Small,
                    variant = PoliButtonVariant.Secondary
                )
            }
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
        SearchLayerKind.CurrentLocation -> MapLibreGeometryOverlayKind.CurrentLocation
    }

private fun SearchLayerKind.areaLabel(): String =
    when (this) {
        SearchLayerKind.Overall -> "전체 수색구역"
        SearchLayerKind.Unit -> "부대 수색구역"
        SearchLayerKind.Team -> "팀 담당구역"
        SearchLayerKind.Path -> "수색 경로"
        SearchLayerKind.Marker -> "마커"
        SearchLayerKind.CurrentLocation -> "현재 위치"
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

private fun String.geometryViewportBounds(): SearchMapViewportBounds? =
    COORDINATE_PAIR.findAll(this)
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
            showMapPreview = true,
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
            onFocusSearchArea = { _, _ -> },
            onToggleHeaderPanel = {},
            onToggleBottomPanel = {},
            onToggleMapOverlays = {}
        )
    }
}
