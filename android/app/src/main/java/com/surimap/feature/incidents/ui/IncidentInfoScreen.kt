package com.surimap.feature.incidents.ui

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgPrimary
import com.surimap.ui.theme.PoliFgSecondary
import com.surimap.ui.theme.SuriMapTheme

data class IncidentInfoUiState(
    val incidentTitle: String,
    val incidentId: String,
    val opLabel: String,
    val dutyShiftLabel: String,
    val assignmentLabel: String
) {
    companion object {
        fun empty(): IncidentInfoUiState =
            IncidentInfoUiState(
                incidentTitle = "선택한 사건",
                incidentId = "사건 컨텍스트 없음",
                opLabel = "OP 미확인",
                dutyShiftLabel = "근무조 미확인",
                assignmentLabel = "담당구역 미확인"
            )
    }
}

@Composable
fun IncidentInfoScreen(
    state: IncidentInfoUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(PoliDimens.SectionPadding)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
        ) {
            PoliButton(
                text = "지도",
                onClick = onBack,
                variant = PoliButtonVariant.Secondary
            )
            PoliChip(text = "사건정보", variant = PoliChipVariant.Neutral)
        }
        Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
            Text(
                text = state.incidentTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = PoliFgPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.incidentId,
                style = MaterialTheme.typography.bodySmall,
                color = PoliFgMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        PoliCard(strong = true) {
            IncidentInfoRow(label = "수색 차수", value = state.opLabel)
            IncidentInfoRow(label = "근무조", value = state.dutyShiftLabel)
            IncidentInfoRow(label = "담당구역", value = state.assignmentLabel)
        }
    }
}

@Composable
private fun IncidentInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = PoliFgMuted)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = PoliFgSecondary)
    }
}

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun IncidentInfoScreenPreview() {
    SuriMapTheme {
        IncidentInfoScreen(
            state =
                IncidentInfoUiState(
                    incidentTitle = "광주 북구 무등산 증심사 계곡 일대 실종자 수색",
                    incidentId = "incident-sample-001",
                    opLabel = "OP 3차",
                    dutyShiftLabel = "주간 1조",
                    assignmentLabel = "A팀 담당 구역"
                ),
            onBack = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
