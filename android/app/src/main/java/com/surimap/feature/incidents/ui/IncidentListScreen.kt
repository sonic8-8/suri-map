package com.surimap.feature.incidents.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.surimap.ui.components.PoliAppBar
import com.surimap.ui.components.PoliBanner
import com.surimap.ui.components.PoliBannerVariant
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliDialog
import com.surimap.ui.navigation.IncidentContext
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliEmphasis
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgSecondary
import com.surimap.ui.theme.PoliPrimary
import com.surimap.ui.theme.PoliSuccess
import com.surimap.ui.theme.PoliWarning
import com.surimap.ui.theme.SuriMapTheme

enum class IncidentListStatus {
    Loading,
    Ready,
    Empty,
    Error,
    Offline,
    Stale
}

enum class IncidentPackageStatus(val label: String) {
    NotInstalled("미설치"),
    UpdateRequired("확인 필요"),
    Ready("준비 완료"),
    Failed("설치 실패")
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
            add(syncLabel)
            add(status.name)
            message?.let(::add)
            incidents.forEach { incident ->
                add(incident.title)
                add(incident.progressLabel)
                add(incident.summary)
                add(incident.packageStatus.label)
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
                syncLabel = "최신 상태",
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
                syncLabel = "배정 없음",
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
    val packageStatus: IncidentPackageStatus = IncidentPackageStatus.NotInstalled
) {
    val progressLabel: String
        get() = listOf("진행 중", currentOpLabel?.toSearchRoundLabel()?.takeIf(String::isNotBlank))
            .filterNotNull()
            .joinToString(" · ")

    fun toIncidentContext(): IncidentContext =
        IncidentContext(
            incidentId = incidentId,
            currentOpId = currentOpId,
            currentOpLabel = currentOpLabel,
            currentDutyShiftId = currentDutyShiftId
        )
}

private fun String.toSearchRoundLabel(): String =
    replace(Regex("""OP\s*(\d+)차"""), "$1차 수색")

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
                trailing = {
                    IncidentListAppBarActions(state = state, onRefresh = onRefresh)
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
private fun IncidentListAppBarActions(
    state: IncidentListUiState,
    onRefresh: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IncidentSyncStatus(state = state)
        IconButton(
            onClick = onRefresh,
            enabled = state.canRefresh,
            modifier =
                Modifier
                    .size(40.dp)
                    .semantics {
                        contentDescription = "사건 목록 새로고침"
                        role = Role.Button
                    }
        ) {
            androidx.compose.material3.Icon(
                imageVector = SuriRefreshIcon,
                contentDescription = null,
                tint = if (state.canRefresh) PoliFgSecondary else PoliFgMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun IncidentSyncStatus(state: IncidentListUiState) {
    val (label, color) =
        when (state.status) {
            IncidentListStatus.Loading -> "갱신 중" to PoliPrimary
            IncidentListStatus.Ready -> "최신 상태" to PoliSuccess
            IncidentListStatus.Empty -> "배정 없음" to PoliFgMuted
            IncidentListStatus.Error -> "오류" to PoliEmphasis
            IncidentListStatus.Offline -> "오프라인" to PoliWarning
            IncidentListStatus.Stale -> "이전 정보" to PoliWarning
        }

    Row(
        modifier = Modifier.requiredWidth(72.dp),
        horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(color = color, shape = CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = PoliFgSecondary,
            maxLines = 1
        )
    }
}

private val SuriRefreshIcon: ImageVector =
    ImageVector.Builder(
        name = "SuriRefresh",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
            reflectiveCurveTo(7.58f, 20f, 12f, 20f)
            curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
            horizontalLineTo(17.65f)
            curveTo(16.82f, 16.33f, 14.6f, 18f, 12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            reflectiveCurveTo(8.69f, 6f, 12f, 6f)
            curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
            lineTo(13f, 11f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            lineTo(17.65f, 6.35f)
            close()
        }
    }.build()

private val SuriPackageCheckIcon: ImageVector =
    ImageVector.Builder(
        name = "SuriPackageCheck",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(4f, 3f)
            horizontalLineTo(20f)
            verticalLineTo(8f)
            horizontalLineTo(4f)
            verticalLineTo(3f)
            close()
            moveTo(5f, 9.5f)
            horizontalLineTo(19f)
            verticalLineTo(20f)
            horizontalLineTo(5f)
            verticalLineTo(9.5f)
            close()
            moveTo(9f, 11.5f)
            horizontalLineTo(15f)
            verticalLineTo(13.5f)
            horizontalLineTo(9f)
            verticalLineTo(11.5f)
            close()
            moveTo(10.4f, 17.4f)
            lineTo(7.8f, 14.8f)
            lineTo(6.4f, 16.2f)
            lineTo(10.4f, 20.2f)
            lineTo(17.8f, 12.8f)
            lineTo(16.4f, 11.4f)
            close()
        }
    }.build()

private val SuriPackageMinusIcon: ImageVector =
    ImageVector.Builder(
        name = "SuriPackageMinus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(4f, 3f)
            horizontalLineTo(20f)
            verticalLineTo(8f)
            horizontalLineTo(4f)
            verticalLineTo(3f)
            close()
            moveTo(5f, 9.5f)
            horizontalLineTo(19f)
            verticalLineTo(20f)
            horizontalLineTo(5f)
            verticalLineTo(9.5f)
            close()
            moveTo(9f, 11.5f)
            horizontalLineTo(15f)
            verticalLineTo(13.5f)
            horizontalLineTo(9f)
            verticalLineTo(11.5f)
            close()
            moveTo(7f, 16f)
            horizontalLineTo(17f)
            verticalLineTo(18f)
            horizontalLineTo(7f)
            verticalLineTo(16f)
            close()
        }
    }.build()

private val SuriPackageXIcon: ImageVector =
    ImageVector.Builder(
        name = "SuriPackageX",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(4f, 3f)
            horizontalLineTo(20f)
            verticalLineTo(8f)
            horizontalLineTo(4f)
            verticalLineTo(3f)
            close()
            moveTo(5f, 9.5f)
            horizontalLineTo(19f)
            verticalLineTo(20f)
            horizontalLineTo(5f)
            verticalLineTo(9.5f)
            close()
            moveTo(9f, 11.5f)
            horizontalLineTo(15f)
            verticalLineTo(13.5f)
            horizontalLineTo(9f)
            verticalLineTo(11.5f)
            close()
            moveTo(8.2f, 14.2f)
            lineTo(9.6f, 12.8f)
            lineTo(12f, 15.2f)
            lineTo(14.4f, 12.8f)
            lineTo(15.8f, 14.2f)
            lineTo(13.4f, 16.6f)
            lineTo(15.8f, 19f)
            lineTo(14.4f, 20.4f)
            lineTo(12f, 18f)
            lineTo(9.6f, 20.4f)
            lineTo(8.2f, 19f)
            lineTo(10.6f, 16.6f)
            close()
        }
    }.build()

@Composable
private fun AssignedIncidentList(
    state: IncidentListUiState,
    onOpenIncident: (AssignedIncidentUiModel) -> Unit,
    onOpenOfflinePackage: (AssignedIncidentUiModel) -> Unit,
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
    }
}

@Composable
private fun IncidentCard(
    incident: AssignedIncidentUiModel,
    onOpenIncident: () -> Unit,
    onOpenOfflinePackage: () -> Unit
) {
    val cardTextColor = Color(0xFF0F172A)
    val cardMutedTextColor = Color(0xFF475569)

    PoliCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = "현장 기록 열기",
                    role = Role.Button,
                    onClick = onOpenIncident
                ),
        strong = true,
        containerColor = Color.White
    ) {
        Text(text = incident.title, style = MaterialTheme.typography.titleMedium, color = cardTextColor)
        Text(text = incident.progressLabel, style = MaterialTheme.typography.bodyMedium, color = cardMutedTextColor)
        if (incident.summary.isNotBlank()) {
            IncidentCardRow(text = incident.summary, textColor = cardMutedTextColor)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OfflinePackageIconButton(
                status = incident.packageStatus,
                onClick = onOpenOfflinePackage,
                modifier = Modifier.weight(1f)
            )
            IncidentPrimaryActionButton(
                text = "현장 기록 열기",
                onClick = onOpenIncident,
                modifier = Modifier.weight(3f)
            )
        }
    }
}

