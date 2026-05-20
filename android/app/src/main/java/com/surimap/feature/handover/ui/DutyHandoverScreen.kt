package com.surimap.feature.handover.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlin.math.roundToLong
import com.surimap.core.map.MapLibreGeometryOverlay
import com.surimap.core.map.MapLibreGeometryOverlayKind
import com.surimap.core.map.MapLibreRuntimeMapState
import com.surimap.core.map.MapLibreViewportBounds
import com.surimap.core.map.SuriMapLibreMap
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
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgSecondary
import com.surimap.ui.theme.PoliPrimaryFillSoft
import com.surimap.ui.theme.SuriMapTheme

data class DutyHandoverUiState(
    val title: String,
    val subtitle: String,
    val recordScope: HandoverRecordScope = HandoverRecordScope.DutyShift,
    val summaryStatus: SearchHistorySummaryStatus,
    val generatedAtLabel: String,
    val summary: String?,
    val sourceReadiness: SummarySourceReadiness,
    val metrics: List<HandoverMetric>,
    val records: List<HandoverRecord>,
    val dutyShiftOptions: List<HandoverDutyShiftOption> = emptyList(),
    val replayPoints: List<HandoverReplayPointUi> = emptyList(),
    val replayPathSegments: List<HandoverReplayPathSegment> = emptyList(),
    val replayMarkers: List<HandoverReplayMarker> = emptyList(),
    val replayControl: HandoverReplayControlUiState = HandoverReplayControlUiState(),
    val selectedTab: DutyHandoverTab = DutyHandoverTab.Replay,
    val selectedOriginalRecordKey: String? = null,
    val canEndDutyShift: Boolean = false,
    val endingDutyShift: Boolean = false,
    val canRequestSummaryGeneration: Boolean = false
) {
    val replaySectionTitles: List<String> = HandoverReplaySections
    val replayBadges: List<String> =
        if (replayControl.displayDurationMs > 0L && replayPoints.size >= 2) {
            listOf(recordScope.primaryBadge, recordScope.actorBadge, "타임라인 재생")
        } else {
            listOf(recordScope.primaryBadge, recordScope.actorBadge, "기록 없음")
        }

    val summaryTitle: String = recordScope.summaryTitle
    val overviewTitle: String = recordScope.overviewTitle
    val emptyRecordLabel: String = recordScope.emptyRecordLabel
    val summaryStatusLabel: String = summaryStatus.label

    val reportSectionTitles: List<String> = HandoverReportSections.map { title ->
        when (title) {
            DUTY_SHIFT_OVERVIEW_TITLE -> overviewTitle
            DUTY_SHIFT_SUMMARY_TITLE -> summaryTitle
            else -> title
        }
    }

    val handoverMemoRecords: List<HandoverRecord> =
        records.filter { record ->
            record.title.contains("메모") || record.subtitle.contains("메모")
        }

    val markerPhotoRecords: List<HandoverRecord> =
        records.filter { record ->
            record.title.contains("마커") && record.subtitle.contains("사진")
        }

    val summaryText: String =
        summary ?: summaryStatus.emptyCopy()

    val selectedOriginalRecord: HandoverRecord? =
        selectedOriginalRecordKey?.let { selectedKey ->
            records.firstOrNull { record -> record.sourceKey == selectedKey }
        }

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
            add(summaryTitle)
            add(summaryStatusLabel)
            add(generatedAtLabel)
            add(summaryText)
            dutyShiftOptions.forEach { option ->
                add(option.label)
                add(option.subtitle)
            }
            summaryActionLabel?.let(::add)
            selectedOriginalRecord?.let { selectedRecord ->
                add("선택된 원본 기록")
                add(selectedRecord.title)
                add(selectedRecord.subtitle)
            }
            if (selectedTab == DutyHandoverTab.Replay) {
                addAll(replaySectionTitles)
                addAll(replayBadges)
                add("리플레이 컨트롤")
                add(replayControl.playPauseLabel)
                add(replayControl.timeRangeLabel)
                add("속도")
                HandoverReplaySpeed.entries.forEach { add(it.label) }
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

    fun selectOriginalRecord(sourceKey: String): DutyHandoverUiState =
        if (records.any { record -> record.sourceKey == sourceKey }) {
            copy(selectedTab = DutyHandoverTab.Report, selectedOriginalRecordKey = sourceKey)
        } else {
            copy(selectedOriginalRecordKey = null)
        }

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
            replayPathSegments: List<HandoverReplayPathSegment>? = null,
            replayMarkers: List<HandoverReplayMarker>? = null
        ): DutyHandoverUiState {
            val sampleReplayPoints =
                listOf(
                    HandoverReplayPointUi(0L, 37.5761, 126.9769),
                    HandoverReplayPointUi(45_000L, 37.5771, 126.9781),
                    HandoverReplayPointUi(90_000L, 37.5768, 126.9802),
                    HandoverReplayPointUi(120_000L, 37.5784, 126.9818)
                )
            val displayPathSegments =
                replayPathSegments ?: listOf(
                    HandoverReplayPathSegment(
                        label = "도보 경로 · 동쪽 능선",
                        timeRangeLabel = "12:07-13:18",
                        distanceLabel = "1.8km",
                        modeLabel = "도보",
                        points = sampleReplayPoints
                    )
                )
            val displayMarkers =
                replayMarkers ?: listOf(
                    HandoverReplayMarker(
                        title = "단서 마커 · 배수로 입구",
                        timeLabel = "13:36",
                        typeLabel = "단서",
                        photoCountLabel = "사진 2장",
                        elapsedMs = 89_000L,
                        lat = 37.5768,
                        lng = 126.9802
                    )
                )
            return DutyHandoverUiState(
                title = "이전 근무 확인",
                subtitle = "OP 3차 · 교대 인수인계",
                summaryStatus = summaryStatus,
                generatedAtLabel = generatedAtLabel,
                summary = summary,
                sourceReadiness = sourceReadiness,
                metrics = metrics,
                records = records,
                replayPoints = sampleReplayPoints,
                replayPathSegments = displayPathSegments,
                replayMarkers = displayMarkers,
                replayControl = HandoverReplayControlUiState(displayDurationMs = 120_000L),
                canRequestSummaryGeneration = false
            )
        }
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

enum class HandoverRecordScope {
    DutyShift
}

enum class DutyHandoverTab(val label: String) {
    Replay("리플레이"),
    Report("보고서")
}

data class HandoverMetric(val value: String, val label: String)
data class HandoverRecord(
    val title: String,
    val subtitle: String,
    val actionLabel: String,
    val sourceKey: String = "$title|$subtitle"
)
data class HandoverReplayPointUi(
    val elapsedMs: Long,
    val lat: Double,
    val lng: Double
)
data class HandoverReplayPathSegment(
    val label: String,
    val timeRangeLabel: String,
    val distanceLabel: String,
    val modeLabel: String,
    val sourceKey: String = "$label|$timeRangeLabel",
    val points: List<HandoverReplayPointUi> = emptyList()
)

data class HandoverDutyShiftOption(
    val dutyShiftId: String,
    val label: String,
    val subtitle: String,
    val selected: Boolean = false
)
data class HandoverReplayMarker(
    val title: String,
    val timeLabel: String,
    val typeLabel: String,
    val photoCountLabel: String,
    val elapsedMs: Long? = null,
    val lat: Double? = null,
    val lng: Double? = null
)

data class HandoverReplayControlUiState(
    val playing: Boolean = false,
    val displayPlayheadMs: Long = 0L,
    val displayDurationMs: Long = 0L,
    val speed: HandoverReplaySpeed = HandoverReplaySpeed.X1
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

    fun togglePlaying(): HandoverReplayControlUiState {
        if (displayDurationMs <= 0L) {
            return copy(playing = false, displayPlayheadMs = 0L)
        }
        if (!playing && displayPlayheadMs >= displayDurationMs) {
            return copy(playing = true, displayPlayheadMs = 0L)
        }
        return copy(playing = !playing)
    }

    fun seekTo(displayPlayheadMs: Long): HandoverReplayControlUiState =
        copy(displayPlayheadMs = displayPlayheadMs.coerceIn(0L, displayDurationMs.coerceAtLeast(0L)))

    fun selectSpeed(speed: HandoverReplaySpeed): HandoverReplayControlUiState =
        copy(speed = speed)

    fun advanceBy(realElapsedMs: Long): HandoverReplayControlUiState {
        if (!playing || displayDurationMs <= 0L) {
            return copy(playing = false)
        }
        val nextPlayheadMs =
            (displayPlayheadMs + (realElapsedMs.coerceAtLeast(0L) * speed.multiplier))
                .coerceIn(0L, displayDurationMs)
        return copy(
            displayPlayheadMs = nextPlayheadMs,
            playing = nextPlayheadMs < displayDurationMs
        )
    }

    fun withDuration(displayDurationMs: Long): HandoverReplayControlUiState {
        val duration = displayDurationMs.coerceAtLeast(0L)
        return copy(
            displayDurationMs = duration,
            displayPlayheadMs = this.displayPlayheadMs.coerceIn(0L, duration),
            playing = playing && duration > 0L && displayPlayheadMs < duration
        )
    }
}

private val HandoverReplaySections =
    listOf("지도 리플레이", "경로 개요", "마커", "타임라인")

private val HandoverReportSections =
    listOf(
        DUTY_SHIFT_OVERVIEW_TITLE,
        DUTY_SHIFT_SUMMARY_TITLE,
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
    mapState: MapLibreRuntimeMapState = MapLibreRuntimeMapState(),
    onBack: () -> Unit,
    onWriteMemo: () -> Unit,
    onOpenSearch: () -> Unit,
    onSelectTab: (DutyHandoverTab) -> Unit = {},
    onSelectDutyShift: (String) -> Unit = {},
    onEndDutyShift: () -> Unit = {},
    onReplayPlayPause: () -> Unit = {},
    onReplaySeek: (Long) -> Unit = {},
    onReplaySpeedSelect: (HandoverReplaySpeed) -> Unit = {},
    onSelectOriginalRecord: (HandoverRecord) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        PoliAppBar(title = state.title, subtitle = state.subtitle, showBack = true, onBack = onBack)
        DutyHandoverTabRow(
            selectedTab = state.selectedTab,
            onSelectTab = onSelectTab
        )
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
                        mapState = mapState,
                        onSelectDutyShift = onSelectDutyShift,
                        onReplayPlayPause = onReplayPlayPause,
                        onReplaySeek = onReplaySeek,
                        onReplaySpeedSelect = onReplaySpeedSelect
                    )
                DutyHandoverTab.Report ->
                    ReportTab(
                        state = state,
                        onSelectOriginalRecord = onSelectOriginalRecord
                    )
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
    mapState: MapLibreRuntimeMapState,
    onSelectDutyShift: (String) -> Unit,
    onReplayPlayPause: () -> Unit,
    onReplaySeek: (Long) -> Unit,
    onReplaySpeedSelect: (HandoverReplaySpeed) -> Unit
) {
    DutyShiftSelectorCard(options = state.dutyShiftOptions, onSelectDutyShift = onSelectDutyShift)
    ReplayMapCard(state = state, mapState = mapState)
    ReplayControlCard(
        control = state.replayControl,
        onPlayPause = onReplayPlayPause,
        onSeek = onReplaySeek,
        onSpeedSelect = onReplaySpeedSelect
    )
    ReplayPathSummaryCard(state)
    ReplayMarkerCard(markers = state.replayMarkers)
    RecordCard(title = "타임라인", records = state.records)
}

