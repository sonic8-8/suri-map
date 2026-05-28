package com.surimap.feature.outbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.surimap.ui.components.PoliAppBar
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonSize
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliPullToRefresh
import com.surimap.ui.components.PoliRow
import com.surimap.ui.components.PoliSkeletonCard
import com.surimap.ui.components.PoliSkeletonLine
import com.surimap.ui.components.rememberPoliShimmerBrush
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
        actionGuide = "사건 종료 시점과 단말 식별 정보를 IT 부서에 전달하세요."
    ),
    PolicePhoneNotAssigned(
        errorCode = "police_phone_not_assigned",
        title = "단말 배정 확인 필요",
        actionGuide = "단말 배정 정정이 필요합니다. 사건 번호와 단말 식별 정보를 함께 전달하세요."
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
    ),
    ClockResyncRequired(
        errorCode = "clock_resync_required",
        title = "단말 시각 재동기화 필요",
        actionGuide = "서버 시각 동기화 후 다시 전송할 수 있습니다."
    ),
    RetryableNetwork(
        errorCode = "retryable_network",
        title = "네트워크 복구 후 재시도 가능",
        actionGuide = "내부망 연결 상태를 확인한 뒤 다시 전송하세요."
    ),
    PolicePhoneAccessRequired(
        errorCode = "police_phone_access_required",
        title = "단말 접근 복구 필요",
        actionGuide = "로그인/단말 배정 상태를 복구한 뒤 다시 전송하세요."
    )
}

data class BlockedOutboxItemUiState(
    val operationId: String,
    val title: String,
    val timestampLabel: String,
    val reason: BlockedOutboxReason,
    val retryable: Boolean = false
) {
    val subtitle: String = reason.title
    val retryActionLabel: String? = if (retryable) "지금 재시도" else null
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
    val supportActionLabel: String = "문의 안내 보기"

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
                item.retryActionLabel?.let(::add)
            }
            pendingSummary?.let { pending ->
                add(pending.title)
                add(pending.message)
            }
        }

    companion object {
        fun blockedFixture(): BlockedOutboxUiState =
            BlockedOutboxUiState(
                title = "미전송 기록",
                subtitle = "현재 사건 · 단말 기록",
                blockedItems =
                listOf(
                    BlockedOutboxItemUiState(
                        operationId = "op-path-001",
                        title = "경로 기록",
                        timestampLabel = "14:18 ~ 14:22",
                        reason = BlockedOutboxReason.IncidentClosed
                    ),
                    BlockedOutboxItemUiState(
                        operationId = "op-package-001",
                        title = "지도 데이터 상태",
                        timestampLabel = "14:08",
                        reason = BlockedOutboxReason.PolicePhoneNotAssigned
                    ),
                    BlockedOutboxItemUiState(
                        operationId = "op-photo-001",
                        title = "사진 첨부",
                        timestampLabel = "14:02",
                        reason = BlockedOutboxReason.RetryExhausted,
                        retryable = true
                    ),
                    BlockedOutboxItemUiState(
                        operationId = "op-marker-001",
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
                title = "미전송 기록",
                subtitle = "현재 사건 · 단말 기록",
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
    onRetry: (BlockedOutboxItemUiState) -> Unit = {},
    onRefresh: () -> Unit = {},
    refreshing: Boolean = false,
    loading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shimmerBrush =
        if (loading) {
            rememberPoliShimmerBrush(label = "blocked-outbox-skeleton")
        } else {
            null
        }
    PoliPullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PoliAppBar(
                title = state.title,
                subtitle = state.subtitle,
                showBack = false,
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
                if (loading && shimmerBrush != null) {
                    BlockedOutboxLoadingContent(shimmerBrush = shimmerBrush)
                } else if (state.canEnterDiagnostic) {
                    BlockedGroup(state = state, onRetry = onRetry)
                } else {
                    PoliCard {
                        Text(text = "처리 불가 항목이 없습니다", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "정상 자동 전송 대기는 연결 복구 시 자동 처리됩니다.",
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

            if (loading && shimmerBrush != null) {
                BlockedOutboxActionSkeleton(shimmerBrush = shimmerBrush)
            } else if (state.canEnterDiagnostic) {
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
}

@Composable
private fun BlockedOutboxLoadingContent(shimmerBrush: Brush) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "미전송 기록 불러오는 중" },
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
    ) {
        PoliSkeletonCard(title = "처리 불가 항목", lineCount = 3, shimmerBrush = shimmerBrush, strong = true)
        PoliSkeletonCard(title = "자동 처리 대기", lineCount = 2, shimmerBrush = shimmerBrush)
    }
}

@Composable
private fun BlockedOutboxActionSkeleton(shimmerBrush: Brush) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(PoliDimens.SectionPadding),
        horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
    ) {
        PoliSkeletonLine(shimmerBrush = shimmerBrush, modifier = Modifier.weight(1f), minHeight = PoliDimens.CtaHeight)
    }
}

@Composable
private fun BlockedGroup(state: BlockedOutboxUiState, onRetry: (BlockedOutboxItemUiState) -> Unit) {
    PoliCard(strong = true) {
        Text(text = "${state.headerLabel} - IT 부서 문의", style = MaterialTheme.typography.titleMedium, color = PoliWarning)
        Text(
            text = "자동 처리로 해결되지 않는 항목입니다. 사유별 안내를 확인하세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = PoliFgMuted
        )
        state.blockedItems.forEach { item ->
            PoliRow(title = item.title, subtitle = "${item.timestampLabel} · ${item.subtitle}") {
                if (item.retryable) {
                    PoliButton(
                        text = item.retryActionLabel ?: "재시도",
                        onClick = { onRetry(item) },
                        size = PoliButtonSize.Small,
                        variant = PoliButtonVariant.Secondary
                    )
                } else {
                    PoliChip(text = "확인 필요", variant = PoliChipVariant.Bad)
                }
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
