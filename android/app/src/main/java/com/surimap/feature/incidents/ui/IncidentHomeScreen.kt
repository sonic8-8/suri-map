package com.surimap.feature.incidents.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.surimap.ui.components.PoliBanner
import com.surimap.ui.components.PoliBannerVariant
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliPullToRefresh
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgPrimary
import com.surimap.ui.theme.SuriMapTheme
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val IncidentInfoTitleColor = Color(0xFF0F172A)
private val IncidentInfoValueColor = Color(0xFF111827)
private val IncidentInfoLabelColor = Color(0xFF64748B)
private val IncidentInfoAccentColor = Color(0xFF2563EB)
private val IncidentInfoBlockColor = Color(0xFFEFF4FA)
private val IncidentInfoCardBorder = Color(0xFFE2E8F0)
private val IncidentInfoPhotoPlaceholder = Color(0xFFF1F5F9)
private val IncidentAssignmentCardColor = Color.White
private val IncidentAssignmentTextColor = Color(0xFF0F172A)
private val IncidentAssignmentSubtleColor = Color(0xFF475569)
private val IncidentAssignmentDividerColor = Color(0xFFE5E7EB)

enum class IncidentHomeMapDataStatus {
    Ready,
    Preparing,
    NeedsAttention,
    Missing
}

private enum class IncidentDetailTab(val label: String) {
    MissingPerson("실종자"),
    Assignment("참여/지휘"),
    Readiness("현장 준비")
}

data class IncidentHomeUiState(
    val incidentTitle: String,
    val missingPersonSummary: String,
    val incidentStatusLabel: String = "진행 중",
    val openedAtLabel: String? = null,
    val missingPersonName: String? = null,
    val missingPersonPhotoUrl: String? = null,
    val lastSeenAtLabel: String? = null,
    val lastSeenLocationLabel: String? = null,
    val appearanceLabel: String? = null,
    val opLabel: String,
    val assignmentLabel: String,
    val assignmentCountLabel: String = "참여 계정 확인 중",
    val assignmentRoleSummary: String? = null,
    val assignmentItems: List<IncidentAssignmentUiState> = emptyList(),
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
            add(incidentStatusLabel)
            openedAtLabel?.let(::add)
            missingPersonName?.let(::add)
            if (!missingPersonPhotoUrl.isNullOrBlank()) {
                add("실종자 사진")
            }
            lastSeenAtLabel?.let(::add)
            lastSeenLocationLabel?.let(::add)
            appearanceLabel?.let(::add)
            add(opLabel)
            add(assignmentLabel)
            add(assignmentCountLabel)
            assignmentRoleSummary?.let(::add)
            assignmentItems.take(5).forEach { assignment ->
                addAll(assignment.visibleText())
            }
            if (assignmentItems.size > 5) {
                add("외 ${assignmentItems.size - 5}명")
            }
            add(mapDataLabel)
            add(mapDataDetail)
            add(syncLabel)
            add(lastUpdatedLabel)
            add(outboxSummaryLabel)
            if (blockedOutboxCount > 0) {
                add("미전송 진단 보기")
            } else {
                add("지도 데이터 확인")
            }
        }

    companion object {
        fun sample(): IncidentHomeUiState =
            IncidentHomeUiState(
                incidentTitle = "광주 광산구 황룡강 생태길 실종 신고",
                missingPersonSummary = "70대 남성 · 회색 점퍼 · 보행 느림",
                openedAtLabel = "2026-05-28 09:10",
                missingPersonName = "홍길동",
                missingPersonPhotoUrl = "https://example.test/missing-person/hong.jpg",
                lastSeenAtLabel = "2026-05-28 08:40",
                lastSeenLocationLabel = "황룡강 생태길 북측 진입로",
                appearanceLabel = "회색 점퍼 · 보행 느림",
                opLabel = "2차 수색",
                assignmentLabel = "팀 담당 구역",
                assignmentCountLabel = "3개",
                assignmentRoleSummary = "사건 지휘 1 · 현장 지휘 1 · 수색 대원 1",
                assignmentItems = listOf(
                    IncidentAssignmentUiState(
                        displayName = "광산 실종팀 상황반",
                        roleLabel = "사건 지휘",
                        accountTypeLabel = "지휘",
                        organizationLabel = "실종팀",
                        assignedAtLabel = "2026-05-28 09:12"
                    ),
                    IncidentAssignmentUiState(
                        displayName = "기동대 1부대 A팀",
                        roleLabel = "현장 지휘",
                        accountTypeLabel = "팀",
                        organizationLabel = "지원부대",
                        assignedAtLabel = "2026-05-28 09:20"
                    ),
                    IncidentAssignmentUiState(
                        displayName = "광산 31호",
                        roleLabel = "수색 대원",
                        accountTypeLabel = "순찰차",
                        organizationLabel = "파출소",
                        assignedAtLabel = "2026-05-28 09:24"
                    )
                ),
                mapDataStatus = IncidentHomeMapDataStatus.Ready,
                mapDataDetail = "오프라인 지도와 사건 기본 정보가 준비되어 있습니다.",
                syncLabel = "최신 상태",
                lastUpdatedLabel = "방금 갱신",
                pendingOutboxCount = 3,
                blockedOutboxCount = 2
            )
    }
}