@Composable
private fun IncidentPrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = PoliPrimary,
        contentColor = Color.White,
        modifier =
            modifier
                .height(58.dp)
                .semantics {
                    contentDescription = text
                    role = Role.Button
                }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OfflinePackageIconButton(
    status: IncidentPackageStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = status.buttonColors
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = colors.container,
        contentColor = colors.content,
        border = BorderStroke(1.dp, colors.border),
        modifier =
            modifier
                .height(58.dp)
                .semantics {
                    contentDescription = "오프라인 패키지 ${status.label}"
                    role = Role.Button
                }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = status.icon,
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.content,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class PackageButtonColors(
    val container: Color,
    val border: Color,
    val content: Color
)

private val IncidentPackageStatus.buttonColors: PackageButtonColors
    get() =
        when (this) {
            IncidentPackageStatus.Ready ->
                PackageButtonColors(
                    container = Color(0xFFE8F7EE),
                    border = Color(0xFF9FD7B4),
                    content = Color(0xFF176B3A)
                )
            IncidentPackageStatus.UpdateRequired ->
                PackageButtonColors(
                    container = Color(0xFFFFF3D6),
                    border = Color(0xFFE8C06A),
                    content = Color(0xFF8A5A00)
                )
            IncidentPackageStatus.NotInstalled,
            IncidentPackageStatus.Failed ->
                PackageButtonColors(
                    container = Color(0xFFFFE7E7),
                    border = Color(0xFFE9A3A3),
                    content = Color(0xFF9A1F1F)
                )
        }

private val IncidentPackageStatus.icon: ImageVector
    get() =
        when (this) {
            IncidentPackageStatus.Ready -> SuriPackageCheckIcon
            IncidentPackageStatus.UpdateRequired -> SuriPackageMinusIcon
            IncidentPackageStatus.NotInstalled,
            IncidentPackageStatus.Failed -> SuriPackageXIcon
        }

@Composable
private fun IncidentCardRow(
    text: String,
    textColor: Color,
    content: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        content()
    }
}

@Composable
private fun EmptyIncidentList(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    MessageIncidentList(
        title = "현재 배정된 사건이 없습니다",
        body = "상황실 배정을 기다리거나 상단의 새로고침을 시도하세요.",
        actionText = null,
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
                currentOpLabel = "OP 3차",
                currentDutyShiftId = "duty-shift-014",
                title = "무등산 증심사 계곡 실종자 수색",
                summary = "광주 북구 ○○산 · 60대 여성",
                packageStatus = IncidentPackageStatus.Ready
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
