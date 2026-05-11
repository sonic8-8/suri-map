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
import com.surimap.ui.theme.SuriMapTheme

data class HandoverMemoUiState(
    val title: String,
    val subtitle: String,
    val selectedTarget: String,
    val memoText: String,
    val countLabel: String,
    val offline: Boolean
)

@Composable
fun HandoverMemoScreen(
    state: HandoverMemoUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
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
            PoliCard {
                Text(text = "근무 구간", style = MaterialTheme.typography.titleMedium)
                PoliRow(title = "DutyShift", subtitle = "14:00 인계 · 현재 OP 3차") {
                    PoliChip(text = "현재")
                }
            }

            PoliCard {
                Text(text = "대상", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
                    listOf("OP", "근무", "경로", "구역").forEach { label ->
                        PoliChip(
                            text = label,
                            modifier = Modifier.weight(1f),
                            variant = if (label == "OP") PoliChipVariant.Outbox else PoliChipVariant.Neutral
                        )
                    }
                }
                PoliField(label = "선택", value = state.selectedTarget)
            }

            PoliCard {
                Text(text = "본문", style = MaterialTheme.typography.titleMedium)
                PoliField(label = "메모", value = state.memoText)
                Text(text = state.countLabel, style = MaterialTheme.typography.labelMedium, color = PoliFgMuted)
            }

            if (state.offline) {
                PoliCard {
                    PoliRow(title = "오프라인 저장", subtitle = "저장 후 연결 시 자동 전송됩니다.") {
                        PoliChip(text = "미전송", variant = PoliChipVariant.Warn)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(PoliDimens.SectionPadding),
            horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
        ) {
            PoliButton(
                text = "취소",
                onClick = onBack,
                modifier = Modifier.weight(1f),
                variant = PoliButtonVariant.Secondary
            )
            PoliButton(text = "메모 저장", onClick = onSave, modifier = Modifier.weight(1.35f))
        }
    }
}

fun sampleHandoverMemoState() =
    HandoverMemoUiState(
        title = "인수인계 메모",
        subtitle = "사건 #1234 · OP 3차",
        selectedTarget = "현재 OP 3차",
        memoText = "북측 진입로 주민 진술 대기. 배수로 아래쪽 원본 마커 확인 필요.",
        countLabel = "38 / 500",
        offline = true
    )

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun HandoverMemoPreview() {
    SuriMapTheme {
        HandoverMemoScreen(state = sampleHandoverMemoState(), onBack = {}, onSave = {})
    }
}
