package com.surimap.feature.handover.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlin.math.roundToLong
import com.surimap.feature.handover.domain.HandoverReplayCameraMode
import com.surimap.feature.handover.domain.HandoverReplaySpeed
import com.surimap.ui.components.PoliAppBar
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonSize
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliField
import com.surimap.ui.components.PoliRow
import com.surimap.ui.theme.PoliBgInput
import com.surimap.ui.theme.PoliCurrent
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgSecondary
import com.surimap.ui.theme.PoliPrimaryMid
import com.surimap.ui.theme.PoliWarning
import com.surimap.ui.theme.SuriMapTheme

data class DutyHandoverUiState(
    val title: String,
    val subtitle: String,
    val summaryStatus: SearchHistorySummaryStatus,
    val generatedAtLabel: String,
    val summary: String?,
    val sourceReadiness: SummarySourceReadiness,
    val metrics: List<HandoverMetric>,
    val records: List<HandoverRecord>,
    val replayPathSegments: List<HandoverReplayPathSegment> = emptyList(),
    val replayMarkers: List<HandoverReplayMarker> = emptyList(),
    val replayControl: HandoverReplayControlUiState = HandoverReplayControlUiState(),
    val selectedTab: DutyHandoverTab = DutyHandoverTab.Replay,
    val canEndDutyShift: Boolean = false,
    val endingDutyShift: Boolean = false,
    val canRequestSummaryGeneration: Boolean = false
) {
    val replaySectionTitles: List<String> = HandoverReplaySections
    val replayBadges: List<String> = listOf("근무 기준", "단일 근무자", "정적 보기")

    val reportSectionTitles: List<String> = HandoverReportSections

    val handoverMemoRecords: List<HandoverRecord> =
        records.filter { record ->
            record.title.contains("메모") || record.subtitle.contains("메모")
        }

    val markerPhotoRecords: List<HandoverRecord> =
        records.filter { record ->
            record.title.contains("마커") && record.subtitle.contains("사진")
        }

    val summaryText: String =
        summary ?: summaryStatus.emptyCopy

    val summaryActionLabel: String? =
        when (summaryStatus) {
            SearchHistorySummaryStatus.NeedsSummary,
            SearchHistorySummaryStatus.Unavailable -> "원본 확인"
            SearchHistorySummaryStatus.Ready,
            SearchHistorySummaryStatus.Generating,
            SearchHistorySummaryStatus.Empty -> null
        }

    fun visibleText(): List<String> =
        buildList {
            add(title)
            add(subtitle)
            DutyHandoverTab.entries.forEach { add(it.label) }
            add(selectedTab.label)
            add("서버 인수인계 요약")
            add(summaryStatus.label)
            add(generatedAtLabel)
            add(summaryText)
            summaryActionLabel?.let(::add)
            if (selectedTab == DutyHandoverTab.Replay) {
                addAll(replaySectionTitles)
                addAll(replayBadges)
                add("리플레이 컨트롤")
                add(replayControl.playPauseLabel)
                add(replayControl.timeRangeLabel)
                add("속도")
                HandoverReplaySpeed.entries.forEach { add(it.label) }
                add("카메라")
                HandoverReplayCameraMode.entries.forEach { add(it.label) }
                replayPathSegments.forEach {
                    add(it.label)
                    add(it.timeRangeLabel)
                    add(it.distanceLabel)
                    add(it.modeLabel)
                }
                replayMarkers.forEach {
                    add(it.title)
                    add(it.timeLabel)
                    add(it.typeLabel)
                    add(it.photoCountLabel)
                }
            }
            if (selectedTab == DutyHandoverTab.Report) {
                addAll(reportSectionTitles)
                add(sourceReadiness.reportLabel)
                handoverMemoRecords.forEach {
                    add(it.title)
                    add(it.subtitle)
                    add(it.actionLabel)
                }
                markerPhotoRecords.forEach {
                    add(it.title)
                    add(it.subtitle)
                    add(it.actionLabel)
                }
            }
            add("타임라인")
            add("원본 기록")
            metrics.forEach {
                add(it.value)
                add(it.label)
            }
            records.forEach {
                add(it.title)
                add(it.subtitle)
                add(it.actionLabel)
            }
            if (canEndDutyShift) {
                add(dutyShiftActionLabel)
            }
        }

    fun selectTab(tab: DutyHandoverTab): DutyHandoverUiState =
        copy(selectedTab = tab)

    val dutyShiftActionLabel: String =
        if (endingDutyShift) "종료 등록 중" else "근무 종료"

    companion object {
        fun ready(): DutyHandoverUiState =
            base(
                summaryStatus = SearchHistorySummaryStatus.Ready,
                generatedAtLabel = "박 순경 근무 기록 기준 · 12:00-14:00 · 서버 처리 완료",
                summary = "동쪽 능선과 북측 진입로 주변을 도보로 수색했습니다. 배수로 인근 단서 마커 1건과 운영 메모 2건이 남아 있습니다.",
                sourceReadiness = SummarySourceReadiness.Ready
            )

        fun generating(): DutyHandoverUiState =
            base(
                summaryStatus = SearchHistorySummaryStatus.Generating,
                generatedAtLabel = "서버가 이전 근무 기록을 정리하는 중",
                summary = null,
                sourceReadiness = SummarySourceReadiness.PendingSync
            )

        fun needsSummary(): DutyHandoverUiState =
            base(
                summaryStatus = SearchHistorySummaryStatus.NeedsSummary,
                generatedAtLabel = "요약 생성 필요 · 서버 handover boundary 대기",
                summary = null,
                sourceReadiness = SummarySourceReadiness.Stale
            )

        fun unavailable(): DutyHandoverUiState =
            base(
                summaryStatus = SearchHistorySummaryStatus.Unavailable,
                generatedAtLabel = "요약을 불러오지 못했습니다 · 원본 기록 유지",
                summary = null,
                sourceReadiness = SummarySourceReadiness.Ready
            )

        fun empty(): DutyHandoverUiState =
            base(
                summaryStatus = SearchHistorySummaryStatus.Empty,
                generatedAtLabel = "이전 기록 없음",
                summary = null,
                sourceReadiness = SummarySourceReadiness.Ready,
                metrics = emptyList(),
                records = emptyList()
            )

        private fun base(
            summaryStatus: SearchHistorySummaryStatus,
            generatedAtLabel: String,
            summary: String?,
            sourceReadiness: SummarySourceReadiness,
            metrics: List<HandoverMetric> =
                listOf(
                    HandoverMetric("1.8km", "도보 경로"),
                    HandoverMetric("4건", "마커"),
                    HandoverMetric("2건", "메모")
                ),
            records: List<HandoverRecord> =
                listOf(
                    HandoverRecord("도보 경로 · 동쪽 능선", "12:07-13:18 · GPS 일부 약함", "보기"),
                    HandoverRecord("단서 마커 · 배수로 입구", "사진 2장 · 작성 13:36", "열기"),
                    HandoverRecord("운영 메모 · 북측 진입로", "주민 진술 대기, 배수로 아래 확인 필요", "열기")
                ),
            replayPathSegments: List<HandoverReplayPathSegment> =
                listOf(
                    HandoverReplayPathSegment(
                        label = "도보 경로 · 동쪽 능선",
                        timeRangeLabel = "12:07-13:18",
                        distanceLabel = "1.8km",
                        modeLabel = "도보"
                    )
                ),
            replayMarkers: List<HandoverReplayMarker> =
                listOf(
                    HandoverReplayMarker(
                        title = "단서 마커 · 배수로 입구",
                        timeLabel = "13:36",
                        typeLabel = "단서",
                        photoCountLabel = "사진 2장"
                    )
                )
        ): DutyHandoverUiState =
            DutyHandoverUiState(
                title = "이전 근무 확인",
                subtitle = "OP 3차 · 교대 인수인계",
                summaryStatus = summaryStatus,
                generatedAtLabel = generatedAtLabel,
                summary = summary,
                sourceReadiness = sourceReadiness,
                metrics = metrics,
                records = records,
                replayPathSegments = replayPathSegments,
                replayMarkers = replayMarkers,
                canRequestSummaryGeneration = false
            )
    }
}

