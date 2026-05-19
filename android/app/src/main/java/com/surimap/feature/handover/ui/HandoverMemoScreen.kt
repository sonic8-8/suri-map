package com.surimap.feature.handover.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.surimap.ui.components.PoliAppBar
import com.surimap.ui.components.PoliBanner
import com.surimap.ui.components.PoliBannerVariant
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliRow
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.SuriMapTheme

enum class HandoverMemoTarget(val label: String) {
    OP("OP"),
    Path("경로"),
    Area("구역"),
    DutyShift("근무"),
    Marker("마커")
}

data class HandoverMemoUiState(
    val title: String,
    val subtitle: String,
    val selectedTarget: HandoverMemoTarget,
    val selectedTargetTitle: String,
    val selectedTargetSubtitle: String,
    val memoText: String,
    val offline: Boolean,
    val saving: Boolean = false,
    val incidentClosed: Boolean = false,
    val maxLength: Int = 500
) {
    val targetOptions: List<HandoverMemoTarget> = HandoverMemoTarget.entries.toList()
    val countLabel: String = "${memoText.length.coerceAtMost(maxLength)} / $maxLength"
    val canSave: Boolean = memoText.isNotBlank() && !saving && !incidentClosed
    val outboxStatusLabel: String = if (offline) "미전송" else "저장 준비"

    fun visibleText(): List<String> =
        buildList {
            add(title)
            add(subtitle)
            targetOptions.forEach { add(it.label) }
            add(selectedTargetTitle)
            add(selectedTargetSubtitle)
            add(memoText)
            add(countLabel)
            add(outboxStatusLabel)
            add("메모 저장")
            if (incidentClosed) {
                add("사건 종료 상태에서는 저장할 수 없습니다.")
            }
        }

    companion object {
        fun default(
            offline: Boolean = true,
            selectedTarget: HandoverMemoTarget = HandoverMemoTarget.OP
        ): HandoverMemoUiState =
            HandoverMemoUiState(
                title = "인수인계 메모",
                subtitle = "사건 #1234 · OP 3차 · DutyShift 14:00",
                selectedTarget = selectedTarget,
                selectedTargetTitle = "현재 OP 3차",
                selectedTargetSubtitle = "재수색 · 활성 운영 기간",
                memoText = "북측 진입로 주민 진술 대기. 배수로 아래쪽 원본 마커 확인 필요.",
                offline = offline
            )
    }
}

@Composable
fun HandoverMemoScreen(
    state: HandoverMemoUiState,
    onBack: () -> Unit,
    onSelectTarget: (HandoverMemoTarget) -> Unit,
    onMemoChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
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
                PoliRow(title = "근무", subtitle = "14:00 인계 · 현재 OP 3차") {
                    PoliChip(text = "현재")
                }
            }

            PoliCard {
                Text(text = "대상", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
                    state.targetOptions.forEach { target ->
                        PoliChip(
                            text = target.label,
                            modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = PoliDimens.TouchMin)
                                .clickable { onSelectTarget(target) },
                            variant = if (target == state.selectedTarget) PoliChipVariant.Outbox else PoliChipVariant.Neutral
                        )
                    }
                }
                PoliRow(title = state.selectedTargetTitle, subtitle = state.selectedTargetSubtitle) {
                    PoliChip(text = "선택", variant = PoliChipVariant.Good)
                }
            }

            PoliCard {
                Text(text = "본문", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.memoText,
                    onValueChange = { text -> onMemoChange(text.take(state.maxLength)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    enabled = !state.incidentClosed,
                    label = { Text(text = "메모") }
                )
                Text(text = state.countLabel, style = MaterialTheme.typography.labelMedium, color = PoliFgMuted)
            }

            if (state.offline) {
                PoliCard {
                    PoliRow(title = "오프라인 저장", subtitle = "저장 후 연결 시 자동 전송됩니다.") {
                        PoliChip(text = state.outboxStatusLabel, variant = PoliChipVariant.Warn)
                    }
                }
            }

            if (state.incidentClosed) {
                PoliBanner(text = "사건 종료 상태에서는 저장할 수 없습니다.", variant = PoliBannerVariant.Bad)
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
            PoliButton(text = "메모 저장", onClick = onSave, modifier = Modifier.weight(1.35f), enabled = state.canSave)
        }
    }
}

fun sampleHandoverMemoState(): HandoverMemoUiState = HandoverMemoUiState.default()

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun HandoverMemoPreview() {
    SuriMapTheme {
        HandoverMemoScreen(
            state = sampleHandoverMemoState(),
            onBack = {},
            onSelectTarget = {},
            onMemoChange = {},
            onSave = {}
        )
    }
}
