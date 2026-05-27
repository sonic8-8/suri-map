package com.surimap.feature.search.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import com.surimap.R
import com.surimap.core.map.MapLibreMapViewHandle
import com.surimap.core.map.MapLibreRuntimeMapState
import com.surimap.core.map.MapLibreGeometryOverlay
import com.surimap.core.map.MapLibreGeometryOverlayKind
import com.surimap.core.map.MapLibreGeometryVisualStyle
import com.surimap.core.map.MapLibreViewportBounds
import com.surimap.core.map.SuriMapLibreMap
import com.surimap.core.sync.LocalWarningBanner
import com.surimap.core.sync.LocalWarningCode
import com.surimap.core.sync.LocalWarningUiState
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
import com.surimap.ui.components.PoliToast
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
import com.surimap.ui.theme.PoliOverlayDim
import com.surimap.ui.theme.PoliPrimary
import com.surimap.ui.theme.PoliPrimaryBorder
import com.surimap.ui.theme.PoliPrimaryFillSoft
import com.surimap.ui.theme.PoliPrimaryHi
import com.surimap.ui.theme.PoliSuccess
import com.surimap.ui.theme.PoliWarning
import com.surimap.ui.theme.SuriMapTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ExpandedBottomPanelMapInset = 400.dp
private val MapToastTopPadding = PoliDimens.Space3
private val SearchPanelActionGap = PoliDimens.Space3
private val BottomSheetCollapsedBottomPadding = PoliDimens.Space3
private val BottomSheetExpandedBottomPadding = PoliDimens.Space3
private val BottomSheetCollapsedHeight =
    PoliDimens.Space6 +
        PoliDimens.TouchGlove +
        (PoliDimens.Space2 * 2) +
        BottomSheetCollapsedBottomPadding
private val BottomSheetMaxFallbackHeight = 400.dp
private val BottomSheetExpandedExtraSpace = 0.dp
private const val MapOverlayButtonAlpha = 0.94f
private const val PanelFlingThresholdPx = 650f
private const val PackageWarningToastDurationMs = 4_000L
private const val PackageWarningToastTitle = "오프라인 지도가 준비되지 않았어요"
private const val PackageWarningToastText = "오프라인 사용 전 다운로드가 필요합니다"

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

data class SearchMapLayerVisualStyle(
    val fillColor: String? = null,
    val fillOpacity: Float? = null,
    val lineColor: String? = null,
    val lineWidth: Float? = null,
    val lineOpacity: Float? = null
)

data class SearchMapLayerUiState(
    val label: String,
    val kind: SearchLayerKind,
    val highlighted: Boolean = false,
    val overlayId: String? = null,
    val geoJson: String? = null,
    val assignedToCurrentPhone: Boolean = false,
    val bearingDegrees: Double? = null,
    val markerType: String? = null,
    val supportRequestType: String? = null,
    val visualStyle: SearchMapLayerVisualStyle? = null
)