enum class SearchHistorySummaryStatus {
    Ready,
    Generating,
    NeedsSummary,
    Unavailable,
    Empty
}

enum class SummarySourceReadiness {
    PendingSync,
    Ready,
    Stale
}

enum class DutyHandoverTab(val label: String) {
    Replay("리플레이"),
    Report("보고서")
}

data class HandoverMetric(val value: String, val label: String)
data class HandoverRecord(val title: String, val subtitle: String, val actionLabel: String)
data class HandoverReplayPathSegment(
    val label: String,
    val timeRangeLabel: String,
    val distanceLabel: String,
    val modeLabel: String
)
data class HandoverReplayMarker(
    val title: String,
    val timeLabel: String,
    val typeLabel: String,
    val photoCountLabel: String
)

data class HandoverReplayControlUiState(
    val playing: Boolean = false,
    val displayPlayheadMs: Long = 0L,
    val displayDurationMs: Long = 0L,
    val speed: HandoverReplaySpeed = HandoverReplaySpeed.X1,
    val cameraMode: HandoverReplayCameraMode = HandoverReplayCameraMode.Overview
) {
    val playPauseLabel: String = if (playing) "일시정지" else "재생"
    val currentTimeLabel: String = formatReplayElapsed(displayPlayheadMs)
    val durationLabel: String = formatReplayElapsed(displayDurationMs)
    val timeRangeLabel: String = "$currentTimeLabel / $durationLabel"
    val sliderPosition: Float =
        if (displayDurationMs <= 0L) {
            0f
        } else {
            (displayPlayheadMs.toFloat() / displayDurationMs.toFloat()).coerceIn(0f, 1f)
        }

    fun togglePlaying(): HandoverReplayControlUiState =
        copy(playing = !playing)

    fun seekTo(displayPlayheadMs: Long): HandoverReplayControlUiState =
        copy(displayPlayheadMs = displayPlayheadMs.coerceIn(0L, displayDurationMs.coerceAtLeast(0L)))

    fun selectSpeed(speed: HandoverReplaySpeed): HandoverReplayControlUiState =
        copy(speed = speed)

    fun selectCameraMode(cameraMode: HandoverReplayCameraMode): HandoverReplayControlUiState =
        copy(cameraMode = cameraMode)

    fun withDuration(displayDurationMs: Long): HandoverReplayControlUiState {
        val duration = displayDurationMs.coerceAtLeast(0L)
        return copy(
            displayDurationMs = duration,
            displayPlayheadMs = this.displayPlayheadMs.coerceIn(0L, duration)
        )
    }
}

