package com.surimap.feature.marker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.surimap.ui.components.PoliAppBar
import com.surimap.ui.components.PoliBanner
import com.surimap.ui.components.PoliBannerVariant
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonSize
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliProgress
import com.surimap.ui.components.PoliRow
import com.surimap.ui.theme.PoliBgBase
import com.surimap.ui.theme.PoliBgInput
import com.surimap.ui.theme.PoliBorder
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliEmphasis
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgPrimary
import com.surimap.ui.theme.PoliFgSecondary
import com.surimap.ui.theme.PoliOverlayDim
import com.surimap.ui.theme.PoliWarning
import com.surimap.ui.theme.SuriMapTheme

enum class MarkerDetailPhotoStatus(val label: String) {
    Attached("첨부됨"),
    Attaching("attach 진행 중"),
    Deleting("삭제 진행 중"),
    Failed("실패")
}

data class MarkerDetailPhotoUiState(
    val photoId: String,
    val label: String,
    val status: MarkerDetailPhotoStatus,
    val progress: Float = 1f
)

data class MarkerDetailUiState(
    val markerId: String,
    val markerType: MarkerType,
    val title: String,
    val memo: String,
    val version: Long,
    val lon: Double?,
    val lat: Double?,
    val createdByAccountId: String,
    val securityContextAccountId: String,
    val canManageAllMarkers: Boolean,
    val canEditByContext: Boolean? = null,
    val policePhoneLabel: String,
    val accountLabel: String,
    val locationLabel: String,
    val occurredAtLabel: String,
    val versionLabel: String,
    val syncLabel: String,
    val mutationStatus: MarkerSaveStatus = MarkerSaveStatus.Editing,
    val photos: List<MarkerDetailPhotoUiState>,
    val showDeleteConfirm: Boolean
) {
    val isOwnMarker: Boolean = createdByAccountId == securityContextAccountId
    val canEdit: Boolean = canEditByContext ?: (isOwnMarker || canManageAllMarkers)
    val canSave: Boolean = canEdit && version > 0 && mutationStatus != MarkerSaveStatus.Saving
    val canDelete: Boolean = canEdit && version > 0 && mutationStatus != MarkerSaveStatus.Saving
    val longPressDeleteEnabled: Boolean = false

    val permissionLabel: String =
        when {
            canEditByContext == true -> "편집 가능"
            canEditByContext == false -> "읽기 전용"
            isOwnMarker -> "내가 작성"
            canManageAllMarkers -> "편집 가능"
            else -> "읽기 전용"
        }

    val statusLabel: String =
        when (mutationStatus) {
            MarkerSaveStatus.Editing -> syncLabel
            MarkerSaveStatus.Saving -> "저장 중"
            MarkerSaveStatus.PendingOutbox -> "오프라인 저장됨 · 전송 대기"
            MarkerSaveStatus.Saved -> "저장 완료"
            MarkerSaveStatus.Failed -> "저장 실패"
        }

    fun visibleText(): List<String> =
        buildList {
            add("마커 상세")
            add(markerType.label)
            add(markerType.apiValue)
            add(title)
            add(memo)
            add(permissionLabel)
            add("SecurityContext.accountId=$securityContextAccountId")
            add("createdByAccountId=$createdByAccountId")
            add(policePhoneLabel)
            add(accountLabel)
            add(locationLabel)
            add(occurredAtLabel)
            add(versionLabel)
            add(syncLabel)
            add(statusLabel)
            photos.forEach { photo ->
                add(photo.label)
                add(photo.status.label)
            }
            if (canEdit) {
                add("저장")
            }
            if (canDelete) {
                add("삭제")
            }
            if (showDeleteConfirm) {
                add("삭제 확인")
                add("마커를 삭제합니다")
            }
        }

    companion object {
        fun ownMarker(showDeleteConfirm: Boolean = false): MarkerDetailUiState =
            base(
                createdByAccountId = "acct-team-alpha",
                securityContextAccountId = "acct-team-alpha",
                canManageAllMarkers = false,
                showDeleteConfirm = showDeleteConfirm
            )

        fun readonlyOtherAccount(): MarkerDetailUiState =
            base(
                markerType = MarkerType.PERSON_FOUND,
                title = "발견 보고",
                memo = "북측 능선 50m 지점 발견. 보호자 확인 중.",
                createdByAccountId = "acct-other-team",
                securityContextAccountId = "acct-team-alpha",
                canManageAllMarkers = false,
                policePhoneLabel = "실종팀 폴리폰",
                accountLabel = "실종팀 지휘 계정"
            )

        fun loading(markerId: String): MarkerDetailUiState =
            base(
                markerId = markerId,
                markerType = MarkerType.NOTE,
                title = "마커 확인 중",
                memo = "",
                createdByAccountId = "",
                securityContextAccountId = "",
                canManageAllMarkers = false,
                canEditByContext = false,
                policePhoneLabel = "폴리폰 확인 중",
                accountLabel = "계정 확인 중",
                locationLabel = "위치 확인 중",
                occurredAtLabel = "시각 확인 중",
                version = 0,
                versionLabel = "version 확인 중",
                syncLabel = "조회 중",
                mutationStatus = MarkerSaveStatus.Saving,
                photos = emptyList()
            )

        fun unavailable(markerId: String): MarkerDetailUiState =
            base(
                markerId = markerId,
                markerType = MarkerType.NOTE,
                title = "마커를 찾을 수 없음",
                memo = "",
                createdByAccountId = "",
                securityContextAccountId = "",
                canManageAllMarkers = false,
                canEditByContext = false,
                policePhoneLabel = "폴리폰 미확인",
                accountLabel = "계정 미확인",
                locationLabel = "위치 미확인",
                occurredAtLabel = "시각 미확인",
                version = 0,
                versionLabel = "version 미확인",
                syncLabel = "조회 실패",
                mutationStatus = MarkerSaveStatus.Failed,
                photos = emptyList()
            )

        fun withPhotoProgress(): MarkerDetailUiState =
            ownMarker().copy(
                photos =
                listOf(
                    MarkerDetailPhotoUiState("photo-attaching-001", "사진 1", MarkerDetailPhotoStatus.Attaching, 0.56f),
                    MarkerDetailPhotoUiState("photo-deleting-001", "사진 2", MarkerDetailPhotoStatus.Deleting, 0.42f)
                )
            )

        private fun base(
            markerId: String = "mk-precinct-clue-001",
            markerType: MarkerType = MarkerType.CLUE,
            title: String = "의류 발견",
            memo: String = "검정 패딩, 회색 운동화. 20m 북측 능선 측구.",
            version: Long = 3,
            lon: Double? = 126.9134,
            lat: Double? = 35.1631,
            createdByAccountId: String,
            securityContextAccountId: String,
            canManageAllMarkers: Boolean,
            canEditByContext: Boolean? = null,
            policePhoneLabel: String = "기동대 1부대 A팀 폴리폰",
            accountLabel: String = "기동대 1부대 A팀 계정",
            locationLabel: String = "35.163100, 126.913400",
            occurredAtLabel: String = "14:18 · clock +120ms",
            versionLabel: String = "v3 · 마지막 수정 14:24",
            syncLabel: String = "동기화",
            mutationStatus: MarkerSaveStatus = MarkerSaveStatus.Editing,
            photos: List<MarkerDetailPhotoUiState> =
                listOf(
                    MarkerDetailPhotoUiState("photo-001", "사진 1", MarkerDetailPhotoStatus.Attached),
                    MarkerDetailPhotoUiState("photo-002", "사진 2", MarkerDetailPhotoStatus.Attached),
                    MarkerDetailPhotoUiState("photo-003", "사진 3", MarkerDetailPhotoStatus.Attached)
                ),
            showDeleteConfirm: Boolean = false
        ): MarkerDetailUiState =
            MarkerDetailUiState(
                markerId = markerId,
                markerType = markerType,
                title = title,
                memo = memo,
                version = version,
                lon = lon,
                lat = lat,
                createdByAccountId = createdByAccountId,
                securityContextAccountId = securityContextAccountId,
                canManageAllMarkers = canManageAllMarkers,
                canEditByContext = canEditByContext,
                policePhoneLabel = policePhoneLabel,
                accountLabel = accountLabel,
                locationLabel = locationLabel,
                occurredAtLabel = occurredAtLabel,
                versionLabel = versionLabel,
                syncLabel = syncLabel,
                mutationStatus = mutationStatus,
                photos = photos,
                showDeleteConfirm = showDeleteConfirm
            )
    }
}