data class IncidentAssignmentUiState(
    val displayName: String,
    val roleLabel: String,
    val accountTypeLabel: String? = null,
    val organizationLabel: String? = null,
    val assignedAtLabel: String? = null
) {
    val metaLabel: String
        get() =
            listOfNotNull(accountTypeLabel, organizationLabel)
                .filter(String::isNotBlank)
                .joinToString(" · ")
                .ifBlank { "계정 정보 확인 중" }

    fun visibleText(): List<String> =
        buildList {
            add(displayName)
            add(roleLabel)
            add(metaLabel)
            assignedAtLabel?.let(::add)
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
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = PoliDimens.SectionPadding,
                            top = PoliDimens.Space6 + PoliDimens.Space5,
                            end = PoliDimens.SectionPadding
                        ),
                verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
            ) {
                Text(text = "사건 정보", style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
                IncidentSummaryHeader(state = state)
                Spacer(modifier = Modifier.height(PoliDimens.Space3))
                IncidentDetailIndexSection(
                    state = state,
                    onOpenMapData = onOpenMapData,
                    onOpenBlockedOutbox = onOpenBlockedOutbox
                )
            }

        }
    }
}

@Composable
private fun IncidentSummaryHeader(state: IncidentHomeUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = PoliDimens.Space2, bottom = PoliDimens.Space1),
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)
    ) {
        Text(
            text = state.incidentTitle,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = PoliFgPrimary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
            IncidentInfoBadge(text = state.incidentStatusLabel, variant = state.incidentStatusChipVariant())
            state.opLabel.takeIf(String::isNotBlank)?.let { opLabel ->
                IncidentInfoBadge(text = opLabel, variant = PoliChipVariant.Outbox)
            }
        }
        state.openedAtLabel?.let { openedAt ->
            Text(
                text = "접수 $openedAt",
                style = MaterialTheme.typography.bodyMedium,
                color = PoliFgMuted
            )
        }
    }
}

