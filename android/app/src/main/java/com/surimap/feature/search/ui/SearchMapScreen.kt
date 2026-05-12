package com.surimap.feature.search.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    Team
}

data class SearchMapLayerUiState(
    val label: String,
    val kind: SearchLayerKind,
    val highlighted: Boolean = false
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
    val handoverPrompt: HandoverPromptUiState?,
    val incidentAlert: IncidentAlertUiState? = null
) {
    val canWritePath: Boolean = lifecycleStatus == SearchLifecycleStatus.Active
    val canCreateMarker: Boolean = lifecycleStatus == SearchLifecycleStatus.Active
    val canStopSearch: Boolean =
        lifecycleStatus == SearchLifecycleStatus.Active || lifecycleStatus == SearchLifecycleStatus.Paused
    val shouldOpenBlockedOutbox: Boolean = blockedOutboxCount > 0
    val showHandoverPrompt: Boolean = handoverPrompt?.shouldShow == true

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
            add(dutyShiftLabel)
            add(assignmentLabel)
            add(syncLabel)
            add(lifecycleTitle)
            add(lifecycleMessage)
            add(primaryActionLabel)
            add(if (canWritePath) "경로 기록 가능" else "경로 기록 차단")
            add(if (canCreateMarker) "마커 생성 가능" else "마커 생성 차단")
            add("마커 생성")
            if (showHandoverPrompt) {
                add("이전 근무 기록 있음")
            }
            incidentAlert?.visibleText()?.forEach(::add)
            if (blockedOutboxCount > 0) {
                add("미전송 ${blockedOutboxCount}건 처리 불가")
            }
            layers.forEach { add(it.label) }
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
                incidentTitle = "광주 북구 산악 실종",
                missingPersonSummary = "60대 여 · 회색 점퍼",
                opLabel = "OP 3차 · 재수색",
                dutyShiftLabel = "DutyShift · 14:00 인계",
                assignmentLabel = "담당: 기동대 1부대 A팀",
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
                    SearchMapLayerUiState("A팀 담당 구역", SearchLayerKind.Team, highlighted = true)
                ),
                handoverPrompt = handoverPrompt,
                incidentAlert = incidentAlert
            )
    }
}

@Composable
fun SearchMapScreen(
    state: SearchMapUiState,
    onBack: () -> Unit,
    onPrimaryLifecycleAction: () -> Unit,
    onStopSearch: () -> Unit,
    onCreateMarker: () -> Unit,
    onOpenHandover: () -> Unit,
    onOpenBlockedOutbox: () -> Unit,
    onDismissIncidentAlert: () -> Unit,
    onOpenIncidentAlertMarker: (String) -> Unit,
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
                modifier = Modifier.weight(1f)
            )
        }

        SearchBottomPanel(
            state = state,
            onPrimaryLifecycleAction = onPrimaryLifecycleAction,
            onStopSearch = onStopSearch,
            onCreateMarker = onCreateMarker
        )
    }
}

@Composable
private fun SearchMapHeader(state: SearchMapUiState, onBack: () -> Unit) {
    PoliAppBar(
        title = state.missingPersonSummary,
        subtitle = "${state.incidentTitle} · ${state.opLabel}",
        showBack = true,
        onBack = onBack,
        trailing = {
            PoliChip(text = state.syncLabel, variant = state.syncVariant)
        }
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PoliDimens.SectionPadding),
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)
    ) {
        PoliChip(text = state.dutyShiftLabel, variant = PoliChipVariant.Neutral)
        PoliChip(text = state.assignmentLabel, variant = PoliChipVariant.Outbox)
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
private fun SearchMapShell(state: SearchMapUiState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().background(PoliBgInput)) {
        MockMapCanvas(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(PoliDimens.Space3),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)
        ) {
            state.layers.forEach { layer ->
                PoliChip(
                    text = layer.label,
                    variant = if (layer.highlighted) PoliChipVariant.Outbox else PoliChipVariant.Neutral
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(PoliDimens.Space3),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)
        ) {
            MapControlButton(text = "+")
            MapControlButton(text = "-")
            MapControlButton(text = "층")
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(PoliDimens.Space4).size(56.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = PoliBgSurface,
            contentColor = PoliCurrent,
            border = androidx.compose.foundation.BorderStroke(1.dp, PoliCurrent)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "내\n위치", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MockMapCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val grid = Color.White.copy(alpha = 0.06f)
        val gridStep = 56.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            x += gridStep
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            y += gridStep
        }

        drawRect(
            color = Color(0xFF6B7280),
            topLeft = Offset(size.width * 0.05f, size.height * 0.08f),
            size = Size(size.width * 0.9f, size.height * 0.62f),
            style = Stroke(width = 2.dp.toPx())
        )
        drawRoundRect(
            color = PoliCurrent.copy(alpha = 0.16f),
            topLeft = Offset(size.width * 0.15f, size.height * 0.16f),
            size = Size(size.width * 0.7f, size.height * 0.34f),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
        drawRoundRect(
            color = PoliCurrent,
            topLeft = Offset(size.width * 0.15f, size.height * 0.16f),
            size = Size(size.width * 0.7f, size.height * 0.34f),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )

        drawLine(
            color = PoliCurrent,
            start = Offset(size.width * 0.28f, size.height * 0.39f),
            end = Offset(size.width * 0.62f, size.height * 0.46f),
            strokeWidth = 4.dp.toPx()
        )
        drawLine(
            color = PoliFgSecondary,
            start = Offset(size.width * 0.2f, size.height * 0.63f),
            end = Offset(size.width * 0.43f, size.height * 0.67f),
            strokeWidth = 3.dp.toPx()
        )
        drawCircle(
            color = PoliCurrent,
            radius = 9.dp.toPx(),
            center = Offset(size.width * 0.62f, size.height * 0.46f)
        )
        drawCircle(
            color = PoliFgSecondary,
            radius = 7.dp.toPx(),
            center = Offset(size.width * 0.34f, size.height * 0.35f),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = PoliEmphasis,
            radius = 10.dp.toPx(),
            center = Offset(size.width * 0.66f, size.height * 0.34f),
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            color = PoliFgPrimary,
            radius = 4.dp.toPx(),
            center = Offset(size.width * 0.66f, size.height * 0.34f)
        )
    }
}

@Composable
private fun MapControlButton(text: String) {
    Surface(
        modifier = Modifier.size(PoliDimens.TouchMin),
        shape = MaterialTheme.shapes.medium,
        color = PoliBgSurface,
        contentColor = PoliFgSecondary,
        border = androidx.compose.foundation.BorderStroke(1.dp, PoliBorderStrong)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SearchBottomPanel(
    state: SearchMapUiState,
    onPrimaryLifecycleAction: () -> Unit,
    onStopSearch: () -> Unit,
    onCreateMarker: () -> Unit
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .background(PoliBgSurface)
            .padding(PoliDimens.SectionPadding),
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
    ) {
        SearchStatusCard(state = state)
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
        PoliButton(
            text = "마커 생성",
            onClick = onCreateMarker,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canCreateMarker,
            size = PoliButtonSize.Large
        )
    }
}

@Composable
private fun SearchStatusCard(state: SearchMapUiState) {
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
            onOpenIncidentAlertMarker = {}
        )
    }
}
