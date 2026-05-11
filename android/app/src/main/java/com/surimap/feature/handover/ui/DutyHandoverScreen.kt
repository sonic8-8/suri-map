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
    val summaryStatus: SummaryStatus,
    val generatedAtLabel: String,
    val summary: String,
    val metrics: List<HandoverMetric>,
    val records: List<HandoverRecord>
)

enum class SummaryStatus {
    Ready,
    Processing,
    SourceOnly,
    Empty
}

data class HandoverMetric(val value: String, val label: String)
data class HandoverRecord(val title: String, val subtitle: String, val actionLabel: String)

@Composable
fun DutyHandoverScreen(
    state: DutyHandoverUiState,
    onBack: () -> Unit,
    onWriteMemo: () -> Unit,
    onOpenSearch: () -> Unit,
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
                state.records.forEach { record ->
                    PoliRow(title = record.title, subtitle = record.subtitle) {
                        PoliChip(text = record.actionLabel)
                    }
                }
            }
            PoliCard {
                Text(text = "상태 기준", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "요약은 서버 자동 처리 결과만 표시합니다. 준비 중이거나 실패해도 Android에서 생성·재시도 버튼을 제공하지 않습니다.",
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
    }
}

@Composable
private fun SummaryCard(state: DutyHandoverUiState) {
    PoliCard(strong = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
                Text(text = "시스템 자동 요약", style = MaterialTheme.typography.titleMedium)
                Text(text = state.generatedAtLabel, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
            }
            PoliChip(text = state.summaryStatus.label, variant = state.summaryStatus.variant)
        }
        Text(text = state.summary, style = MaterialTheme.typography.bodyLarge, color = PoliFgSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
            state.metrics.forEach { metric ->
                PoliField(label = metric.label, value = metric.value, modifier = Modifier.weight(1f))
            }
        }
    }
}

private val SummaryStatus.label: String
    get() =
        when (this) {
            SummaryStatus.Ready -> "준비됨"
            SummaryStatus.Processing -> "자동 처리 중"
            SummaryStatus.SourceOnly -> "원본 확인"
            SummaryStatus.Empty -> "기록 없음"
        }

private val SummaryStatus.variant: PoliChipVariant
    get() =
        when (this) {
            SummaryStatus.Ready -> PoliChipVariant.Good
            SummaryStatus.Processing -> PoliChipVariant.Warn
            SummaryStatus.SourceOnly -> PoliChipVariant.Bad
            SummaryStatus.Empty -> PoliChipVariant.Neutral
        }

fun sampleDutyHandoverState() =
    DutyHandoverUiState(
        title = "이전 근무 확인",
        subtitle = "사건 #1234 · OP 3차 · 교대 14:00",
        summaryStatus = SummaryStatus.Ready,
        generatedAtLabel = "박 순경 근무 기록 기준 · 12:00-14:00 · 서버 자동 생성",
        summary = "동쪽 능선과 북측 진입로 주변을 도보로 수색했습니다. 배수로 인근 단서 마커 1건과 운영 메모 2건이 남아 있습니다.",
        metrics =
        listOf(
            HandoverMetric("1.8km", "도보 경로"),
            HandoverMetric("4건", "마커"),
            HandoverMetric("2건", "메모")
        ),
        records =
        listOf(
            HandoverRecord("도보 경로 · 동쪽 능선", "12:07-13:18 · GPS 일부 약함", "보기"),
            HandoverRecord("단서 마커 · 배수로 입구", "사진 2장 · 작성 13:36", "열기"),
            HandoverRecord("운영 메모 · 북측 진입로", "주민 진술 대기, 배수로 아래 확인 필요", "열기")
        )
    )

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
