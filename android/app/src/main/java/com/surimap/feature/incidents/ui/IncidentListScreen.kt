package com.surimap.feature.incidents.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.surimap.ui.components.PoliAppBar
import com.surimap.ui.components.PoliBanner
import com.surimap.ui.components.PoliBannerVariant
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliDialog
import com.surimap.ui.components.PoliRow
import com.surimap.ui.navigation.IncidentContext
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.SuriMapTheme

enum class IncidentListStatus {
    Loading,
    Ready,
    Empty,
    Error,
    Offline,
    Stale
}

data class IncidentListUiState(
    val policePhoneLabel: String,
    val syncLabel: String,
    val status: IncidentListStatus,
    val incidents: List<AssignedIncidentUiModel>,
    val showClosedDialog: Boolean = false,
    val message: String? = null,
    val canRefresh: Boolean = true,
    val shouldClearIncidentContext: Boolean = false
) {
    fun visibleText(): List<String> =
        buildList {
            add(policePhoneLabel)
            add(syncLabel)
            add(status.name)
            message?.let(::add)
            incidents.forEach { incident ->
                add(incident.title)
                add(incident.summary)
                add(incident.packageStatus)
                add(incident.assignmentStatus)
            }
            add(primaryOpenLabel)
        }

    val primaryOpenLabel: String =
        when {
            incidents.isEmpty() -> "선택한 사건 열기"
            else -> "현장 기록 열기"
        }

    companion object {
        fun loading(policePhoneLabel: String): IncidentListUiState =
            IncidentListUiState(
                policePhoneLabel = policePhoneLabel,
                syncLabel = "갱신 중",
                status = IncidentListStatus.Loading,
                incidents = emptyList(),
                canRefresh = false
            )

        fun ready(
            policePhoneLabel: String,
            incidents: List<AssignedIncidentUiModel>
        ): IncidentListUiState =
            IncidentListUiState(
                policePhoneLabel = policePhoneLabel,
                syncLabel = "동기화",
                status = IncidentListStatus.Ready,
                incidents = incidents
            )

        fun empty(
            policePhoneLabel: String,
            showClosedDialog: Boolean = false,
            shouldClearIncidentContext: Boolean = false
        ): IncidentListUiState =
            IncidentListUiState(
                policePhoneLabel = policePhoneLabel,
                syncLabel = "대기",
                status = IncidentListStatus.Empty,
                incidents = emptyList(),
                showClosedDialog = showClosedDialog,
                message = "현재 배정된 사건이 없습니다",
                shouldClearIncidentContext = shouldClearIncidentContext
            )

        fun error(policePhoneLabel: String, message: String): IncidentListUiState =
            IncidentListUiState(
                policePhoneLabel = policePhoneLabel,
                syncLabel = "오류",
                status = IncidentListStatus.Error,
                incidents = emptyList(),
                message = message
            )

        fun offline(policePhoneLabel: String): IncidentListUiState =
            IncidentListUiState(
                policePhoneLabel = policePhoneLabel,
                syncLabel = "오프라인",
                status = IncidentListStatus.Offline,
                incidents = emptyList(),
                message = "오프라인입니다. 내부망 연결 후 다시 갱신됩니다.",
                canRefresh = false
            )
    }
}

data class AssignedIncidentUiModel(
    val incidentId: String,
    val currentOpId: String? = null,
    val currentOpLabel: String? = null,
    val currentDutyShiftId: String? = null,
    val title: String,
    val summary: String,
    val packageStatus: String,
    val assignmentStatus: String
) {
    fun toIncidentContext(): IncidentContext =
        IncidentContext(
            incidentId = incidentId,
            currentOpId = currentOpId,
            currentOpLabel = currentOpLabel,
            currentDutyShiftId = currentDutyShiftId
        )
}