private val HandoverReplaySections =
    listOf("경로 미리보기", "마커", "타임라인")

private val HandoverReportSections =
    listOf(
        "근무 개요",
        "서버 인수인계 요약",
        "이동 통계",
        "발견·기록 시간순",
        "인수인계 메모",
        "마커 사진",
        "동기화 상태"
    )

data class HandoverPromptUiState(
    val currentDutyShiftStartedAt: Instant?,
    val lastSeenHandoverAt: Instant? = null
) {
    val shouldShow: Boolean =
        currentDutyShiftStartedAt != null &&
            (lastSeenHandoverAt == null || currentDutyShiftStartedAt.isAfter(lastSeenHandoverAt))

    companion object {
        fun unreadSample(): HandoverPromptUiState =
            HandoverPromptUiState(
                currentDutyShiftStartedAt = Instant.parse("2026-04-28T05:00:00Z"),
                lastSeenHandoverAt = Instant.parse("2026-04-28T04:00:00Z")
            )
    }
}

@Composable
fun DutyHandoverScreen(
    state: DutyHandoverUiState,
    onBack: () -> Unit,
    onWriteMemo: () -> Unit,
    onOpenSearch: () -> Unit,
    onSelectTab: (DutyHandoverTab) -> Unit = {},
    onEndDutyShift: () -> Unit = {},
    onReplayPlayPause: () -> Unit = {},
    onReplaySeek: (Long) -> Unit = {},
    onReplaySpeedSelect: (HandoverReplaySpeed) -> Unit = {},
    onReplayCameraModeSelect: (HandoverReplayCameraMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        PoliAppBar(title = state.title, subtitle = state.subtitle, showBack = true, onBack = onBack)
        DutyHandoverTabRow(selectedTab = state.selectedTab, onSelectTab = onSelectTab)
        Column(
            modifier =
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PoliDimens.SectionPadding),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
        ) {
            when (state.selectedTab) {
                DutyHandoverTab.Replay ->
                    ReplayTab(
                        state = state,
                        onReplayPlayPause = onReplayPlayPause,
                        onReplaySeek = onReplaySeek,
                        onReplaySpeedSelect = onReplaySpeedSelect,
                        onReplayCameraModeSelect = onReplayCameraModeSelect
                    )
                DutyHandoverTab.Report -> ReportTab(state)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(PoliDimens.SectionPadding),
            horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
        ) {
            PoliButton(
                text = "메모 작성",
                onClick = onWriteMemo,
                modifier = Modifier.weight(1f),
                variant = PoliButtonVariant.Secondary
            )
            PoliButton(text = "수색 화면", onClick = onOpenSearch, modifier = Modifier.weight(1.25f))
        }
        if (state.canEndDutyShift) {
            PoliButton(
                text = state.dutyShiftActionLabel,
                onClick = onEndDutyShift,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PoliDimens.SectionPadding,
                        end = PoliDimens.SectionPadding,
                        bottom = PoliDimens.SectionPadding
                    ),
                enabled = !state.endingDutyShift,
                variant = PoliButtonVariant.Danger
            )
        }
    }
}