@Composable
fun MarkerDetailScreen(
    state: MarkerDetailUiState,
    onBack: () -> Unit,
    onMemoChange: (String) -> Unit,
    onSave: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onAddPhoto: () -> Unit,
    onRetryPhoto: (MarkerDetailPhotoUiState) -> Unit,
    onDeletePhoto: (MarkerDetailPhotoUiState) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(PoliBgBase)) {
        Column(modifier = Modifier.fillMaxSize()) {
            PoliAppBar(
                title = "마커 상세",
                subtitle = "${state.markerId} · ${state.permissionLabel}",
                showBack = true,
                onBack = onBack,
                trailing = {
                    PoliChip(text = state.statusLabel, variant = state.mutationStatusVariant)
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
                MarkerSummaryCard(state = state)
                if (!state.canEdit) {
                    PoliBanner(
                        text = "읽기 전용 — 본인이 생성한 마커만 수정·삭제 가능합니다.",
                        variant = PoliBannerVariant.Warn
                    )
                }
                MarkerMemoCard(state = state, onMemoChange = onMemoChange)
                MarkerPhotosCard(
                    state = state,
                    onAddPhoto = onAddPhoto,
                    onRetryPhoto = onRetryPhoto,
                    onDeletePhoto = onDeletePhoto
                )
                MarkerMetaCard(state = state)
            }
            MarkerDetailActions(
                state = state,
                onBack = onBack,
                onSave = onSave,
                onRequestDelete = onRequestDelete
            )
        }

        if (state.showDeleteConfirm) {
            DeleteConfirmDialog(
                onDismissDelete = onDismissDelete,
                onConfirmDelete = onConfirmDelete
            )
        }
    }
}