@Composable
private fun ReplayPathSummaryCard(state: DutyHandoverUiState) {
    PoliCard(strong = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
            state.replayBadges.forEach { badge ->
                PoliChip(text = badge)
            }
        }
        Text(text = "경로 개요", style = MaterialTheme.typography.titleMedium)
        Text(
            text = state.generatedAtLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = PoliFgMuted
        )
        if (state.metrics.isNotEmpty()) {
            MetricRow(metrics = state.metrics)
        }
        if (state.replayPathSegments.isEmpty()) {
            EmptyReportText("선택한 근무 구간에 경로 데이터 없음")
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
private fun DutyShiftSelectorCard(
    options: List<HandoverDutyShiftOption>,
    onSelectDutyShift: (String) -> Unit
) {
    if (options.isEmpty()) {
        return
    }
    ReportSectionCard(title = "리플레이 근무 선택") {
        options.forEach { option ->
            PoliRow(
                title = option.label,
                subtitle = option.subtitle,
                modifier = Modifier.clickable { onSelectDutyShift(option.dutyShiftId) }
            ) {
                if (option.selected) {
                    PoliChip(text = "선택됨", variant = PoliChipVariant.Good)
                }
            }
        }
    }
}

@Composable
private fun ReplayMapCard(
    state: DutyHandoverUiState,
    mapState: MapLibreRuntimeMapState
) {
    ReportSectionCard(title = "지도 리플레이") {
        if (state.replayPathSegments.none { it.points.size >= 2 }) {
            EmptyReportText("선택한 근무 구간에 지도에 표시할 경로 데이터 없음")
            return@ReportSectionCard
        }
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(PoliBgInput)
        ) {
            SuriMapLibreMap(
                state = state.toReplayRuntimeMapState(mapState),
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = state.replayControl.timeRangeLabel,
                style = MaterialTheme.typography.labelLarge,
                color = PoliFgSecondary,
                modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(PoliDimens.Space3)
                    .background(PoliBgInput, MaterialTheme.shapes.small)
                    .padding(horizontal = PoliDimens.Space2, vertical = PoliDimens.Space1)
            )
        }
    }
}

private fun List<HandoverReplayPointUi>.positionAt(elapsedMs: Long): HandoverReplayPointUi? {
    val points = sortedBy(HandoverReplayPointUi::elapsedMs)
    val first = points.firstOrNull() ?: return null
    if (elapsedMs <= first.elapsedMs) {
        return first
    }
    val last = points.last()
    if (elapsedMs >= last.elapsedMs) {
        return last
    }
    val nextIndex = points.indexOfFirst { it.elapsedMs >= elapsedMs }
    val previous = points[nextIndex - 1]
    val next = points[nextIndex]
    val delta = next.elapsedMs - previous.elapsedMs
    if (delta <= 0L) {
        return next
    }
    val ratio = (elapsedMs - previous.elapsedMs).toDouble() / delta.toDouble()
    return HandoverReplayPointUi(
        elapsedMs = elapsedMs,
        lat = previous.lat + ((next.lat - previous.lat) * ratio),
        lng = previous.lng + ((next.lng - previous.lng) * ratio)
    )
}

internal fun DutyHandoverUiState.toReplayRuntimeMapState(base: MapLibreRuntimeMapState): MapLibreRuntimeMapState {
    val playheadPoint = replayPoints.positionAt(replayControl.displayPlayheadMs)
    val pathOverlays =
        replayPathSegments.flatMap { segment ->
            val fullPath =
                segment.points.lineStringGeoJson()?.let { geoJson ->
                    MapLibreGeometryOverlay(
                        id = "handover-path-full-${segment.sourceKey}",
                        kind = MapLibreGeometryOverlayKind.Path,
                        geoJson = geoJson,
                        highlighted = false,
                        label = segment.label
                    )
                }
            val progressedPath =
                segment.points
                    .pointsUntil(replayControl.displayPlayheadMs)
                    .lineStringGeoJson()
                    ?.let { geoJson ->
                        MapLibreGeometryOverlay(
                            id = "handover-path-progress-${segment.sourceKey}",
                            kind = MapLibreGeometryOverlayKind.Path,
                            geoJson = geoJson,
                            highlighted = true
                        )
                    }
            listOfNotNull(fullPath, progressedPath)
        }
    val markerOverlays =
        replayMarkers.mapIndexedNotNull { index, marker ->
            if (!marker.isVisibleAt(replayControl.displayPlayheadMs)) {
                return@mapIndexedNotNull null
            }
            val lat = marker.lat ?: return@mapIndexedNotNull null
            val lng = marker.lng ?: return@mapIndexedNotNull null
            MapLibreGeometryOverlay(
                id = "handover-marker-$index",
                kind = MapLibreGeometryOverlayKind.Marker,
                geoJson = pointGeoJson(lat = lat, lng = lng),
                highlighted = true,
                label = marker.typeLabel
            )
        }
    val playheadOverlay =
        playheadPoint?.let { point ->
            MapLibreGeometryOverlay(
                id = "handover-playhead",
                kind = MapLibreGeometryOverlayKind.CurrentLocation,
                geoJson = pointGeoJson(lat = point.lat, lng = point.lng),
                highlighted = true,
                label = "현재 재생 위치"
            )
        }
    return base.copy(
        initialBounds = replayMapBounds(playheadPoint) ?: base.initialBounds,
        geometryOverlays = pathOverlays + markerOverlays + listOfNotNull(playheadOverlay)
    )
}

private fun List<HandoverReplayPointUi>.pointsUntil(elapsedMs: Long): List<HandoverReplayPointUi> {
    val points = sortedBy(HandoverReplayPointUi::elapsedMs)
    val first = points.firstOrNull() ?: return emptyList()
    val clampedElapsedMs = elapsedMs.coerceAtLeast(first.elapsedMs)
    val progressed = points.takeWhile { point -> point.elapsedMs <= clampedElapsedMs }
    val playheadPoint = positionAt(clampedElapsedMs)
    return (progressed + listOfNotNull(playheadPoint))
        .distinctBy { point -> point.elapsedMs }
        .sortedBy(HandoverReplayPointUi::elapsedMs)
}

private fun HandoverReplayMarker.isVisibleAt(displayPlayheadMs: Long): Boolean =
    elapsedMs == null || elapsedMs <= displayPlayheadMs

private fun DutyHandoverUiState.replayMapBounds(playheadPoint: HandoverReplayPointUi?): MapLibreViewportBounds? {
    val points =
        replayPathSegments.flatMap { it.points } +
            replayMarkers.mapNotNull { marker ->
                val lat = marker.lat ?: return@mapNotNull null
                val lng = marker.lng ?: return@mapNotNull null
                HandoverReplayPointUi(elapsedMs = 0L, lat = lat, lng = lng)
            } +
            listOfNotNull(playheadPoint)
    if (points.isEmpty()) {
        return null
    }
    val minLat = points.minOf(HandoverReplayPointUi::lat)
    val maxLat = points.maxOf(HandoverReplayPointUi::lat)
    val minLng = points.minOf(HandoverReplayPointUi::lng)
    val maxLng = points.maxOf(HandoverReplayPointUi::lng)
    val latPadding = ((maxLat - minLat) * 0.16).coerceAtLeast(0.0005)
    val lngPadding = ((maxLng - minLng) * 0.16).coerceAtLeast(0.0005)
    return MapLibreViewportBounds(
        south = minLat - latPadding,
        west = minLng - lngPadding,
        north = maxLat + latPadding,
        east = maxLng + lngPadding
    )
}

private fun List<HandoverReplayPointUi>.lineStringGeoJson(): String? {
    val coordinates =
        sortedBy(HandoverReplayPointUi::elapsedMs)
            .filter { point -> point.lat.isFinite() && point.lng.isFinite() }
            .takeIf { it.size >= 2 }
            ?: return null
    return """{"type":"LineString","coordinates":[${coordinates.joinToString(",") { point -> "[${point.lng},${point.lat}]" }}]}"""
}

private fun pointGeoJson(lat: Double, lng: Double): String =
    """{"type":"Point","coordinates":[$lng,$lat]}"""

@Composable
private fun ReplayControlCard(
    control: HandoverReplayControlUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedSelect: (HandoverReplaySpeed) -> Unit
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
private fun ReportTab(
    state: DutyHandoverUiState,
    onSelectOriginalRecord: (HandoverRecord) -> Unit
) {
    ReportSectionCard(title = state.overviewTitle) {
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
    RecordCard(
        title = "발견·기록 시간순",
        records = state.records,
        emptyText = state.emptyRecordLabel,
        selectedRecordKey = state.selectedOriginalRecordKey,
        onSelectRecord = onSelectOriginalRecord
    )
    RecordCard(
        title = "인수인계 메모",
        records = state.handoverMemoRecords,
        emptyText = "인수인계 메모 없음",
        selectedRecordKey = state.selectedOriginalRecordKey,
        onSelectRecord = onSelectOriginalRecord
    )
    RecordCard(
        title = "마커 사진",
        records = state.markerPhotoRecords,
        emptyText = "연결된 마커 사진 없음",
        selectedRecordKey = state.selectedOriginalRecordKey,
        onSelectRecord = onSelectOriginalRecord
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
    emptyText: String = "이전 기록 없음",
    selectedRecordKey: String? = null,
    onSelectRecord: (HandoverRecord) -> Unit = {}
) {
    ReportSectionCard(title = title) {
        if (records.isEmpty()) {
            EmptyReportText(emptyText)
        } else {
            records.forEach { record ->
                HandoverRecordRow(
                    record = record,
                    selected = selectedRecordKey == record.sourceKey,
                    onSelectRecord = onSelectRecord
                )
            }
        }
    }
}

@Composable
private fun HandoverRecordRow(
    record: HandoverRecord,
    selected: Boolean,
    onSelectRecord: (HandoverRecord) -> Unit
) {
    PoliRow(
        title = record.title,
        subtitle = record.subtitle,
        modifier =
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) PoliPrimaryFillSoft else Color.Transparent)
            .clickable { onSelectRecord(record) }
            .padding(horizontal = PoliDimens.Space3, vertical = PoliDimens.Space2)
    ) {
        PoliChip(
            text = if (selected) "선택됨" else record.actionLabel,
            variant = if (selected) PoliChipVariant.Outbox else PoliChipVariant.Neutral
        )
    }
}

@Composable
private fun SummaryCard(state: DutyHandoverUiState) {
    PoliCard(strong = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
                Text(text = state.summaryTitle, style = MaterialTheme.typography.titleMedium)
                Text(text = state.generatedAtLabel, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
            }
            PoliChip(text = state.summaryStatusLabel, variant = state.summaryStatus.variant)
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

private const val DUTY_SHIFT_OVERVIEW_TITLE = "근무 개요"
private const val DUTY_SHIFT_SUMMARY_TITLE = "이전 근무 요약"

private val HandoverRecordScope.overviewTitle: String
    get() =
        when (this) {
            HandoverRecordScope.DutyShift -> DUTY_SHIFT_OVERVIEW_TITLE
        }

private val HandoverRecordScope.summaryTitle: String
    get() =
        when (this) {
            HandoverRecordScope.DutyShift -> DUTY_SHIFT_SUMMARY_TITLE
        }

private val HandoverRecordScope.primaryBadge: String
    get() =
        when (this) {
            HandoverRecordScope.DutyShift -> "근무 기준"
        }

private val HandoverRecordScope.actorBadge: String
    get() =
        when (this) {
            HandoverRecordScope.DutyShift -> "단일 근무자"
        }

private val HandoverRecordScope.emptyRecordLabel: String
    get() =
        when (this) {
            HandoverRecordScope.DutyShift -> "이전 기록 없음"
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

private fun SearchHistorySummaryStatus.emptyCopy(): String =
    when (this) {
        SearchHistorySummaryStatus.Ready -> ""
        SearchHistorySummaryStatus.Generating ->
            "이전 근무 기록을 자동 처리 중입니다. 원본 기록은 즉시 확인할 수 있습니다."
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