@Composable
private fun DutyHandoverTabRow(
    selectedTab: DutyHandoverTab,
    onSelectTab: (DutyHandoverTab) -> Unit
) {
    SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
        DutyHandoverTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                text = { Text(text = tab.label) }
            )
        }
    }
}

@Composable
private fun ReplayTab(
    state: DutyHandoverUiState,
    onReplayPlayPause: () -> Unit,
    onReplaySeek: (Long) -> Unit,
    onReplaySpeedSelect: (HandoverReplaySpeed) -> Unit,
    onReplayCameraModeSelect: (HandoverReplayCameraMode) -> Unit
) {
    ReplayPathPreviewCard(state)
    ReplayControlCard(
        control = state.replayControl,
        onPlayPause = onReplayPlayPause,
        onSeek = onReplaySeek,
        onSpeedSelect = onReplaySpeedSelect,
        onCameraModeSelect = onReplayCameraModeSelect
    )
    ReplayMarkerCard(markers = state.replayMarkers)
    RecordCard(title = "타임라인", records = state.records)
}

@Composable
private fun ReplayPathPreviewCard(state: DutyHandoverUiState) {
    PoliCard(strong = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
            state.replayBadges.forEach { badge ->
                PoliChip(text = badge)
            }
        }
        Text(text = "경로 미리보기", style = MaterialTheme.typography.titleMedium)
        Text(
            text = state.generatedAtLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = PoliFgMuted
        )
        StaticRoutePreview(hasPath = state.replayPathSegments.isNotEmpty(), markerCount = state.replayMarkers.size)
        if (state.metrics.isNotEmpty()) {
            MetricRow(metrics = state.metrics)
        }
        if (state.replayPathSegments.isEmpty()) {
            EmptyReportText("경로 데이터 없음")
        } else {
            state.replayPathSegments.forEach { segment ->
                PoliRow(
                    title = segment.label,
                    subtitle = "${segment.timeRangeLabel} · ${segment.distanceLabel}"
                ) {
                    PoliChip(text = segment.modeLabel)
                }
            }
        }
    }
}

