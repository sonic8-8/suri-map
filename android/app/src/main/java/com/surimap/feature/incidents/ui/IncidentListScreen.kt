package com.surimap.feature.incidents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.surimap.ui.components.PoliAppBar
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliDialog
import com.surimap.ui.components.PoliRow
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.SuriMapTheme

data class IncidentListUiState(
    val policePhoneLabel: String,
    val syncLabel: String,
    val incidents: List<AssignedIncidentUiModel>,
    val showClosedDialog: Boolean = false
)

data class AssignedIncidentUiModel(
    val title: String,
    val summary: String,
    val packageStatus: String,
    val assignmentStatus: String
)

@Composable
fun IncidentListScreen(
    state: IncidentListUiState,
    onOpenIncident: () -> Unit,
    onRefresh: () -> Unit,
    onDismissClosedDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PoliAppBar(
                title = "사건 선택",
                subtitle = state.policePhoneLabel,
                trailing = {
                    PoliChip(text = state.syncLabel, variant = PoliChipVariant.Good)
                }
            )

            if (state.incidents.isEmpty()) {
                EmptyIncidentList(onRefresh = onRefresh, modifier = Modifier.weight(1f))
            } else {
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = PoliDimens.SectionPadding),
                    verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
                ) {
                    state.incidents.forEach { incident ->
                        IncidentCard(incident)
                    }
                    Text(
                        text = "활성 배정은 보통 1건입니다. 동시에 2건이 보이면 배정 변경 중인 짧은 전환 상태입니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PoliFgMuted
                    )
                }
                Row(modifier = Modifier.padding(PoliDimens.SectionPadding)) {
                    PoliButton(text = "선택한 사건 열기", onClick = onOpenIncident, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (state.showClosedDialog) {
            PoliDialog(
                title = "사건이 종료되었습니다",
                body = "종료된 사건에는 더 이상 입력할 수 없습니다. 작성 중인 내용은 저장되지 않으며 로컬 정리는 백그라운드에서 진행됩니다.",
                primaryText = "확인",
                onPrimary = onDismissClosedDialog,
                modifier = Modifier.fillMaxSize(),
                danger = true
            )
        }
    }
}

@Composable
private fun IncidentCard(incident: AssignedIncidentUiModel) {
    PoliCard(strong = true) {
        Text(text = incident.title, style = MaterialTheme.typography.titleMedium)
        Text(text = incident.summary, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
        PoliRow(title = "패키지 상태", subtitle = incident.packageStatus) {
            PoliChip(text = "준비됨", variant = PoliChipVariant.Good)
        }
        PoliRow(title = "현재 폴리폰 배정", subtitle = incident.assignmentStatus) {
            PoliChip(text = "활성", variant = PoliChipVariant.Good)
        }
    }
}

@Composable
private fun EmptyIncidentList(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(PoliDimens.SectionPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)) {
            Text(text = "현재 배정된 사건이 없습니다", style = MaterialTheme.typography.titleMedium)
            Text(text = "상황실 배정을 기다리거나 새로고침을 시도하세요.", style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
            PoliButton(text = "새로고침", onClick = onRefresh, variant = PoliButtonVariant.Secondary)
        }
    }
}

fun sampleIncidentListState(showClosedDialog: Boolean = false) =
    IncidentListUiState(
        policePhoneLabel = "POL-1A-0023 · 기동대 1부대 A팀",
        syncLabel = "동기화",
        showClosedDialog = showClosedDialog,
        incidents =
        listOf(
            AssignedIncidentUiModel(
                title = "사건 #1234",
                summary = "광주 북구 ○○산 · 60대 여성 · OP 3차",
                packageStatus = "오늘 13:40 적재 완료",
                assignmentStatus = "이 폴리폰에 active 배정된 사건"
            )
        )
    )

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun IncidentListPreview() {
    SuriMapTheme {
        IncidentListScreen(
            state = sampleIncidentListState(),
            onOpenIncident = {},
            onRefresh = {},
            onDismissClosedDialog = {}
        )
    }
}
