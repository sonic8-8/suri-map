package com.surimap.feature.handover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import java.time.Instant
import com.surimap.ui.components.PoliAppBar
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliField
import com.surimap.ui.components.PoliRow
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgSecondary
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
    val canEndDutyShift: Boolean = false,
    val endingDutyShift: Boolean = false,
    val canRequestSummaryGeneration: Boolean = false
) {
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
            add("AI 인수인계 요약")
            add(summaryStatus.label)
            add(generatedAtLabel)
            add(summaryText)
            add(sourceReadiness.label)
            summaryActionLabel?.let(::add)
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
                generatedAtLabel = "summary_unavailable · 원본 기록 유지",
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
                )
        ): DutyHandoverUiState =
            DutyHandoverUiState(
                title = "이전 근무 확인",
                subtitle = "사건 #1234 · OP 3차 · 교대 14:00",
                summaryStatus = summaryStatus,
                generatedAtLabel = generatedAtLabel,
                summary = summary,
                sourceReadiness = sourceReadiness,
                metrics = metrics,
                records = records,
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

data class HandoverMetric(val value: String, val label: String)
data class HandoverRecord(val title: String, val subtitle: String, val actionLabel: String)

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
    onEndDutyShift: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        PoliAppBar(title = state.title, subtitle = state.subtitle, showBack = true, onBack = onBack)
        Column(
            modifier =
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PoliDimens.SectionPadding),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
        ) {
            SummaryCard(state)
            PoliCard {
                Text(text = "원본 기록", style = MaterialTheme.typography.titleMedium)
                if (state.records.isEmpty()) {
                    Text(
                        text = "이전 기록 없음",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PoliFgMuted
                    )
                } else {
                    state.records.forEach { record ->
                        PoliRow(title = record.title, subtitle = record.subtitle) {
                            PoliChip(text = record.actionLabel)
                        }
                    }
                }
            }
            PoliCard {
                Text(text = "상태 기준", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "요약은 서버 내부 job 결과만 읽습니다. Android는 원본 경로·마커·메모를 계속 보여주며 생성·재시도 요청을 만들지 않습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PoliFgMuted
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
private fun SummaryCard(state: DutyHandoverUiState) {
    PoliCard(strong = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
                Text(text = "AI 인수인계 요약", style = MaterialTheme.typography.titleMedium)
                Text(text = state.generatedAtLabel, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
            }
            PoliChip(text = state.summaryStatus.label, variant = state.summaryStatus.variant)
        }
        Text(text = state.summaryText, style = MaterialTheme.typography.bodyLarge, color = PoliFgSecondary)
        PoliChip(text = state.sourceReadiness.label)
        state.summaryActionLabel?.let { actionLabel ->
            PoliChip(text = actionLabel, variant = PoliChipVariant.Outbox)
        }
        if (state.metrics.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
                state.metrics.forEach { metric ->
                    PoliField(label = metric.label, value = metric.value, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private val SearchHistorySummaryStatus.label: String
    get() =
        when (this) {
            SearchHistorySummaryStatus.Ready -> "준비됨"
            SearchHistorySummaryStatus.Generating -> "자동 처리 중"
            SearchHistorySummaryStatus.NeedsSummary -> "요약 생성 필요"
            SearchHistorySummaryStatus.Unavailable -> "summary_unavailable"
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
            SearchHistorySummaryStatus.Unavailable -> "summary_unavailable 상태입니다. 서버 요약이 실패해도 원본 경로·마커·메모는 계속 확인할 수 있습니다."
            SearchHistorySummaryStatus.Empty -> "이전 기록 없음"
        }

private val SummarySourceReadiness.label: String
    get() =
        when (this) {
            SummarySourceReadiness.PendingSync -> "sourceReadiness=PENDING_SYNC"
            SummarySourceReadiness.Ready -> "sourceReadiness=READY"
            SummarySourceReadiness.Stale -> "sourceReadiness=STALE"
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