@Composable
private fun IncidentInfoBadge(text: String, variant: PoliChipVariant) {
    val (container, border, content) =
        when (variant) {
            PoliChipVariant.Good -> Triple(Color(0xFFE5F7EC), Color(0xFF8FD3AA), Color(0xFF1F6B45))
            PoliChipVariant.Warn -> Triple(Color(0xFFFFF0D7), Color(0xFFF0C47C), Color(0xFFA75D00))
            PoliChipVariant.Bad -> Triple(Color(0xFFFEE2E2), Color(0xFFE58A8A), Color(0xFFB91C1C))
            else -> Triple(Color(0xFFE8EEF4), Color(0xFFB6C4D2), Color(0xFF50657A))
        }

    Surface(
        modifier = Modifier.widthIn(min = 72.dp).heightIn(min = 32.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun IncidentDetailIndexSection(
    state: IncidentHomeUiState,
    onOpenMapData: () -> Unit,
    onOpenBlockedOutbox: () -> Unit
) {
    var selectedTabKey by rememberSaveable { mutableStateOf(IncidentDetailTab.MissingPerson.name) }
    val selectedTab =
        IncidentDetailTab.entries.firstOrNull { tab -> tab.name == selectedTabKey } ?: IncidentDetailTab.MissingPerson

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = IncidentAssignmentCardColor,
            contentColor = IncidentAssignmentTextColor,
            border = BorderStroke(1.dp, IncidentAssignmentDividerColor)
        ) {
            Column(
                modifier = Modifier.padding(PoliDimens.CardPadding),
                verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
            ) {
                IncidentDetailTabBar(
                    selectedTab = selectedTab,
                    onSelectTab = { tab -> selectedTabKey = tab.name },
                    hasReadinessWarning = state.blockedOutboxCount > 0 ||
                        state.mapDataStatus != IncidentHomeMapDataStatus.Ready
                )
                when (selectedTab) {
                    IncidentDetailTab.MissingPerson -> MissingPersonDetailContent(state = state)
                    IncidentDetailTab.Assignment -> AssignmentSummaryContent(state = state)
                    IncidentDetailTab.Readiness -> FieldReadinessContent(state = state)
                }
            }
        }
        if (selectedTab == IncidentDetailTab.Readiness) {
            FieldReadinessActionButton(
                state = state,
                onOpenMapData = onOpenMapData,
                onOpenBlockedOutbox = onOpenBlockedOutbox
            )
        }
    }
}

@Composable
private fun IncidentDetailTabBar(
    selectedTab: IncidentDetailTab,
    onSelectTab: (IncidentDetailTab) -> Unit,
    hasReadinessWarning: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color(0xFFF1F5F9),
        contentColor = IncidentAssignmentTextColor,
        border = BorderStroke(1.dp, IncidentAssignmentDividerColor)
    ) {
        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            IncidentDetailTab.entries.forEach { tab ->
                IncidentDetailTabButton(
                    tab = tab,
                    selected = tab == selectedTab,
                    showWarning = tab == IncidentDetailTab.Readiness && hasReadinessWarning,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun IncidentDetailTabButton(
    tab: IncidentDetailTab,
    selected: Boolean,
    showWarning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier =
            modifier
                .heightIn(min = 40.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) Color.White else Color.Transparent,
        contentColor = if (selected) IncidentAssignmentTextColor else IncidentAssignmentSubtleColor,
        border = if (selected) BorderStroke(1.dp, IncidentAssignmentDividerColor) else null
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PoliDimens.Space2, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space1),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                )
                if (showWarning) {
                    Surface(
                        modifier = Modifier.width(6.dp).height(6.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = Color(0xFFE11D48),
                        content = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingPersonDetailContent(state: IncidentHomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)) {
        MissingPersonCardHeader()
        MissingPersonPhotoPreview(
            photoUrl = state.missingPersonPhotoUrl,
            displayName = state.missingPersonName
        )
        MissingPersonIdentityBlock(
            name = state.missingPersonName,
            appearance = state.appearanceLabel
        )
        LastSeenBlock(
            lastSeenAt = state.lastSeenAtLabel,
            lastSeenLocation = state.lastSeenLocationLabel
        )
    }
}

@Composable
private fun MissingPersonCardHeader() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.width(4.dp).height(24.dp),
            shape = MaterialTheme.shapes.small,
            color = IncidentInfoAccentColor
        ) {}
        Text(
            text = "실종자 정보",
            style = MaterialTheme.typography.titleMedium,
            color = IncidentInfoTitleColor
        )
    }
}

@Composable
private fun MissingPersonPhotoPreview(photoUrl: String?, displayName: String?) {
    val resolvedUrl = photoUrl?.takeIf(String::isNotBlank)
    if (resolvedUrl == null) {
        MissingPersonPhotoPlaceholder(text = "실종자 사진 없음")
        return
    }
    val bitmapState =
        produceState<MissingPersonPhotoLoadState>(initialValue = MissingPersonPhotoLoadState.Loading, resolvedUrl) {
            value =
                loadMissingPersonPhotoBitmap(resolvedUrl)?.let(MissingPersonPhotoLoadState::Loaded)
                    ?: MissingPersonPhotoLoadState.Failed
        }
    val bitmap =
        when (val loadState = bitmapState.value) {
            MissingPersonPhotoLoadState.Failed -> {
                MissingPersonPhotoPlaceholder(text = "실종자 사진 불러오기 실패")
                return
            }
            is MissingPersonPhotoLoadState.Loaded -> loadState.bitmap
            MissingPersonPhotoLoadState.Loading -> {
                MissingPersonPhotoPlaceholder(text = "실종자 사진 불러오는 중")
                return
            }
        }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "${displayName?.takeIf(String::isNotBlank) ?: "실종자"} 사진",
        modifier =
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.medium),
        contentScale = ContentScale.Crop
    )
}