@Composable
private fun MarkerSummaryCard(state: MarkerDetailUiState) {
    PoliCard(strong = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space4), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = PoliBgInput,
                contentColor = if (state.markerType == MarkerType.PERSON_FOUND) PoliEmphasis else PoliFgSecondary,
                border = BorderStroke(2.dp, if (state.markerType == MarkerType.PERSON_FOUND) PoliEmphasis else PoliBorder)
            ) {
                Box(modifier = Modifier.padding(18.dp), contentAlignment = Alignment.Center) {
                    Text(text = state.markerType.label.take(2), style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)) {
                Text(text = "${state.markerType.label} (${state.markerType.apiValue})", style = MaterialTheme.typography.labelMedium, color = PoliFgMuted)
                Text(text = state.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = state.permissionLabel, style = MaterialTheme.typography.bodyMedium, color = PoliFgSecondary)
            }
        }
    }
}

@Composable
private fun MarkerMemoCard(state: MarkerDetailUiState, onMemoChange: (String) -> Unit) {
    PoliCard {
        Text(text = "메모", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.memo,
            onValueChange = onMemoChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp),
            readOnly = !state.canEdit,
            textStyle = MaterialTheme.typography.bodyMedium,
            minLines = 3,
            shape = MaterialTheme.shapes.medium
        )
        Text(
            text = if (state.canEdit) "${state.memo.length} / 500 · 자기 계정 생성분만 수정 가능" else "읽기 전용",
            style = MaterialTheme.typography.bodySmall,
            color = PoliFgMuted
        )
    }
}

@Composable
private fun MarkerPhotosCard(
    state: MarkerDetailUiState,
    onAddPhoto: () -> Unit,
    onRetryPhoto: (MarkerDetailPhotoUiState) -> Unit,
    onDeletePhoto: (MarkerDetailPhotoUiState) -> Unit
) {
    PoliCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
            Text(text = "사진 ${state.photos.size}장", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (state.canEdit) {
                PoliButton(text = "사진 추가", onClick = onAddPhoto, size = PoliButtonSize.Small, variant = PoliButtonVariant.Secondary)
            }
        }
        state.photos.forEach { photo ->
            PhotoDetailRow(
                photo = photo,
                canEdit = state.canEdit,
                onRetryPhoto = onRetryPhoto,
                onDeletePhoto = onDeletePhoto
            )
        }
    }
}

