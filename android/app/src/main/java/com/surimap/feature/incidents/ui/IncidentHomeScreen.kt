package com.surimap.feature.incidents.ui

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
import androidx.compose.ui.tooling.preview.Preview
import com.surimap.ui.components.PoliAppBar
import com.surimap.ui.components.PoliBanner
import com.surimap.ui.components.PoliBannerVariant
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliPullToRefresh
import com.surimap.ui.components.PoliRow
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.SuriMapTheme

enum class IncidentHomeMapDataStatus {
    Ready,
    Preparing,
    NeedsAttention,
    Missing
}

data class IncidentHomeUiState(
    val incidentTitle: String,
    val missingPersonSummary: String,
    val opLabel: String,
    val assignmentLabel: String,
    val mapDataStatus: IncidentHomeMapDataStatus,
    val mapDataDetail: String,
    val syncLabel: String,
    val lastUpdatedLabel: String,
    val pendingOutboxCount: Int,
    val blockedOutboxCount: Int,
    val refreshing: Boolean = false
) {
    val mapDataLabel: String
        get() =
            when (mapDataStatus) {
                IncidentHomeMapDataStatus.Ready -> "지도 데이터 준비 완료"
                IncidentHomeMapDataStatus.Preparing -> "지도 데이터 준비 중"
                IncidentHomeMapDataStatus.NeedsAttention -> "지도 데이터 확인 필요"
                IncidentHomeMapDataStatus.Missing -> "지도 데이터 준비 필요"
            }

    val mapDataVariant: PoliChipVariant
        get() =
            when (mapDataStatus) {
                IncidentHomeMapDataStatus.Ready -> PoliChipVariant.Good
                IncidentHomeMapDataStatus.Preparing,
                IncidentHomeMapDataStatus.NeedsAttention,
                IncidentHomeMapDataStatus.Missing -> PoliChipVariant.Warn
            }

    val outboxSummaryLabel: String
        get() =
            when {
                blockedOutboxCount > 0 -> "처리 불가 ${blockedOutboxCount}건"
                pendingOutboxCount > 0 -> "자동 전송 대기 ${pendingOutboxCount}건"
                else -> "미전송 없음"
            }

    val outboxVariant: PoliChipVariant
        get() =
            when {
                blockedOutboxCount > 0 -> PoliChipVariant.Bad
                pendingOutboxCount > 0 -> PoliChipVariant.Warn
                else -> PoliChipVariant.Good
            }

    fun visibleText(): List<String> =
        buildList {
            add("사건 정보")
            add(incidentTitle)
            add(missingPersonSummary)
            add(opLabel)
            add(assignmentLabel)
            add(mapDataLabel)
            add(mapDataDetail)
            add(syncLabel)
            add(lastUpdatedLabel)
            add(outboxSummaryLabel)
            add("현장 기록 열기")
            add("지도 데이터 확인")
            if (blockedOutboxCount > 0) {
                add("미전송 진단 보기")
            }
        }

    companion object {
        fun sample(): IncidentHomeUiState =
            IncidentHomeUiState(
                incidentTitle = "광주 광산구 황룡강 생태길 실종 신고",
                missingPersonSummary = "70대 남성 · 회색 점퍼 · 보행 느림",
                opLabel = "2차 수색",
                assignmentLabel = "팀 담당 구역",
                mapDataStatus = IncidentHomeMapDataStatus.Ready,
                mapDataDetail = "오프라인 지도와 사건 기본 정보가 준비되어 있습니다.",
                syncLabel = "최신 상태",
                lastUpdatedLabel = "방금 갱신",
                pendingOutboxCount = 3,
                blockedOutboxCount = 2
            )
    }
}

@Composable
fun IncidentHomeScreen(
    state: IncidentHomeUiState,
    onOpenSearchMap: () -> Unit,
    onOpenMapData: () -> Unit,
    onOpenBlockedOutbox: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    PoliPullToRefresh(
        refreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PoliAppBar(
                title = "사건 정보",
                subtitle = state.lastUpdatedLabel,
                trailing = {
                    PoliChip(text = state.syncLabel, variant = PoliChipVariant.Neutral)
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
                IncidentSummaryCard(state = state)
                FieldReadinessCard(
                    state = state,
                    onOpenMapData = onOpenMapData,
                    onOpenBlockedOutbox = onOpenBlockedOutbox
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(PoliDimens.SectionPadding),
                horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
            ) {
                PoliButton(
                    text = "지도 데이터 확인",
                    onClick = onOpenMapData,
                    modifier = Modifier.weight(1f),
                    variant = PoliButtonVariant.Secondary
                )
                PoliButton(
                    text = "현장 기록 열기",
                    onClick = onOpenSearchMap,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun IncidentSummaryCard(state: IncidentHomeUiState) {
    PoliCard(strong = true) {
        Text(text = state.incidentTitle, style = MaterialTheme.typography.titleMedium)
        Text(text = state.missingPersonSummary, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
        PoliRow(title = "현재 수색 차수", subtitle = state.opLabel)
        PoliRow(title = "담당 구역", subtitle = state.assignmentLabel.ifBlank { "담당 구역 확인 중" })
    }
}

@Composable
private fun FieldReadinessCard(
    state: IncidentHomeUiState,
    onOpenMapData: () -> Unit,
    onOpenBlockedOutbox: () -> Unit
) {
    PoliCard {
        Text(text = "현장 기록 준비", style = MaterialTheme.typography.titleMedium)
        PoliRow(title = state.mapDataLabel, subtitle = state.mapDataDetail) {
            PoliChip(text = state.mapDataLabel.shortStatusLabel(), variant = state.mapDataVariant)
        }
        PoliRow(title = "동기화 상태", subtitle = state.lastUpdatedLabel) {
            PoliChip(text = state.syncLabel, variant = PoliChipVariant.Neutral)
        }
        PoliRow(title = "미전송 기록", subtitle = "정상 대기는 자동 전송됩니다.") {
            PoliChip(text = state.outboxSummaryLabel, variant = state.outboxVariant)
        }
        if (state.blockedOutboxCount > 0) {
            PoliBanner(
                text = "자동 전송으로 해결되지 않는 항목이 있습니다. 원인과 재시도 가능 여부를 확인하세요.",
                variant = PoliBannerVariant.Bad
            )
            PoliButton(
                text = "미전송 진단 보기",
                onClick = onOpenBlockedOutbox,
                modifier = Modifier.fillMaxWidth(),
                variant = PoliButtonVariant.Secondary
            )
        } else {
            PoliButton(
                text = "지도 데이터 확인",
                onClick = onOpenMapData,
                modifier = Modifier.fillMaxWidth(),
                variant = PoliButtonVariant.Secondary
            )
        }
    }
}

private fun String.shortStatusLabel(): String =
    when {
        contains("완료") -> "준비 완료"
        contains("중") -> "준비 중"
        contains("확인") -> "확인 필요"
        else -> "준비 필요"
    }

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun IncidentHomeScreenPreview() {
    SuriMapTheme {
        IncidentHomeScreen(
            state = IncidentHomeUiState.sample(),
            onOpenSearchMap = {},
            onOpenMapData = {},
            onOpenBlockedOutbox = {},
            onRefresh = {}
        )
    }
}