@Composable
private fun StaticRoutePreview(hasPath: Boolean, markerCount: Int) {
    val shape = MaterialTheme.shapes.medium
    Canvas(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(156.dp)
            .clip(shape)
            .background(PoliBgInput)
            .padding(PoliDimens.Space4)
    ) {
        if (!hasPath) {
            drawCircle(
                color = PoliFgMuted,
                radius = 8.dp.toPx(),
                center = Offset(size.width * 0.5f, size.height * 0.5f)
            )
            return@Canvas
        }

        val start = Offset(size.width * 0.12f, size.height * 0.70f)
        val midA = Offset(size.width * 0.38f, size.height * 0.42f)
        val midB = Offset(size.width * 0.62f, size.height * 0.55f)
        val end = Offset(size.width * 0.86f, size.height * 0.24f)
        val stroke = 7.dp.toPx()
        drawLine(PoliPrimaryMid, start, midA, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(PoliPrimaryMid, midA, midB, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(PoliPrimaryMid, midB, end, strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(PoliCurrent, radius = 7.dp.toPx(), center = start)
        drawCircle(PoliCurrent, radius = 7.dp.toPx(), center = end)

        val markerPositions = listOf(midA, midB, end).take(markerCount.coerceAtMost(3))
        markerPositions.forEach { position ->
            drawCircle(PoliWarning, radius = 9.dp.toPx(), center = position)
            drawCircle(PoliBgInput, radius = 4.dp.toPx(), center = position)
        }
    }
}

@Composable
private fun ReplayControlCard(
    control: HandoverReplayControlUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedSelect: (HandoverReplaySpeed) -> Unit,
    onCameraModeSelect: (HandoverReplayCameraMode) -> Unit
) {
    ReportSectionCard(title = "리플레이 컨트롤") {
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
            PoliButton(
                text = control.playPauseLabel,
                onClick = onPlayPause,
                enabled = control.displayDurationMs > 0L,
                size = PoliButtonSize.Small,
                modifier = Modifier.weight(0.8f)
            )
            PoliField(
                label = "시간",
                value = control.timeRangeLabel,
                modifier = Modifier.weight(1.2f)
            )
        }
        Slider(
            value = control.sliderPosition,
            onValueChange = { position ->
                onSeek((position * control.displayDurationMs).roundToLong())
            },
            enabled = control.displayDurationMs > 0L
        )
        ReplaySpeedControls(selectedSpeed = control.speed, onSpeedSelect = onSpeedSelect)
        ReplayCameraControls(selectedMode = control.cameraMode, onCameraModeSelect = onCameraModeSelect)
    }
}

@Composable
private fun ReplaySpeedControls(
    selectedSpeed: HandoverReplaySpeed,
    onSpeedSelect: (HandoverReplaySpeed) -> Unit
) {
    Text(text = "속도", style = MaterialTheme.typography.labelLarge, color = PoliFgMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
        HandoverReplaySpeed.entries.forEach { speed ->
            ReplayOptionButton(
                text = speed.label,
                selected = speed == selectedSpeed,
                onClick = { onSpeedSelect(speed) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ReplayCameraControls(
    selectedMode: HandoverReplayCameraMode,
    onCameraModeSelect: (HandoverReplayCameraMode) -> Unit
) {
    Text(text = "카메라", style = MaterialTheme.typography.labelLarge, color = PoliFgMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
        HandoverReplayCameraMode.entries.forEach { mode ->
            ReplayOptionButton(
                text = mode.label,
                selected = mode == selectedMode,
                onClick = { onCameraModeSelect(mode) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ReplayOptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PoliButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        size = PoliButtonSize.Small,
        variant = if (selected) PoliButtonVariant.Primary else PoliButtonVariant.Secondary
    )
}

@Composable
private fun ReplayMarkerCard(markers: List<HandoverReplayMarker>) {
    ReportSectionCard(title = "마커") {
        if (markers.isEmpty()) {
            EmptyReportText("표시할 마커 없음")
        } else {
            markers.forEach { marker ->
                PoliRow(
                    title = marker.title,
                    subtitle = "${marker.timeLabel} · ${marker.photoCountLabel}"
                ) {
                    PoliChip(text = marker.typeLabel)
                }
            }
        }
    }
}

@Composable
private fun ReportTab(state: DutyHandoverUiState) {
    ReportSectionCard(title = "근무 개요") {
        PoliField(label = "대상", value = state.subtitle)
        PoliField(label = "기록 기준", value = state.generatedAtLabel)
    }
    SummaryCard(state)
    ReportSectionCard(title = "이동 통계") {
        if (state.metrics.isEmpty()) {
            EmptyReportText("이동 통계 없음")
        } else {
            MetricRow(metrics = state.metrics)
        }
    }
    RecordCard(title = "발견·기록 시간순", records = state.records)
    RecordCard(
        title = "인수인계 메모",
        records = state.handoverMemoRecords,
        emptyText = "인수인계 메모 없음"
    )
    RecordCard(
        title = "마커 사진",
        records = state.markerPhotoRecords,
        emptyText = "연결된 마커 사진 없음"
    )
    ReportSectionCard(title = "동기화 상태") {
        PoliChip(text = state.sourceReadiness.reportLabel, variant = state.sourceReadiness.variant)
        Text(
            text = state.sourceReadiness.reportCopy,
            style = MaterialTheme.typography.bodyMedium,
            color = PoliFgMuted
        )
    }
}

@Composable
private fun ReportSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    PoliCard {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun RecordCard(
    title: String,
    records: List<HandoverRecord>,
    emptyText: String = "이전 기록 없음"
) {
    ReportSectionCard(title = title) {
        if (records.isEmpty()) {
            EmptyReportText(emptyText)
        } else {
            records.forEach { record ->
                PoliRow(title = record.title, subtitle = record.subtitle) {
                    PoliChip(text = record.actionLabel)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: DutyHandoverUiState) {
    PoliCard(strong = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
                Text(text = "서버 인수인계 요약", style = MaterialTheme.typography.titleMedium)
                Text(text = state.generatedAtLabel, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
            }
            PoliChip(text = state.summaryStatus.label, variant = state.summaryStatus.variant)
        }
        Text(text = state.summaryText, style = MaterialTheme.typography.bodyLarge, color = PoliFgSecondary)
        state.summaryActionLabel?.let { actionLabel ->
            PoliChip(text = actionLabel, variant = PoliChipVariant.Outbox)
        }
    }
}

@Composable
private fun MetricRow(metrics: List<HandoverMetric>) {
    Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
        metrics.forEach { metric ->
            PoliField(label = metric.label, value = metric.value, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmptyReportText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = PoliFgMuted
    )
}

private val SummarySourceReadiness.reportLabel: String
    get() =
        when (this) {
            SummarySourceReadiness.PendingSync -> "동기화 대기"
            SummarySourceReadiness.Ready -> "동기화 완료"
            SummarySourceReadiness.Stale -> "기록 갱신 필요"
        }

private val SummarySourceReadiness.reportCopy: String
    get() =
        when (this) {
            SummarySourceReadiness.PendingSync -> "서버 반영 전 기록이 있어 원본 기록을 함께 확인합니다."
            SummarySourceReadiness.Ready -> "서버 기록 기준으로 보고서를 표시합니다."
            SummarySourceReadiness.Stale -> "새 기록 반영 전 상태입니다. 원본 기록을 함께 확인합니다."
        }

private val SummarySourceReadiness.variant: PoliChipVariant
    get() =
        when (this) {
            SummarySourceReadiness.PendingSync -> PoliChipVariant.Outbox
            SummarySourceReadiness.Ready -> PoliChipVariant.Good
            SummarySourceReadiness.Stale -> PoliChipVariant.Warn
        }

private val SearchHistorySummaryStatus.label: String
    get() =
        when (this) {
            SearchHistorySummaryStatus.Ready -> "준비됨"
            SearchHistorySummaryStatus.Generating -> "자동 처리 중"
            SearchHistorySummaryStatus.NeedsSummary -> "요약 생성 필요"
            SearchHistorySummaryStatus.Unavailable -> "요약 확인 필요"
            SearchHistorySummaryStatus.Empty -> "이전 기록 없음"
        }

private val SearchHistorySummaryStatus.variant: PoliChipVariant
    get() =
        when (this) {
            SearchHistorySummaryStatus.Ready -> PoliChipVariant.Good
            SearchHistorySummaryStatus.Generating -> PoliChipVariant.Warn
            SearchHistorySummaryStatus.NeedsSummary -> PoliChipVariant.Outbox
            SearchHistorySummaryStatus.Unavailable -> PoliChipVariant.Bad
            SearchHistorySummaryStatus.Empty -> PoliChipVariant.Neutral
        }

private val SearchHistorySummaryStatus.emptyCopy: String
    get() =
        when (this) {
            SearchHistorySummaryStatus.Ready -> ""
            SearchHistorySummaryStatus.Generating -> "이전 근무 기록을 자동 처리 중입니다. 원본 기록은 즉시 확인할 수 있습니다."
            SearchHistorySummaryStatus.NeedsSummary -> "요약 생성 필요 상태입니다. 공개 생성 API가 없으므로 원본 기록을 먼저 확인합니다."
            SearchHistorySummaryStatus.Unavailable -> "요약을 불러오지 못했습니다. 원본 경로·마커·메모는 계속 확인할 수 있습니다."
            SearchHistorySummaryStatus.Empty -> "이전 기록 없음"
        }

private val HandoverReplaySpeed.label: String
    get() =
        when (this) {
            HandoverReplaySpeed.X1 -> "1x"
            HandoverReplaySpeed.X4 -> "4x"
            HandoverReplaySpeed.X16 -> "16x"
            HandoverReplaySpeed.X60 -> "60x"
        }

private val HandoverReplayCameraMode.label: String
    get() =
        when (this) {
            HandoverReplayCameraMode.Overview -> "전체"
            HandoverReplayCameraMode.FollowPlayhead -> "추적"
            HandoverReplayCameraMode.Free -> "자유"
        }

private fun formatReplayElapsed(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, remainingMinutes, remainingSeconds)
    } else {
        "%02d:%02d".format(remainingMinutes, remainingSeconds)
    }
}

fun sampleDutyHandoverState(): DutyHandoverUiState = DutyHandoverUiState.ready()

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun DutyHandoverPreview() {
    SuriMapTheme {
        DutyHandoverScreen(
            state = sampleDutyHandoverState(),
            onBack = {},
            onWriteMemo = {},
            onOpenSearch = {}
        )
    }
}