@Composable
private fun PhotoDetailRow(
    photo: MarkerDetailPhotoUiState,
    canEdit: Boolean,
    onRetryPhoto: (MarkerDetailPhotoUiState) -> Unit,
    onDeletePhoto: (MarkerDetailPhotoUiState) -> Unit
) {
    PoliCard {
        PoliRow(title = photo.label, subtitle = photo.status.label) {
            PoliChip(text = "${(photo.progress * 100).toInt()}%", variant = photo.statusVariant)
        }
        if (photo.status != MarkerDetailPhotoStatus.Attached) {
            PoliProgress(progress = photo.progress)
        }
        if (canEdit) {
            if (photo.status == MarkerDetailPhotoStatus.Failed) {
                PoliButton(
                    text = "업로드 재시도",
                    onClick = { onRetryPhoto(photo) },
                    size = PoliButtonSize.Small,
                    variant = PoliButtonVariant.Secondary
                )
            }
            PoliButton(
                text = "사진 삭제",
                onClick = { onDeletePhoto(photo) },
                size = PoliButtonSize.Small,
                variant = PoliButtonVariant.Secondary
            )
        }
    }
}

@Composable
private fun MarkerMetaCard(state: MarkerDetailUiState) {
    PoliCard {
        Text(text = "메타", style = MaterialTheme.typography.titleMedium)
        PoliRow(title = "작성", subtitle = state.policePhoneLabel)
        PoliRow(title = "계정", subtitle = state.accountLabel)
        PoliRow(title = "위치", subtitle = state.locationLabel)
        PoliRow(title = "시각", subtitle = state.occurredAtLabel)
        PoliRow(title = "버전", subtitle = state.versionLabel)
        PoliRow(
            title = "권한 source",
            subtitle = "SecurityContext.accountId=${state.securityContextAccountId} · createdBy=${state.createdByAccountId}"
        )
    }
}

@Composable
private fun MarkerDetailActions(
    state: MarkerDetailUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRequestDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(PoliDimens.SectionPadding),
        horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
    ) {
        if (state.canEdit) {
            PoliButton(text = "저장", onClick = onSave, modifier = Modifier.weight(1f), enabled = state.canSave)
            if (state.canDelete) {
                PoliButton(
                    text = "삭제",
                    onClick = onRequestDelete,
                    variant = PoliButtonVariant.Danger,
                    enabled = state.canDelete
                )
            }
        } else {
            PoliButton(
                text = "목록으로",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                variant = PoliButtonVariant.Secondary
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(PoliOverlayDim).padding(PoliDimens.SectionPadding)) {
        PoliCard(modifier = Modifier.align(Alignment.Center), strong = true) {
            Text(text = "삭제 확인", style = MaterialTheme.typography.titleMedium, color = PoliEmphasis)
            Text(text = "마커를 삭제합니다. 삭제 후에는 목록에서 숨겨집니다.", style = MaterialTheme.typography.bodyMedium, color = PoliFgSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
                PoliButton(
                    text = "취소",
                    onClick = onDismissDelete,
                    modifier = Modifier.weight(1f),
                    variant = PoliButtonVariant.Secondary
                )
                PoliButton(
                    text = "삭제",
                    onClick = onConfirmDelete,
                    modifier = Modifier.weight(1f),
                    variant = PoliButtonVariant.Danger
                )
            }
        }
    }
}

private val MarkerDetailPhotoUiState.statusVariant: PoliChipVariant
    get() =
        when (status) {
            MarkerDetailPhotoStatus.Attached -> PoliChipVariant.Good
            MarkerDetailPhotoStatus.Attaching -> PoliChipVariant.Outbox
            MarkerDetailPhotoStatus.Deleting -> PoliChipVariant.Warn
            MarkerDetailPhotoStatus.Failed -> PoliChipVariant.Bad
        }

private val MarkerDetailUiState.mutationStatusVariant: PoliChipVariant
    get() =
        when (mutationStatus) {
            MarkerSaveStatus.Editing -> PoliChipVariant.Good
            MarkerSaveStatus.Saving -> PoliChipVariant.Outbox
            MarkerSaveStatus.PendingOutbox -> PoliChipVariant.Warn
            MarkerSaveStatus.Saved -> PoliChipVariant.Good
            MarkerSaveStatus.Failed -> PoliChipVariant.Bad
        }

fun sampleMarkerDetailState(): MarkerDetailUiState = MarkerDetailUiState.ownMarker()

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun MarkerDetailScreenPreview() {
    SuriMapTheme {
        MarkerDetailScreen(
            state = sampleMarkerDetailState(),
            onBack = {},
            onMemoChange = {},
            onSave = {},
            onRequestDelete = {},
            onDismissDelete = {},
            onConfirmDelete = {},
            onAddPhoto = {},
            onRetryPhoto = {},
            onDeletePhoto = {}
        )
    }
}
