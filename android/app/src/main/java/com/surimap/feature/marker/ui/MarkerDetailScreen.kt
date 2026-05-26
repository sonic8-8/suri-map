package com.surimap.feature.marker.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import java.util.Locale
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MarkerDetailPhotoStatus(val label: String) {
    Attached("첨부됨"),
    Attaching("첨부 중"),
    Deleting("삭제 진행 중"),
    Failed("실패")
}

data class MarkerDetailPhotoUiState(
    val photoId: String,
    val label: String,
    val status: MarkerDetailPhotoStatus,
    val progress: Float = 1f,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val attachedAtLabel: String? = null,
    val photoUrl: String? = null,
    val thumbnailUrl: String? = null
) {
    val viewUrl: String? = photoUrl?.takeIf(String::isNotBlank) ?: thumbnailUrl?.takeIf(String::isNotBlank)
    val previewUrl: String? = thumbnailUrl?.takeIf(String::isNotBlank) ?: photoUrl?.takeIf(String::isNotBlank)
    val canOpen: Boolean = !viewUrl.isNullOrBlank()
    val detailLabel: String =
        listOfNotNull(
            status.label,
            contentType?.takeIf(String::isNotBlank),
            sizeBytes?.takeIf { it >= 0L }?.toFileSizeLabel(),
            attachedAtLabel?.takeIf(String::isNotBlank)
        ).joinToString(" · ")
}

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
    val showDeleteConfirm: Boolean,
    val loading: Boolean = false
) {
    val isOwnMarker: Boolean = createdByAccountId == securityContextAccountId
    val canEdit: Boolean = !loading && (canEditByContext ?: (isOwnMarker || canManageAllMarkers))
    val canSave: Boolean = canEdit && version > 0 && mutationStatus != MarkerSaveStatus.Saving
    val canDelete: Boolean = canEdit && version > 0 && mutationStatus != MarkerSaveStatus.Saving
    val longPressDeleteEnabled: Boolean = false

    val permissionLabel: String =
        when {
            loading -> "확인 중"
            canEditByContext == true -> "편집 가능"
            canEditByContext == false -> "읽기 전용"
            isOwnMarker -> "내가 작성"
            canManageAllMarkers -> "편집 가능"
            else -> "읽기 전용"
        }

    val statusLabel: String =
        if (loading) {
            "불러오는 중"
        } else {
            when (mutationStatus) {
                MarkerSaveStatus.Editing -> syncLabel
                MarkerSaveStatus.Saving -> "저장 중"
                MarkerSaveStatus.PendingOutbox -> "오프라인 저장됨 · 전송 대기"
                MarkerSaveStatus.Saved -> "저장 완료"
                MarkerSaveStatus.Failed -> "저장 실패"
            }
        }

    fun visibleText(): List<String> =
        buildList {
            add("마커 상세")
            if (loading) {
                add("마커 정보 불러오는 중")
            }
            add(markerType.label)
            add(title)
            add(memo)
            add(permissionLabel)
            add(policePhoneLabel)
            add(accountLabel)
            add(locationLabel)
            add(occurredAtLabel)
            add(versionLabel)
            add(syncLabel)
            add(statusLabel)
            photos.forEach { photo ->
                add(photo.label)
                add(photo.detailLabel)
                if (photo.canOpen) {
                    add("사진 열기")
                }
            }
            if (canEdit) {
                add("저장")
                add("촬영")
                add("앨범")
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
                createdByAccountId = "acct-person-beta",
                securityContextAccountId = "acct-person-alpha",
                canManageAllMarkers = false,
                policePhoneLabel = "실종팀 작성 단말",
                accountLabel = "실종팀 경감 이지휘"
            )

        fun loading(
            markerId: String,
            markerType: MarkerType = MarkerType.NOTE,
            title: String = "마커 정보"
        ): MarkerDetailUiState =
            base(
                markerId = markerId,
                markerType = markerType,
                title = title,
                memo = "",
                createdByAccountId = "",
                securityContextAccountId = "",
                canManageAllMarkers = false,
                canEditByContext = false,
                policePhoneLabel = "확인 중",
                accountLabel = "확인 중",
                locationLabel = "확인 중",
                occurredAtLabel = "확인 중",
                version = 0,
                versionLabel = "확인 중",
                syncLabel = "불러오는 중",
                mutationStatus = MarkerSaveStatus.Saving,
                photos = emptyList(),
                loading = true
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
                policePhoneLabel = "작성 단말 미확인",
                accountLabel = "계정 미확인",
                locationLabel = "위치 미확인",
                occurredAtLabel = "시각 미확인",
                version = 0,
                versionLabel = "수정 이력 미확인",
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
            policePhoneLabel: String = "기동대 1부대 A팀 작성 단말",
            accountLabel: String = "기동대 1부대 경위 김수색",
            locationLabel: String = "35.163100, 126.913400",
            occurredAtLabel: String = "14:18",
            versionLabel: String = "마지막 수정 14:24",
            syncLabel: String = "동기화",
            mutationStatus: MarkerSaveStatus = MarkerSaveStatus.Editing,
            photos: List<MarkerDetailPhotoUiState> =
                listOf(
                    MarkerDetailPhotoUiState("photo-001", "사진 1", MarkerDetailPhotoStatus.Attached),
                    MarkerDetailPhotoUiState("photo-002", "사진 2", MarkerDetailPhotoStatus.Attached),
                    MarkerDetailPhotoUiState("photo-003", "사진 3", MarkerDetailPhotoStatus.Attached)
                ),
            showDeleteConfirm: Boolean = false,
            loading: Boolean = false
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
                showDeleteConfirm = showDeleteConfirm,
                loading = loading
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
    onCapturePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRetryPhoto: (MarkerDetailPhotoUiState) -> Unit,
    onOpenPhoto: (MarkerDetailPhotoUiState) -> Unit,
    closeLabel: String = "목록으로",
    modalPresentation: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(PoliBgBase)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (modalPresentation) {
                MarkerDetailModalHeader(state = state, onClose = onBack)
            } else {
                PoliAppBar(
                    title = "마커 상세",
                    modifier = Modifier.statusBarsPadding(),
                    subtitle = "${state.markerType.label} · ${state.permissionLabel}",
                    showBack = true,
                    onBack = onBack,
                    trailing = {
                        PoliChip(text = state.statusLabel, variant = state.mutationStatusVariant)
                    }
                )
            }
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PoliDimens.SectionPadding),
                verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
            ) {
                if (state.loading) {
                    MarkerDetailLoadingContent(state = state)
                } else {
                    MarkerSummaryCard(state = state, showPermission = !modalPresentation)
                    state.statusNoticeLabel?.let { notice ->
                        PoliBanner(
                            text = notice,
                            variant = if (state.mutationStatus == MarkerSaveStatus.Failed) PoliBannerVariant.Bad else PoliBannerVariant.Info
                        )
                    }
                    if (!modalPresentation && !state.canEdit) {
                        PoliBanner(
                            text = "읽기 전용 — 본인이 생성한 마커만 수정·삭제 가능합니다.",
                            variant = PoliBannerVariant.Warn
                        )
                    }
                    MarkerMemoCard(state = state, onMemoChange = onMemoChange, showEditHint = !modalPresentation)
                    MarkerPhotosCard(
                        state = state,
                        onCapturePhoto = onCapturePhoto,
                        onPickPhoto = onPickPhoto,
                        onRetryPhoto = onRetryPhoto,
                        onOpenPhoto = onOpenPhoto
                    )
                    MarkerMetaCard(state = state)
                }
            }
            if (!state.loading && (state.canEdit || !modalPresentation)) {
                MarkerDetailActions(
                    state = state,
                    closeLabel = closeLabel,
                    onBack = onBack,
                    onSave = onSave,
                    onRequestDelete = onRequestDelete
                )
            }
        }

        if (!state.loading && state.showDeleteConfirm) {
            DeleteConfirmDialog(
                onDismissDelete = onDismissDelete,
                onConfirmDelete = onConfirmDelete
            )
        }
    }
}

