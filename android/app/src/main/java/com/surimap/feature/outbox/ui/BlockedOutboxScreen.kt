package com.surimap.feature.outbox.ui

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
import com.surimap.ui.components.PoliRow
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliWarning
import com.surimap.ui.theme.SuriMapTheme

enum class BlockedOutboxReason(
    val errorCode: String,
    val title: String,
    val actionGuide: String
) {
    IncidentClosed(
        errorCode = "incident_closed",
        title = "종료된 사건 전송 차단",
        actionGuide = "사건 종료 시점과 폴리폰 ID를 IT 부서에 전달하세요."
    ),
    PolicePhoneNotAssigned(
        errorCode = "police_phone_not_assigned",
        title = "폴리폰 배정 확인 필요",
        actionGuide = "단말 배정 정정이 필요합니다. 사건 번호와 폴리폰 ID를 함께 전달하세요."
    ),
    RetryExhausted(
        errorCode = "retry_exhausted",
        title = "자동 재시도 한도 초과",
        actionGuide = "자동 복구 한도를 넘었습니다. 네트워크 상태와 시각을 IT 부서에 전달하세요."
    ),
    PayloadValidationFailure(
        errorCode = "payload_validation_failure",
        title = "저장 데이터 검증 실패",
        actionGuide = "수정 가능한 화면이 아니므로 원본 기록 보존 상태로 IT 부서에 문의하세요."
    )
}

data class BlockedOutboxItemUiState(
    val title: String,
    val timestampLabel: String,
    val reason: BlockedOutboxReason
) {
    val subtitle: String = "${reason.title} (${reason.errorCode})"
}

data class PendingOutboxSummaryUiState(
    val count: Int,
    val detailLabel: String,
    val oldestAgeLabel: String
) {
    val title: String = "자동 처리 대기 ${count}건"
    val message: String = "$detailLabel · 가장 오래된 항목 $oldestAgeLabel · 사용자 조치 불요"
}

data class BlockedOutboxUiState(
    val title: String,
    val subtitle: String,
    val blockedItems: List<BlockedOutboxItemUiState>,
    val pendingSummary: PendingOutboxSummaryUiState?
) {
    val blockedCount: Int = blockedItems.size
    val canEnterDiagnostic: Boolean = blockedCount > 0
    val headerLabel: String = if (canEnterDiagnostic) "처리 불가 ${blockedCount}건" else "처리 불가 없음"
    val supportActionLabel: String = "IT 부서 문의 안내 보기"

    fun visibleText(): List<String> =
        buildList {
            add(title)
            add(subtitle)
            if (canEnterDiagnostic) {
                add(headerLabel)
                add(supportActionLabel)
            }
            blockedItems.forEach { item ->
                add(item.title)
                add(item.timestampLabel)
                add(item.subtitle)
                add(item.reason.actionGuide)
            }
            pendingSummary?.let { pending ->
                add(pending.title)
                add(pending.message)
            }
        }

    companion object {
        fun blockedFixture(): BlockedOutboxUiState =
            BlockedOutboxUiState(
                title = "미전송 진단",
                subtitle = "POL-1A-0023 · 사건 #1234",
                blockedItems =
                listOf(
                    BlockedOutboxItemUiState(
                        title = "경로 batch",
                        timestampLabel = "14:18 ~ 14:22",
                        reason = BlockedOutboxReason.IncidentClosed
                    ),
                    BlockedOutboxItemUiState(
                        title = "패키지 상태 보고",
                        timestampLabel = "14:08",
                        reason = BlockedOutboxReason.PolicePhoneNotAssigned
                    ),
                    BlockedOutboxItemUiState(
                        title = "사진 첨부 finalize",
                        timestampLabel = "14:02",
                        reason = BlockedOutboxReason.RetryExhausted
                    ),
                    BlockedOutboxItemUiState(
                        title = "마커 수정",
                        timestampLabel = "13:58",
                        reason = BlockedOutboxReason.PayloadValidationFailure
                    )
                ),
                pendingSummary =
                PendingOutboxSummaryUiState(
                    count = 10,
                    detailLabel = "경로 6 · 마커 2 · 사진 2",
                    oldestAgeLabel = "8분 전"
                )
            )

        fun normalPendingFixture(): BlockedOutboxUiState =
            BlockedOutboxUiState(
                title = "미전송 진단",
                subtitle = "POL-1A-0023 · 사건 #1234",
                blockedItems = emptyList(),
                pendingSummary =
                PendingOutboxSummaryUiState(
                    count = 12,
                    detailLabel = "경로 8 · 마커 2 · 사진 2",
                    oldestAgeLabel = "8분 전"
                )
            )
    }
}

@Composable
fun BlockedOutboxScreen(
    state: BlockedOutboxUiState,
    onBack: () -> Unit,
    onOpenSupportGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        PoliAppBar(
            title = state.title,
            subtitle = state.subtitle,
            showBack = true,
            onBack = onBack,
            trailing = {
                PoliChip(
                    text = state.headerLabel,
                    variant = if (state.canEnterDiagnostic) PoliChipVariant.Bad else PoliChipVariant.Neutral
                )
            }
        )

        Column(
            modifier =
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PoliDimens.SectionPadding),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
        ) {
            if (state.canEnterDiagnostic) {
                BlockedGroup(state = state)
            } else {
                PoliCard {
                    Text(text = "처리 불가 항목이 없습니다", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "정상 오프라인 대기 큐는 지도 sync chip에서만 확인합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PoliFgMuted
                    )
                }
            }

            state.pendingSummary?.let { summary ->
                PoliCard {
                    PoliRow(title = summary.title, subtitle = summary.message) {
                        PoliChip(text = "자동", variant = PoliChipVariant.Warn)
                    }
                }
            }
        }

        if (state.canEnterDiagnostic) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(PoliDimens.SectionPadding),
                horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
            ) {
                PoliButton(
                    text = state.supportActionLabel,
                    onClick = onOpenSupportGuide,
                    modifier = Modifier.weight(1f),
                    variant = PoliButtonVariant.Primary
                )
            }
        }
    }
}

@Composable
private fun BlockedGroup(state: BlockedOutboxUiState) {
    PoliCard(strong = true) {
        Text(text = "${state.headerLabel} - IT 부서 문의", style = MaterialTheme.typography.titleMedium, color = PoliWarning)
        Text(
            text = "자동 처리로 해결되지 않는 항목입니다. 사유별 안내를 확인하세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = PoliFgMuted
        )
        state.blockedItems.forEach { item ->
            PoliRow(title = item.title, subtitle = "${item.timestampLabel} · ${item.subtitle}") {
                PoliChip(text = "확인 필요", variant = PoliChipVariant.Bad)
            }
            Text(text = item.reason.actionGuide, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
        }
    }
}

fun sampleBlockedOutboxUiState(): BlockedOutboxUiState = BlockedOutboxUiState.blockedFixture()

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun BlockedOutboxPreview() {
    SuriMapTheme {
        BlockedOutboxScreen(
            state = sampleBlockedOutboxUiState(),
            onBack = {},
            onOpenSupportGuide = {}
        )
    }
}