private sealed interface MissingPersonPhotoLoadState {
    data object Loading : MissingPersonPhotoLoadState
    data object Failed : MissingPersonPhotoLoadState
    data class Loaded(val bitmap: Bitmap) : MissingPersonPhotoLoadState
}

@Composable
private fun MissingPersonIdentityBlock(name: String?, appearance: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)) {
        Text(
            text = name?.takeIf(String::isNotBlank) ?: "실종자",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = IncidentInfoTitleColor
        )
        appearance?.takeIf(String::isNotBlank)?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = IncidentInfoLabelColor
            )
        }
    }
}

@Composable
private fun LastSeenBlock(lastSeenAt: String?, lastSeenLocation: String?) {
    val hasLastSeenAt = !lastSeenAt.isNullOrBlank()
    val hasLastSeenLocation = !lastSeenLocation.isNullOrBlank()
    if (!hasLastSeenAt && !hasLastSeenLocation) {
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = IncidentInfoBlockColor
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PoliDimens.Space3),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)
        ) {
            Text(text = "최종 목격", style = MaterialTheme.typography.labelLarge, color = IncidentInfoLabelColor)
            lastSeenAt?.takeIf(String::isNotBlank)?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = IncidentInfoValueColor
                )
            }
            lastSeenLocation?.takeIf(String::isNotBlank)?.let { value ->
                Text(text = value, style = MaterialTheme.typography.bodyMedium, color = IncidentInfoValueColor)
            }
        }
    }
}

@Composable
private fun MissingPersonPhotoPlaceholder(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 144.dp),
        shape = MaterialTheme.shapes.medium,
        color = IncidentInfoPhotoPlaceholder,
        border = BorderStroke(1.dp, IncidentInfoCardBorder)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(PoliDimens.Space4), contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = IncidentInfoLabelColor)
        }
    }
}

@Composable
private fun AssignmentSummaryContent(state: IncidentHomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
        Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)) {
            Text(
                text = "참여/지휘 정보",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = IncidentAssignmentTextColor
            )
            Text(
                text = listOfNotNull(state.assignmentCountLabel, state.assignmentRoleSummary)
                    .filter(String::isNotBlank)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = IncidentAssignmentSubtleColor
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
            val visibleAssignments = state.assignmentItems.take(5)
            if (visibleAssignments.isEmpty()) {
                Text(
                    text = "참여 계정 상세를 확인 중입니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IncidentAssignmentSubtleColor
                )
            } else {
                visibleAssignments.forEachIndexed { index, assignment ->
                    if (index > 0) {
                        AssignmentDivider()
                    }
                    AssignmentPersonRow(assignment = assignment)
                }
                if (state.assignmentItems.size > visibleAssignments.size) {
                    Text(
                        text = "외 ${state.assignmentItems.size - visibleAssignments.size}명",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IncidentAssignmentSubtleColor
                    )
                }
            }
        }
        AssignmentDivider()
        AssignmentInfoRow(title = "내 배정 구역", subtitle = state.assignmentLabel.ifBlank { "담당 구역 확인 중" })
    }
}