@Composable
private fun MarkerDetailModalHeader(state: MarkerDetailUiState, onClose: () -> Unit) {
    val subtitle =
        listOfNotNull(
            state.markerType.label,
            state.title
                .takeIf(String::isNotBlank)
                ?.takeIf { title -> title != state.markerType.label }
        ).joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(PoliDimens.SectionPadding),
        horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)) {
            Text(text = "마커 상세", style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = PoliFgMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        PoliButton(text = "닫기", onClick = onClose, size = PoliButtonSize.Small, variant = PoliButtonVariant.Secondary)
    }
}

@Composable
private fun MarkerDetailLoadingContent(state: MarkerDetailUiState) {
    MarkerSummaryCard(state = state, showPermission = false)
    PoliBanner(
        text = "마커 정보 불러오는 중",
        variant = PoliBannerVariant.Info
    )
    MarkerDetailSkeletonCard(title = "메모", lineCount = 2)
    MarkerDetailSkeletonCard(title = "사진", lineCount = 1)
    MarkerDetailSkeletonCard(title = "기록 정보", lineCount = 3)
}

@Composable
private fun MarkerDetailSkeletonCard(title: String, lineCount: Int) {
    PoliCard {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        repeat(lineCount) { index ->
            Surface(
                modifier =
                Modifier
                    .fillMaxWidth(if (index == lineCount - 1) 0.68f else 1f)
                    .heightIn(min = if (index == 0) 44.dp else 28.dp),
                shape = MaterialTheme.shapes.medium,
                color = PoliBgInput,
                border = BorderStroke(1.dp, PoliBorder)
            ) {}
        }
    }
}