private enum class SearchMapOverlayTransparencyLevel(
    val label: String,
    val opacityScale: Float
) {
    Full("100%", 1f),
    Medium("70%", 0.7f),
    Low("30%", 0.3f);

    fun next(): SearchMapOverlayTransparencyLevel =
        when (this) {
            Full -> Medium
            Medium -> Low
            Low -> Full
        }
}

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
    val bottomPanelExpanded: Boolean = false,
    val mapOverlaysVisible: Boolean = true,
    val activeSearchPathId: String? = null,
    val activeSearchPathStartedAtEpochMs: Long? = null,
    val localWarnings: LocalWarningUiState = LocalWarningUiState.Empty
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

    val lifecycleStatusLabel: String =
        when (lifecycleStatus) {
            SearchLifecycleStatus.Active -> "수색 진행 중"
            SearchLifecycleStatus.Paused -> "수색 일시정지"
            SearchLifecycleStatus.Stopped -> "수색 대기"
            SearchLifecycleStatus.OpRequired -> "OP 확인 필요"
            SearchLifecycleStatus.OpTransition -> "OP 전환 확인 필요"
        }

    val lifecycleTitle: String =
        when (lifecycleStatus) {
            SearchLifecycleStatus.Active -> ""
            SearchLifecycleStatus.Paused -> "수색 일시정지"
            SearchLifecycleStatus.Stopped -> ""
            SearchLifecycleStatus.OpRequired -> "OP 확인 필요"
            SearchLifecycleStatus.OpTransition -> "OP 전환 확인 필요"
        }

    val lifecycleMessage: String =
        when (lifecycleStatus) {
            SearchLifecycleStatus.Active -> ""
            SearchLifecycleStatus.Paused -> "일시정지 중에는 경로 기록과 마커 생성이 잠시 차단됩니다."
            SearchLifecycleStatus.Stopped -> ""
            SearchLifecycleStatus.OpRequired -> "current OP 누락 또는 조회 실패입니다. 경로·마커 기록 차단 상태입니다."
            SearchLifecycleStatus.OpTransition -> "OP 전환 중입니다. 이전 OP 기록은 readonly로 유지됩니다."
        }

    val primaryActionLabel: String =
        when (lifecycleStatus) {
            SearchLifecycleStatus.Active -> "기록 일시정지"
            SearchLifecycleStatus.Paused -> "수색 재개"
            SearchLifecycleStatus.Stopped -> "수색 시작"
            SearchLifecycleStatus.OpRequired,
            SearchLifecycleStatus.OpTransition -> "수색 차수 확인"
        }

    val secondaryActionLabel: String? =
        if (canStopSearch) {
            "수색 종료"
        } else {
            null
        }

    fun visibleText(): List<String> =
        buildList {
            add(lifecycleStatusLabel)
            if (lifecycleStatus != SearchLifecycleStatus.Paused) {
                lifecycleTitle.takeIf(String::isNotBlank)?.let(::add)
                lifecycleMessage.takeIf(String::isNotBlank)?.let(::add)
            }
            add(if (bottomPanelExpanded) "지도 정보 펼침" else "지도 정보 접힘")
            if (bottomPanelExpanded) {
                add(primaryActionLabel)
                secondaryActionLabel?.let(::add)
                add("사건정보")
                add("근무현황")
                add("투명도")
                add("인수인계")
                add("마커 생성")
            }
            add(if (bottomPanelExpanded) "접기" else "상세")
            add("마커")
            if (showHandoverPrompt) {
                add("이전 근무 기록 있음")
                add("확인")
            }
            markerFocusLabel?.let(::add)
            incidentAlert?.visibleText()?.forEach(::add)
            localWarnings.banners.forEach { banner ->
                add(banner.title)
                add(banner.message)
            }
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
                movementSummary = "도보 1.2km",
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
private fun BottomSheetGrabHandle(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentDescription =
        if (expanded) {
            "수색 정보 접기"
        } else {
            "수색 정보 펼치기"
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PoliDimens.Space6)
                .semantics { this.contentDescription = contentDescription }
                .clickable(
                    role = Role.Button,
                    onClickLabel = contentDescription,
                    onClick = onClick
                ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier =
                Modifier
                    .width(PoliDimens.BottomSheetHandleWidth)
                    .height(PoliDimens.BottomSheetHandleHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = PoliFgMuted.copy(alpha = 0.72f)
        ) {}
    }
}

private fun nearestSheetHeight(value: Float, vararg anchors: Float): Float =
    anchors.minBy { kotlin.math.abs(it - value) }

private fun snapPanelHeight(
    value: Float,
    velocity: Float,
    positiveVelocityExpands: Boolean,
    vararg anchors: Float
): Float {
    val sortedAnchors = anchors.sorted()
    val expandVelocity = if (positiveVelocityExpands) velocity else -velocity
    return when {
        expandVelocity > PanelFlingThresholdPx ->
            sortedAnchors.firstOrNull { it > value + 1f } ?: sortedAnchors.last()
        expandVelocity < -PanelFlingThresholdPx ->
            sortedAnchors.lastOrNull { it < value - 1f } ?: sortedAnchors.first()
        else -> nearestSheetHeight(value, *sortedAnchors.toFloatArray())
    }
}

@Composable
fun SearchMapScreen(
    state: SearchMapUiState,
    mapState: MapLibreRuntimeMapState = MapLibreRuntimeMapState(),
    mapViewHandle: MapLibreMapViewHandle? = null,
    showMapPreview: Boolean = false,
    onPrimaryLifecycleAction: () -> Unit,
    onStopSearch: () -> Unit,
    onCreateMarker: () -> Unit,
    onOpenIncidentInfo: () -> Unit,
    onOpenWorkStatus: () -> Unit,
    onOpenHandover: () -> Unit,
    onOpenBlockedOutbox: () -> Unit,
    onDismissIncidentAlert: () -> Unit,
    onOpenIncidentAlertMarker: (String) -> Unit,
    onOpenFocusedMarkerDetail: (String) -> Unit,
    onCenterCurrentLocation: () -> Unit,
    onViewportBoundsChanged: (SearchMapViewportBounds) -> Unit = {},
    onFocusSearchArea: (SearchLayerKind, String?) -> Unit,
    onToggleBottomPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    MapStatusBarAppearanceEffect()

    val packageWarning =
        state.localWarnings.banners.firstOrNull { warning ->
            warning.code == LocalWarningCode.PACKAGE_MISSING
        }
    val persistentLocalWarnings =
        state.localWarnings.banners.filterNot { warning ->
            warning.code == LocalWarningCode.PACKAGE_MISSING
    }
    var visiblePackageWarning by remember { mutableStateOf<LocalWarningBanner?>(null) }
    var stopConfirmVisible by remember { mutableStateOf(false) }
    var overlayTransparencyLevel by rememberSaveable {
        mutableStateOf(SearchMapOverlayTransparencyLevel.Full)
    }
    LaunchedEffect(packageWarning?.title, packageWarning?.message) {
        if (packageWarning == null) {
            visiblePackageWarning = null
            return@LaunchedEffect
        }
        visiblePackageWarning = packageWarning
        delay(PackageWarningToastDurationMs)
        if (visiblePackageWarning == packageWarning) {
            visiblePackageWarning = null
        }
    }

    val mapBottomInset =
        if (state.bottomPanelExpanded) {
            ExpandedBottomPanelMapInset
        } else {
            BottomSheetCollapsedHeight
        }
    val mapModifier = Modifier.fillMaxSize()

    Box(modifier = modifier.fillMaxSize().background(PoliBgBase)) {
        SearchMapShell(
            state = state,
            mapState = mapState,
            mapViewHandle = mapViewHandle,
            showMapPreview = showMapPreview,
            mapBottomInset = mapBottomInset,
            overlayTransparencyLevel = overlayTransparencyLevel,
            onOpenBlockedOutbox = onOpenBlockedOutbox,
            onCenterCurrentLocation = onCenterCurrentLocation,
            onOpenMarkerDetail = onOpenFocusedMarkerDetail,
            onViewportBoundsChanged = onViewportBoundsChanged,
            modifier = mapModifier
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = PoliDimens.Space3),
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
            persistentLocalWarnings.forEach { warning ->
                LocalWarningBannerView(
                    warning = warning,
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
        }

        SearchBottomPanel(
            state = state,
            onPrimaryLifecycleAction = onPrimaryLifecycleAction,
            onStopSearch = { stopConfirmVisible = true },
            onOpenHandover = onOpenHandover,
            onCreateMarker = onCreateMarker,
            onOpenIncidentInfo = onOpenIncidentInfo,
            onOpenWorkStatus = onOpenWorkStatus,
            overlayTransparencyLevel = overlayTransparencyLevel,
            onCycleOverlayTransparencyLevel = {
                overlayTransparencyLevel = overlayTransparencyLevel.next()
            },
            onToggleBottomPanel = onToggleBottomPanel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        visiblePackageWarning?.let {
            PoliToast(
                title = PackageWarningToastTitle,
                text = PackageWarningToastText,
                modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = PoliDimens.SectionPadding)
                    .padding(top = MapToastTopPadding),
                variant = PoliBannerVariant.Warn
            )
        }
        if (stopConfirmVisible) {
            StopSearchConfirmDialog(
                onDismiss = { stopConfirmVisible = false },
                onConfirmStopSearch = {
                    stopConfirmVisible = false
                    onStopSearch()
                }
            )
        }
    }
}

@Composable
private fun MapStatusBarAppearanceEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowInsetsControllerCompat(window, view)
            val previousLightStatusBars = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = true
            onDispose {
                controller.isAppearanceLightStatusBars = previousLightStatusBars
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun LocalWarningBannerView(
    warning: LocalWarningBanner,
    modifier: Modifier = Modifier
) {
    PoliBanner(
        text = "${warning.title}\n${warning.message}",
        variant =
        when (warning.code) {
            LocalWarningCode.BATTERY_LOW,
            LocalWarningCode.OFFLINE_RECORDING,
            LocalWarningCode.PACKAGE_MISSING,
            LocalWarningCode.OUTBOX_BACKLOG -> PoliBannerVariant.Warn
        },
        modifier = modifier
    )
}

@Composable
private fun StopSearchConfirmDialog(
    onDismiss: () -> Unit,
    onConfirmStopSearch: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(PoliOverlayDim).padding(PoliDimens.SectionPadding)) {
        PoliCard(modifier = Modifier.align(Alignment.Center), strong = true) {
            Text(text = "수색 종료 확인", style = MaterialTheme.typography.titleMedium, color = PoliEmphasis)
            Text(
                text = "현재 수색 경로 기록을 종료합니다. 종료 후에는 새 수색을 시작해야 다시 기록할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = PoliFgSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
                PoliButton(
                    text = "취소",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = PoliButtonVariant.Secondary
                )
                PoliButton(
                    text = "수색 종료",
                    onClick = onConfirmStopSearch,
                    modifier = Modifier.weight(1f),
                    variant = PoliButtonVariant.Danger
                )
            }
        }
    }
}

@Composable
private fun SearchMapShell(
    state: SearchMapUiState,
    mapState: MapLibreRuntimeMapState,
    mapViewHandle: MapLibreMapViewHandle?,
    showMapPreview: Boolean,
    mapBottomInset: Dp,
    overlayTransparencyLevel: SearchMapOverlayTransparencyLevel,
    onOpenBlockedOutbox: () -> Unit,
    onCenterCurrentLocation: () -> Unit,
    onOpenMarkerDetail: (String) -> Unit,
    onViewportBoundsChanged: (SearchMapViewportBounds) -> Unit,
    modifier: Modifier = Modifier
) {
    val runtimeMapState = state.toRuntimeMapState(mapState, overlayTransparencyLevel.opacityScale)
    val currentLocationBottomInset = mapBottomInset + PoliDimens.TouchGlove + PoliDimens.Space6

    Box(modifier = modifier.fillMaxSize().background(PoliBgInput)) {
        if (showMapPreview) {
            SearchMapPreviewScene(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SuriMapLibreMap(
                state = runtimeMapState,
                mapViewHandle = mapViewHandle,
                modifier = Modifier.fillMaxSize(),
                onLoadFailed = {},
                onMarkerClick = onOpenMarkerDetail,
                onViewportBoundsChanged = { bounds ->
                    onViewportBoundsChanged(bounds.toSearchMapViewportBounds())
                }
            )
        }

        if (state.shouldOpenBlockedOutbox) {
            PoliChip(
                text = "처리불가 ${state.blockedOutboxCount}건",
                variant = PoliChipVariant.Bad,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = PoliDimens.Space3, end = PoliDimens.Space3)
                        .clickable(onClick = onOpenBlockedOutbox)
            )
        }

        CurrentLocationButton(
            onClick = onCenterCurrentLocation,
            modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = PoliDimens.Space4, bottom = currentLocationBottomInset)
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
private fun CurrentLocationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier =
            modifier
                .size(PoliDimens.TouchGlove)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "현재 위치로 이동",
                    onClick = onClick
                ),
        shape = MaterialTheme.shapes.medium,
        color = PoliBgSurface.copy(alpha = MapOverlayButtonAlpha),
        contentColor = PoliCurrent,
        border = BorderStroke(1.dp, PoliBorderStrong)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = R.drawable.ic_my_location),
                contentDescription = "현재 위치로 이동",
                tint = PoliCurrent,
                modifier = Modifier.size(PoliDimens.TouchMin / 2)
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
    onOpenIncidentInfo: () -> Unit,
    onOpenWorkStatus: () -> Unit,
    overlayTransparencyLevel: SearchMapOverlayTransparencyLevel,
    onCycleOverlayTransparencyLevel: () -> Unit,
    onToggleBottomPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val collapsedHeightPx = with(density) { BottomSheetCollapsedHeight.toPx() }
    val fallbackExpandedHeightPx = with(density) { BottomSheetMaxFallbackHeight.toPx() }
    var measuredExpandedHeightPx by remember { mutableStateOf(fallbackExpandedHeightPx) }
    val expandedExtraSpacePx = with(density) { BottomSheetExpandedExtraSpace.toPx() }
    val expandedHeightPx =
        (measuredExpandedHeightPx + expandedExtraSpacePx)
            .coerceAtLeast(fallbackExpandedHeightPx)
    val panelHeight = remember {
        Animatable(if (state.bottomPanelExpanded) fallbackExpandedHeightPx else collapsedHeightPx)
    }
    val panelHeightPx = panelHeight.value.coerceIn(collapsedHeightPx, expandedHeightPx)
    val expansionThresholdPx = (collapsedHeightPx + expandedHeightPx) / 2f
    val sheetExpanded = panelHeightPx > expansionThresholdPx
    val expandedContentVisible = sheetExpanded
    val contentTopPadding = PoliDimens.Space2
    val contentBottomPadding =
        if (expandedContentVisible) {
            BottomSheetExpandedBottomPadding
        } else {
            BottomSheetCollapsedBottomPadding
        }
    val contentSpacing =
        if (expandedContentVisible) {
            SearchPanelActionGap
        } else {
            PoliDimens.Space2
        }
    val dragState =
        rememberDraggableState { delta ->
            val nextHeight = (panelHeight.value - delta).coerceIn(collapsedHeightPx, expandedHeightPx)
            coroutineScope.launch {
                panelHeight.snapTo(nextHeight)
            }
        }

    fun toggleBottomPanelFromHandle() {
        val shouldExpand = !sheetExpanded
        val snappedHeight = if (shouldExpand) expandedHeightPx else collapsedHeightPx
        coroutineScope.launch {
            panelHeight.animateTo(
                targetValue = snappedHeight,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
            )
        }
        if (state.bottomPanelExpanded != shouldExpand) {
            onToggleBottomPanel()
        }
    }

    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(with(density) { panelHeightPx.toDp() })
            .clipToBounds()
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    coroutineScope.launch {
                        panelHeight.stop()
                    }
                },
                onDragStopped = { velocity ->
                    val snappedHeight =
                        snapPanelHeight(
                            value = panelHeight.value,
                            velocity = velocity,
                            positiveVelocityExpands = false,
                            collapsedHeightPx,
                            expandedHeightPx
                        )
                    coroutineScope.launch {
                        panelHeight.animateTo(
                            targetValue = snappedHeight,
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                        )
                    }
                    val expanded = snappedHeight == expandedHeightPx
                    if (state.bottomPanelExpanded != expanded) {
                        onToggleBottomPanel()
                    }
                }
            )
    ) {
        Surface(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(with(density) { panelHeightPx.toDp() })
                .align(Alignment.TopCenter),
            shape =
                RoundedCornerShape(
                    topStart = MaterialTheme.shapes.extraLarge.topStart,
                    topEnd = MaterialTheme.shapes.extraLarge.topEnd,
                    bottomEnd = CornerSize(0.dp),
                    bottomStart = CornerSize(0.dp)
                ),
            color = PoliBgSurface,
            contentColor = PoliFgPrimary,
            border = null
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged {
                        measuredExpandedHeightPx = it.height.toFloat().coerceAtLeast(fallbackExpandedHeightPx)
                    }
                    .padding(horizontal = PoliDimens.Space5)
                    .padding(top = contentTopPadding, bottom = contentBottomPadding),
                verticalArrangement = Arrangement.spacedBy(contentSpacing)
            ) {
                BottomSheetGrabHandle(
                    expanded = sheetExpanded,
                    onClick = { toggleBottomPanelFromHandle() }
                )
                SearchCollapsedPanelContent(state = state)
                if (expandedContentVisible) {
                    SearchCollapsedPrimaryActionButton(
                        text = state.primaryActionLabel,
                        onClick = onPrimaryLifecycleAction,
                        lifecycleStatus = state.lifecycleStatus,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (
                        state.lifecycleStatus != SearchLifecycleStatus.Paused &&
                        (state.lifecycleTitle.isNotBlank() || state.lifecycleMessage.isNotBlank())
                    ) {
                        SearchLifecycleMessage(state = state)
                    }
                    SearchPanelActionGrid(
                        canCreateMarker = state.canCreateMarker,
                        canStopSearch = state.canStopSearch,
                        onOpenIncidentInfo = onOpenIncidentInfo,
                        onOpenWorkStatus = onOpenWorkStatus,
                        onCreateMarker = onCreateMarker,
                        overlayTransparencyLevel = overlayTransparencyLevel,
                        onCycleOverlayTransparencyLevel = onCycleOverlayTransparencyLevel,
                        onOpenHandover = onOpenHandover,
                        onStopSearch = onStopSearch
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPanelActionGrid(
    canCreateMarker: Boolean,
    canStopSearch: Boolean,
    onOpenIncidentInfo: () -> Unit,
    onOpenWorkStatus: () -> Unit,
    onCreateMarker: () -> Unit,
    overlayTransparencyLevel: SearchMapOverlayTransparencyLevel,
    onCycleOverlayTransparencyLevel: () -> Unit,
    onOpenHandover: () -> Unit,
    onStopSearch: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SearchPanelActionGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SearchPanelActionGap)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SearchPanelActionGap)
            ) {
                PoliButton(
                    text = "사건정보",
                    onClick = onOpenIncidentInfo,
                    modifier = Modifier.fillMaxWidth(),
                    variant = PoliButtonVariant.Secondary,
                    size = PoliButtonSize.Large
                )
                PoliButton(
                    text = "근무현황",
                    onClick = onOpenWorkStatus,
                    modifier = Modifier.fillMaxWidth(),
                    variant = PoliButtonVariant.Secondary,
                    size = PoliButtonSize.Large
                )
            }
            SearchMarkerCreateSquareButton(
                onClick = onCreateMarker,
                enabled = canCreateMarker
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SearchPanelActionGap)) {
            SearchOverlayTransparencyButton(
                level = overlayTransparencyLevel,
                onClick = onCycleOverlayTransparencyLevel,
                modifier = Modifier.size(PoliDimens.CtaHeightLarge)
            )
            PoliButton(
                text = "인수인계",
                onClick = onOpenHandover,
                modifier = Modifier.weight(1f),
                variant = PoliButtonVariant.Secondary,
                size = PoliButtonSize.Large
            )
            if (canStopSearch) {
                PoliButton(
                    text = "수색 종료",
                    onClick = onStopSearch,
                    modifier = Modifier.weight(1f),
                    variant = PoliButtonVariant.SubtleDanger,
                    size = PoliButtonSize.Large
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchMarkerCreateSquareButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val size = (PoliDimens.CtaHeightLarge * 2) + SearchPanelActionGap
    Surface(
        modifier =
        modifier
            .size(size)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "마커 생성",
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        contentColor = if (enabled) Color.White else PoliFgMuted,
        border = BorderStroke(1.dp, if (enabled) PoliPrimaryBorder else PoliBorder),
        shadowElevation = if (enabled) 3.dp else 0.dp
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        if (enabled) {
                            Brush.verticalGradient(colors = listOf(PoliPrimaryHi, PoliPrimary))
                        } else {
                            Brush.verticalGradient(colors = listOf(PoliBgInput, PoliBgInput))
                        }
                    ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "마커 생성",
                style =
                MaterialTheme.typography.labelLarge.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchOverlayTransparencyButton(
    level: SearchMapOverlayTransparencyLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier =
        modifier
            .clickable(role = Role.Button, onClickLabel = "투명도 ${level.label}", onClick = onClick)
            .semantics { contentDescription = "투명도 ${level.label}" },
        shape = MaterialTheme.shapes.medium,
        color = PoliPrimaryFillSoft,
        contentColor = Color(0xFFEAF4FF),
        border = BorderStroke(1.dp, PoliPrimaryBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(28.dp)) {
                val strokeWidth = size.minDimension * 0.065f
                val layerColor = Color(0xFFEAF4FF)
                val layerAlpha = listOf(1.0f, 0.86f, 0.72f)
                val layers =
                    listOf(
                        listOf(
                            Offset(size.width * 0.18f, size.height * 0.28f),
                            Offset(size.width * 0.72f, size.height * 0.28f),
                            Offset(size.width * 0.60f, size.height * 0.40f),
                            Offset(size.width * 0.06f, size.height * 0.40f)
                        ),
                        listOf(
                            Offset(size.width * 0.24f, size.height * 0.45f),
                            Offset(size.width * 0.78f, size.height * 0.45f),
                            Offset(size.width * 0.66f, size.height * 0.57f),
                            Offset(size.width * 0.12f, size.height * 0.57f)
                        ),
                        listOf(
                            Offset(size.width * 0.30f, size.height * 0.62f),
                            Offset(size.width * 0.84f, size.height * 0.62f),
                            Offset(size.width * 0.72f, size.height * 0.74f),
                            Offset(size.width * 0.18f, size.height * 0.74f)
                        )
                    )
                layers.forEachIndexed { index, points ->
                    val path =
                        Path().apply {
                            moveTo(points[0].x, points[0].y)
                            lineTo(points[1].x, points[1].y)
                            lineTo(points[2].x, points[2].y)
                            lineTo(points[3].x, points[3].y)
                            close()
                        }
                    drawPath(
                        path = path,
                        color = layerColor.copy(alpha = layerAlpha[index]),
                        style = Stroke(width = strokeWidth, join = StrokeJoin.Round)
                    )
                }
            }
            Text(
                text = level.label,
                style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFFEAF4FF),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SearchCollapsedPanelContent(state: SearchMapUiState) {
    SearchCollapsedStatusCard(state = state)
}

@Composable
private fun SearchCollapsedLifecycleActions(
    state: SearchMapUiState,
    onPrimaryLifecycleAction: () -> Unit,
    onStopSearch: () -> Unit
) {
    val secondaryActionLabel = state.secondaryActionLabel
    if (secondaryActionLabel == null) {
        SearchCollapsedPrimaryActionButton(
            text = state.primaryActionLabel,
            onClick = onPrimaryLifecycleAction,
            lifecycleStatus = state.lifecycleStatus,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
        ) {
            SearchCollapsedPrimaryActionButton(
                text = state.primaryActionLabel,
                onClick = onPrimaryLifecycleAction,
                lifecycleStatus = state.lifecycleStatus,
                modifier = Modifier.weight(1f)
            )
            PoliButton(
                text = secondaryActionLabel,
                onClick = onStopSearch,
                modifier = Modifier.weight(0.86f),
                variant = PoliButtonVariant.Danger
            )
        }
    }
}

@Composable
private fun SearchCollapsedPrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    lifecycleStatus: SearchLifecycleStatus,
    modifier: Modifier = Modifier
) {
    val buttonStyle =
        when (lifecycleStatus) {
            SearchLifecycleStatus.Active ->
                SearchCollapsedActionButtonStyle(
                    topColor = PoliPrimaryHi,
                    bottomColor = PoliPrimary,
                    borderColor = PoliPrimaryBorder,
                    contentColor = Color.White
                )
            SearchLifecycleStatus.OpRequired,
            SearchLifecycleStatus.OpTransition ->
                SearchCollapsedActionButtonStyle(
                    topColor = Color(0xFFD97706),
                    bottomColor = Color(0xFFB45309),
                    borderColor = Color(0xFF92400E),
                    contentColor = Color.White
                )
            SearchLifecycleStatus.Paused,
            SearchLifecycleStatus.Stopped ->
                SearchCollapsedActionButtonStyle(
                    topColor = PoliPrimaryHi,
                    bottomColor = PoliPrimary,
                    borderColor = PoliPrimaryBorder,
                    contentColor = Color.White
                )
        }
    val textStyle =
        when (lifecycleStatus) {
            SearchLifecycleStatus.Active,
            SearchLifecycleStatus.Paused -> MaterialTheme.typography.titleMedium
            SearchLifecycleStatus.Stopped,
            SearchLifecycleStatus.OpRequired,
            SearchLifecycleStatus.OpTransition -> MaterialTheme.typography.labelLarge
        }

    Surface(
        modifier =
            modifier
                .height(PoliDimens.CtaHeight)
                .clickable(
                    role = Role.Button,
                    onClickLabel = text,
                    onClick = onClick
                ),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        contentColor = buttonStyle.contentColor,
        border = BorderStroke(1.dp, buttonStyle.borderColor),
        shadowElevation = 3.dp
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(buttonStyle.topColor, buttonStyle.bottomColor)
                        )
                    ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = textStyle,
                color = buttonStyle.contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class SearchCollapsedActionButtonStyle(
    val topColor: Color,
    val bottomColor: Color,
    val borderColor: Color,
    val contentColor: Color
)

@Composable
private fun SearchCollapsedStatusCard(state: SearchMapUiState) {
    val cardStyle =
        when (state.lifecycleStatus) {
            SearchLifecycleStatus.Active ->
                SearchCollapsedStatusCardStyle(
                    containerColor = Color(0xFF0F2A1A),
                    borderColor = PoliSuccess,
                    titleColor = Color(0xFFF0FDF4),
                    subtitleColor = Color(0xFFA7F3D0)
                )
            SearchLifecycleStatus.Paused ->
                SearchCollapsedStatusCardStyle(
                    containerColor = Color(0xFF2B2114),
                    borderColor = Color(0xFFD97706),
                    titleColor = Color(0xFFFFF7ED),
                    subtitleColor = Color(0xFFFCD9A6)
                )
            SearchLifecycleStatus.Stopped ->
                SearchCollapsedStatusCardStyle(
                    containerColor = PoliBgInput,
                    borderColor = PoliPrimaryBorder,
                    titleColor = PoliFgPrimary,
                    subtitleColor = PoliFgMuted
                )
            SearchLifecycleStatus.OpRequired,
            SearchLifecycleStatus.OpTransition ->
                SearchCollapsedStatusCardStyle(
                    containerColor = Color(0xFF2B2114),
                    borderColor = Color(0xFFD97706),
                    titleColor = Color(0xFFFFF7ED),
                    subtitleColor = Color(0xFFFCD9A6)
                )
        }

    Surface(
        modifier = Modifier.fillMaxWidth().height(PoliDimens.TouchGlove),
        shape = MaterialTheme.shapes.medium,
        color = cardStyle.containerColor,
        contentColor = cardStyle.titleColor,
        border = BorderStroke(1.dp, cardStyle.borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PoliDimens.Space4),
            horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchStatusDot(state.lifecycleStatus)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)
            ) {
                Text(
                    text = state.lifecycleStatusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = cardStyle.titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.collapsedRecordLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = cardStyle.subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = state.elapsedLabel,
                style = MaterialTheme.typography.titleMedium,
                color = cardStyle.titleColor,
                maxLines = 1
            )
        }
    }
}

private data class SearchCollapsedStatusCardStyle(
    val containerColor: Color,
    val borderColor: Color,
    val titleColor: Color,
    val subtitleColor: Color
)

private val SearchMapUiState.collapsedRecordLabel: String
    get() =
        when (lifecycleStatus) {
            SearchLifecycleStatus.Active ->
                if (syncStatus == SearchMapSyncStatus.Offline) {
                    "기록 중 · 통신 복구 시 자동 전송"
                } else {
                    "경로 기록 중"
                }
            SearchLifecycleStatus.Paused -> "경로·마커 기록 중단"
            SearchLifecycleStatus.Stopped -> "기록 대기"
            SearchLifecycleStatus.OpRequired,
            SearchLifecycleStatus.OpTransition -> "기록 차단"
        }

@Composable
private fun SearchLifecyclePeekRow(
    state: SearchMapUiState,
    onPrimaryLifecycleAction: () -> Unit,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchStatusDot(state.lifecycleStatus)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)
        ) {
            Text(
                text = state.lifecycleStatusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = PoliFgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${state.elapsedLabel} · ${state.movementSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = PoliFgMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        PoliButton(
            text = state.primaryActionLabel,
            onClick = onPrimaryLifecycleAction,
            modifier = Modifier.weight(0.95f),
            variant = if (state.lifecycleStatus == SearchLifecycleStatus.OpRequired) {
                PoliButtonVariant.Secondary
            } else {
                PoliButtonVariant.Primary
            }
        )
        PoliButton(
            text = if (detailsExpanded) "접기" else "상세",
            onClick = onToggleDetails,
            modifier = Modifier.weight(0.72f),
            variant = PoliButtonVariant.Secondary
        )
    }
}

@Composable
private fun SearchLifecycleMessage(state: SearchMapUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)) {
        if (state.lifecycleTitle.isNotBlank()) {
            Text(
                text = state.lifecycleTitle,
                style = MaterialTheme.typography.labelMedium,
                color = PoliWarning
            )
        }
        if (state.lifecycleMessage.isNotBlank()) {
            Text(
                text = state.lifecycleMessage,
                style = MaterialTheme.typography.bodySmall,
                color = PoliFgMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchStatusDot(status: SearchLifecycleStatus) {
    val color =
        when (status) {
            SearchLifecycleStatus.Active -> PoliSuccess
            SearchLifecycleStatus.Paused -> PoliWarning
            SearchLifecycleStatus.Stopped -> PoliPrimaryBorder
            SearchLifecycleStatus.OpRequired,
            SearchLifecycleStatus.OpTransition -> PoliWarning
        }
    Surface(modifier = Modifier.size(12.dp), shape = MaterialTheme.shapes.extraLarge, color = color) {}
}

private val SearchMapUiState.syncVariant: PoliChipVariant
    get() =
        when (syncStatus) {
            SearchMapSyncStatus.Idle -> PoliChipVariant.Neutral
            SearchMapSyncStatus.Synced -> PoliChipVariant.Good
            SearchMapSyncStatus.Offline -> PoliChipVariant.Warn
            SearchMapSyncStatus.Sending -> PoliChipVariant.Outbox
        }

private fun SearchMapUiState.toRuntimeMapState(
    base: MapLibreRuntimeMapState,
    overlayOpacityScale: Float = 1f
): MapLibreRuntimeMapState {
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
            val layerOpacityScale =
                if (layer.kind == SearchLayerKind.CurrentLocation) {
                    1f
                } else {
                    overlayOpacityScale
                }
            MapLibreGeometryOverlay(
                id = layer.overlayId ?: layer.label,
                kind = layer.kind.toMapLibreGeometryOverlayKind(),
                geoJson = geoJson,
                highlighted = layer.highlighted || focused,
                label = layer.label,
                bearingDegrees = layer.bearingDegrees,
                markerType = layer.markerType,
                supportRequestType = layer.supportRequestType,
                visualStyle =
                    layer.visualStyle?.let { style ->
                        MapLibreGeometryVisualStyle(
                            fillColor = style.fillColor,
                            fillOpacity = style.fillOpacity,
                            lineColor = style.lineColor,
                            lineWidth = style.lineWidth,
                            lineOpacity = style.lineOpacity,
                            opacityScale = layerOpacityScale
                        )
                    } ?: MapLibreGeometryVisualStyle(opacityScale = layerOpacityScale)
            )
        }
    )
}

private fun MapLibreViewportBounds.toSearchMapViewportBounds(): SearchMapViewportBounds =
    SearchMapViewportBounds(
        south = south,
        west = west,
        north = north,
        east = east
    )

private fun SearchLayerKind.toMapLibreGeometryOverlayKind(): MapLibreGeometryOverlayKind =
    when (this) {
        SearchLayerKind.Overall -> MapLibreGeometryOverlayKind.Overall
        SearchLayerKind.Unit -> MapLibreGeometryOverlayKind.Unit
        SearchLayerKind.Team -> MapLibreGeometryOverlayKind.Team
        SearchLayerKind.Path -> MapLibreGeometryOverlayKind.Path
        SearchLayerKind.Marker -> MapLibreGeometryOverlayKind.Marker
        SearchLayerKind.CurrentLocation -> MapLibreGeometryOverlayKind.CurrentLocation
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
            onPrimaryLifecycleAction = {},
            onStopSearch = {},
            onCreateMarker = {},
            onOpenIncidentInfo = {},
            onOpenWorkStatus = {},
            onOpenHandover = {},
            onOpenBlockedOutbox = {},
            onDismissIncidentAlert = {},
            onOpenIncidentAlertMarker = {},
            onOpenFocusedMarkerDetail = {},
            onCenterCurrentLocation = {},
            onFocusSearchArea = { _, _ -> },
            onToggleBottomPanel = {}
        )
    }
}