@Composable
private fun AssignmentPersonRow(assignment: IncidentAssignmentUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)) {
            Text(
                text = assignment.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = IncidentAssignmentTextColor
            )
            Text(
                text = assignment.metaLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = IncidentAssignmentSubtleColor
            )
            assignment.assignedAtLabel?.let { assignedAt ->
                Text(
                    text = "배정 $assignedAt",
                    style = MaterialTheme.typography.labelMedium,
                    color = IncidentAssignmentSubtleColor
                )
            }
        }
        PoliChip(text = assignment.roleLabel, variant = assignment.roleChipVariant())
    }
}

@Composable
private fun AssignmentInfoRow(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = IncidentAssignmentSubtleColor
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = IncidentAssignmentTextColor
        )
    }
}

@Composable
private fun AssignmentDivider() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(1.dp),
        color = IncidentAssignmentDividerColor,
        content = {}
    )
}

private fun IncidentAssignmentUiState.roleChipVariant(): PoliChipVariant =
    when (roleLabel) {
        "사건 지휘" -> PoliChipVariant.Good
        "현장 지휘" -> PoliChipVariant.Outbox
        "수색 대원" -> PoliChipVariant.Neutral
        else -> PoliChipVariant.Warn
    }

@Composable
private fun FieldReadinessContent(
    state: IncidentHomeUiState
) {
    Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
        Text(text = "현장 기록 준비", style = MaterialTheme.typography.titleMedium)
        FieldReadinessRow(title = state.mapDataLabel, subtitle = state.mapDataDetail) {
            PoliChip(text = state.mapDataLabel.shortStatusLabel(), variant = state.mapDataVariant)
        }
        FieldReadinessRow(title = "동기화 상태", subtitle = state.lastUpdatedLabel) {
            PoliChip(text = state.syncLabel, variant = PoliChipVariant.Neutral)
        }
        FieldReadinessRow(title = "미전송 기록", subtitle = "정상 대기는 자동 전송됩니다.") {
            PoliChip(text = state.outboxSummaryLabel, variant = state.outboxVariant)
        }
        if (state.blockedOutboxCount > 0) {
            PoliBanner(
                text = "자동 전송으로 해결되지 않는 항목이 있습니다. 원인과 재시도 가능 여부를 확인하세요.",
                variant = PoliBannerVariant.Bad
            )
        }
    }
}

@Composable
private fun FieldReadinessActionButton(
    state: IncidentHomeUiState,
    onOpenMapData: () -> Unit,
    onOpenBlockedOutbox: () -> Unit
) {
    PoliButton(
        text = if (state.blockedOutboxCount > 0) "미전송 진단 보기" else "지도 데이터 확인",
        onClick = if (state.blockedOutboxCount > 0) onOpenBlockedOutbox else onOpenMapData,
        modifier = Modifier.fillMaxWidth(),
        variant = PoliButtonVariant.Secondary
    )
}

@Composable
private fun FieldReadinessRow(
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = IncidentAssignmentSubtleColor
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IncidentAssignmentTextColor
                )
            }
        }
        trailing()
    }
}

private fun String.shortStatusLabel(): String =
    when {
        contains("완료") -> "준비 완료"
        contains("중") -> "준비 중"
        contains("확인") -> "확인 필요"
        else -> "준비 필요"
    }

private fun IncidentHomeUiState.incidentStatusChipVariant(): PoliChipVariant =
    when (incidentStatusLabel) {
        "종료" -> PoliChipVariant.Bad
        "진행 중" -> PoliChipVariant.Good
        else -> PoliChipVariant.Neutral
    }

private suspend fun loadMissingPersonPhotoBitmap(url: String): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            URL(url).openStream().use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
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