@Composable
private fun MarkerSummaryCard(state: MarkerDetailUiState, showPermission: Boolean) {
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
                Text(text = state.markerType.label, style = MaterialTheme.typography.labelMedium, color = PoliFgMuted)
                Text(text = state.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showPermission) {
                    Text(text = state.permissionLabel, style = MaterialTheme.typography.bodyMedium, color = PoliFgSecondary)
                }
            }
        }
    }
}

@Composable
private fun MarkerMemoCard(state: MarkerDetailUiState, onMemoChange: (String) -> Unit, showEditHint: Boolean) {
    PoliCard {
        Text(text = "메모", style = MaterialTheme.typography.titleMedium)
        if (state.canEdit) {
            OutlinedTextField(
                value = state.memo,
                onValueChange = onMemoChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 3,
                shape = MaterialTheme.shapes.medium
            )
            if (showEditHint) {
                Text(
                    text = "${state.memo.length} / 500 · 내가 작성한 마커만 수정 가능",
                    style = MaterialTheme.typography.bodySmall,
                    color = PoliFgMuted
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = PoliBgInput,
                contentColor = PoliFgPrimary,
                border = BorderStroke(1.dp, PoliBorder)
            ) {
                Text(
                    text = state.memo.ifBlank { "메모 없음" },
                    modifier = Modifier.fillMaxWidth().padding(PoliDimens.Space4),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PoliFgPrimary
                )
            }
        }
    }
}

@Composable
private fun MarkerPhotosCard(
    state: MarkerDetailUiState,
    onCapturePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRetryPhoto: (MarkerDetailPhotoUiState) -> Unit,
    onOpenPhoto: (MarkerDetailPhotoUiState) -> Unit
) {
    PoliCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
            Text(text = "사진 ${state.photos.size}장", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (state.canEdit) {
                PoliButton(text = "촬영", onClick = onCapturePhoto, size = PoliButtonSize.Small, variant = PoliButtonVariant.Secondary)
                PoliButton(text = "앨범", onClick = onPickPhoto, size = PoliButtonSize.Small, variant = PoliButtonVariant.Secondary)
            }
        }
        state.photos.forEach { photo ->
            PhotoDetailRow(
                photo = photo,
                canEdit = state.canEdit,
                onRetryPhoto = onRetryPhoto,
                onOpenPhoto = onOpenPhoto
            )
        }
    }
}

@Composable
private fun PhotoDetailRow(
    photo: MarkerDetailPhotoUiState,
    canEdit: Boolean,
    onRetryPhoto: (MarkerDetailPhotoUiState) -> Unit,
    onOpenPhoto: (MarkerDetailPhotoUiState) -> Unit
) {
    PoliCard {
        if (photo.status == MarkerDetailPhotoStatus.Attached) {
            PoliRow(title = photo.label, subtitle = photo.fieldDetailLabel)
        } else {
            PoliRow(title = photo.label, subtitle = photo.fieldDetailLabel) {
                PoliChip(text = "${(photo.progress * 100).toInt()}%", variant = photo.statusVariant)
            }
        }
        if (photo.status != MarkerDetailPhotoStatus.Attached) {
            PoliProgress(progress = photo.progress)
        }
        MarkerPhotoPreview(photo = photo)
        if (photo.canOpen) {
            PoliButton(
                text = "사진 열기",
                onClick = { onOpenPhoto(photo) },
                size = PoliButtonSize.Small,
                variant = PoliButtonVariant.Secondary
            )
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
        }
    }
}