@Composable
fun IncidentListScreen(
    state: IncidentListUiState,
    onOpenIncident: (AssignedIncidentUiModel) -> Unit,
    onOpenOfflinePackage: (AssignedIncidentUiModel) -> Unit,
    onRefresh: () -> Unit,
    onDismissClosedDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PoliAppBar(
                title = "사건 선택",
                subtitle = state.policePhoneLabel,
                trailing = {
                    PoliChip(text = state.syncLabel, variant = PoliChipVariant.Good)
                }
            )

            when {
                state.status == IncidentListStatus.Loading ->
                    MessageIncidentList(
                        title = "배정 사건을 확인하고 있습니다",
                        body = "캐시된 사건이 없으면 잠시 빈 화면으로 표시됩니다.",
                        actionText = null,
                        onAction = onRefresh,
                        modifier = Modifier.weight(1f)
                    )

                state.status == IncidentListStatus.Error ->
                    MessageIncidentList(
                        title = state.message ?: "사건 목록을 불러오지 못했습니다.",
                        body = "내부망 연결 상태를 확인한 뒤 다시 시도하세요.",
                        actionText = "다시 시도",
                        onAction = onRefresh,
                        modifier = Modifier.weight(1f)
                    )

                state.status == IncidentListStatus.Offline ->
                    MessageIncidentList(
                        title = "오프라인입니다",
                        body = state.message ?: "내부망 연결 후 다시 갱신됩니다.",
                        actionText = null,
                        onAction = onRefresh,
                        modifier = Modifier.weight(1f)
                    )

                state.incidents.isEmpty() ->
                    EmptyIncidentList(onRefresh = onRefresh, modifier = Modifier.weight(1f))

                else ->
                    AssignedIncidentList(
                        state = state,
                        onOpenIncident = onOpenIncident,
                        onOpenOfflinePackage = onOpenOfflinePackage,
                        onRefresh = onRefresh,
                        modifier = Modifier.weight(1f)
                    )
            }
        }

        if (state.showClosedDialog) {
            PoliDialog(
                title = "사건이 종료되었습니다.",
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
private fun AssignedIncidentList(
    state: IncidentListUiState,
    onOpenIncident: (AssignedIncidentUiModel) -> Unit,
    onOpenOfflinePackage: (AssignedIncidentUiModel) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = PoliDimens.SectionPadding)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
    ) {
        if (state.status == IncidentListStatus.Stale) {
            PoliBanner(text = state.message ?: "마지막 갱신 정보입니다.", variant = PoliBannerVariant.Warn)
        }
        state.incidents.forEach { incident ->
            IncidentCard(
                incident = incident,
                onOpenIncident = { onOpenIncident(incident) },
                onOpenOfflinePackage = { onOpenOfflinePackage(incident) }
            )
        }

        PoliButton(
            text = "새로고침",
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            variant = PoliButtonVariant.Secondary,
            enabled = state.canRefresh
        )
    }
}

@Composable
private fun IncidentCard(
    incident: AssignedIncidentUiModel,
    onOpenIncident: () -> Unit,
    onOpenOfflinePackage: () -> Unit
) {
    PoliCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = "현장 기록 열기",
                    role = Role.Button,
                    onClick = onOpenIncident
                ),
        strong = true
    ) {
        Text(text = incident.title, style = MaterialTheme.typography.titleMedium)
        Text(text = incident.summary, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
        PoliRow(title = "패키지 상태", subtitle = incident.packageStatus) {
            PoliChip(text = "확인", variant = PoliChipVariant.Neutral)
        }
        PoliRow(title = "현재 폴리폰 배정", subtitle = incident.assignmentStatus) {
            PoliChip(text = "활성", variant = PoliChipVariant.Good)
        }
        PoliButton(
            text = "현장 기록 열기",
            onClick = onOpenIncident,
            modifier = Modifier.fillMaxWidth()
        )
        PoliButton(
            text = "오프라인 패키지",
            onClick = onOpenOfflinePackage,
            modifier = Modifier.fillMaxWidth(),
            variant = PoliButtonVariant.Secondary
        )
    }
}

@Composable
private fun EmptyIncidentList(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    MessageIncidentList(
        title = "현재 배정된 사건이 없습니다",
        body = "상황실 배정을 기다리거나 새로고침을 시도하세요.",
        actionText = "새로고침",
        onAction = onRefresh,
        modifier = modifier
    )
}

@Composable
private fun MessageIncidentList(
    title: String,
    body: String,
    actionText: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth().padding(PoliDimens.SectionPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
            if (actionText != null) {
                PoliButton(text = actionText, onClick = onAction, variant = PoliButtonVariant.Secondary)
            }
        }
    }
}

fun sampleIncidentListState(showClosedDialog: Boolean = false) =
    IncidentListUiState.ready(
        policePhoneLabel = "POL-1A-0023 · 기동대 1부대 A팀",
        incidents =
        listOf(
            AssignedIncidentUiModel(
                incidentId = "inc-precinct-first-001",
                currentOpId = "op-003",
                currentDutyShiftId = "duty-shift-014",
                title = "사건 #1234",
                summary = "광주 북구 ○○산 · 60대 여성 · OP 3차",
                packageStatus = "오늘 13:40 적재 완료",
                assignmentStatus = "이 폴리폰에 active 배정된 사건"
            )
        )
    ).copy(showClosedDialog = showClosedDialog)

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun IncidentListPreview() {
    SuriMapTheme {
        IncidentListScreen(
            state = sampleIncidentListState(),
            onOpenIncident = {},
            onOpenOfflinePackage = {},
            onRefresh = {},
            onDismissClosedDialog = {}
        )
    }
}