@Composable
private fun MarkerPhotoPreview(photo: MarkerDetailPhotoUiState) {
    val previewUrl = photo.previewUrl?.takeIf(String::isNotBlank) ?: return
    val bitmapState =
        produceState<Bitmap?>(initialValue = null, previewUrl) {
            value = loadMarkerPhotoBitmap(previewUrl)
        }
    val bitmap = bitmapState.value
    if (bitmap == null) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 144.dp),
            shape = MaterialTheme.shapes.medium,
            color = PoliBgInput,
            border = BorderStroke(1.dp, PoliBorder)
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(PoliDimens.Space4), contentAlignment = Alignment.Center) {
                Text(text = "사진 불러오는 중", style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
            }
        }
        return
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "${photo.label} 미리보기",
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 260.dp)
            .clip(MaterialTheme.shapes.medium),
        contentScale = ContentScale.Crop
    )
}

private fun Long.toFileSizeLabel(): String =
    when {
        this >= 1_048_576L -> String.format(Locale.US, "%.1fMB", this / 1_048_576.0)
        this >= 1_024L -> String.format(Locale.US, "%.1fKB", this / 1_024.0)
        else -> "${this}B"
    }

private suspend fun loadMarkerPhotoBitmap(url: String): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            URL(url).openStream().use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }

@Composable
private fun MarkerMetaCard(state: MarkerDetailUiState) {
    PoliCard {
        Text(text = "기록 정보", style = MaterialTheme.typography.titleMedium)
        MarkerDetailInfoRow(label = "기록자", value = state.accountLabel)
        MarkerDetailInfoRow(label = "기록 시각", value = state.occurredAtLabel)
        MarkerDetailInfoRow(label = "위치 좌표", value = state.locationLabel)
        MarkerDetailInfoRow(label = "최근 수정", value = state.versionLabel)
    }
}

@Composable
private fun MarkerDetailInfoRow(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = PoliBgInput,
        contentColor = PoliFgPrimary,
        border = BorderStroke(1.dp, PoliBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PoliDimens.Space3),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = PoliFgMuted)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, color = PoliFgPrimary)
        }
    }
}

@Composable
private fun MarkerDetailActions(
    state: MarkerDetailUiState,
    closeLabel: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRequestDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(PoliDimens.SectionPadding),
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
                text = closeLabel,
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

private val MarkerDetailPhotoUiState.fieldDetailLabel: String
    get() =
        when (status) {
            MarkerDetailPhotoStatus.Attached ->
                attachedAtLabel
                    ?.takeIf(String::isNotBlank)
                    ?.toCompactPhotoTimeLabel()
                    ?.let { "첨부 완료 · $it" }
                    ?: "첨부 완료"
            MarkerDetailPhotoStatus.Attaching -> "첨부 중"
            MarkerDetailPhotoStatus.Deleting -> "삭제 중"
            MarkerDetailPhotoStatus.Failed -> "첨부 실패"
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

private val MarkerDetailUiState.statusNoticeLabel: String?
    get() =
        when (mutationStatus) {
            MarkerSaveStatus.Editing -> null
            MarkerSaveStatus.Saving -> "저장 중입니다."
            MarkerSaveStatus.PendingOutbox -> "저장했습니다. 연결되면 자동 전송됩니다."
            MarkerSaveStatus.Saved -> "저장했습니다."
            MarkerSaveStatus.Failed -> "저장하지 못했습니다. 다시 시도하세요."
        }

private fun String.toCompactPhotoTimeLabel(): String {
    if (length >= 16 && this[4] == '-' && this[7] == '-' && this[10] == 'T') {
        return "${substring(5, 7)}/${substring(8, 10)} ${substring(11, 16)}"
    }
    return replace('T', ' ').removeSuffix("Z")
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
            onCapturePhoto = {},
            onPickPhoto = {},
            onRetryPhoto = {},
            onOpenPhoto = {}
        )
    }
}
