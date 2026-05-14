package com.surimap.feature.marker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.surimap.ui.theme.PoliBgSurface
import com.surimap.ui.theme.PoliBorder
import com.surimap.ui.theme.PoliBorderStrong
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgPrimary
import com.surimap.ui.theme.PoliFgSecondary
import com.surimap.ui.theme.PoliOverlayDim
import com.surimap.ui.theme.PoliPrimaryBorder
import com.surimap.ui.theme.PoliPrimaryFillSoft
import com.surimap.ui.theme.PoliWarning
import com.surimap.ui.theme.SuriMapTheme
import java.util.Locale

enum class MarkerType(val apiValue: String, val label: String) {
    CLUE("CLUE", "단서"),
    PERSON_FOUND("PERSON_FOUND", "실종자 발견"),
    FIELD_CONDITION("FIELD_CONDITION", "현장 상태"),
    SUPPORT_REQUEST("SUPPORT_REQUEST", "지원 요청"),
    NOTE("NOTE", "운영 메모")
}

enum class SupportRequestType(val apiValue: String, val label: String) {
    DRONE("DRONE", "드론"),
    POLICE_DOG("POLICE_DOG", "경찰견"),
    OTHER("OTHER", "기타 지원")
}

enum class MarkerSaveStatus {
    Editing,
    Saving,
    PendingOutbox,
    Saved,
    Failed
}

enum class MarkerPhotoStage(val label: String) {
    Selected("선택됨"),
    UploadUrl("upload-url 발급"),
    ObjectStorageUpload("object storage 업로드"),
    Attach("attach")
}

data class MarkerPhotoUiState(
    val fileName: String,
    val stage: MarkerPhotoStage,
    val progress: Float,
    val sizeBytes: Long? = null,
    val retryAvailable: Boolean = false
)

enum class MarkerLocationSource {
    Current,
    Manual
}

data class MarkerLocationUiState(
    val lon: Double,
    val lat: Double
) {
    val isValid: Boolean =
        lon.isFinite() && lat.isFinite() && lon in -180.0..180.0 && lat in -90.0..90.0
}

data class MarkerCreateSheetUiState(
    val selectedType: MarkerType,
    val supportRequestType: SupportRequestType?,
    val memo: String,
    val photoCount: Int,
    val photos: List<MarkerPhotoUiState>,
    val saveStatus: MarkerSaveStatus,
    val selectedLocation: MarkerLocationUiState?,
    val locationSource: MarkerLocationSource,
    val locationLabel: String,
    val createdAtLabel: String,
    val authorLabel: String
) {
    val markerTypePayloadName: String = "type"
    val supportRequestPayloadName: String = "supportRequestType"
    val locationPayloadName: String = "location"
    val manualLocationAdjusted: Boolean = locationSource == MarkerLocationSource.Manual
    val requiresSupportRequestType: Boolean =
        selectedType == MarkerType.SUPPORT_REQUEST && supportRequestType == null
    val maxPhotoCount: Int = MAX_PHOTO_COUNT
    val maxPhotoBytes: Long = MAX_PHOTO_BYTES
    val selectedLocationIsValid: Boolean = selectedLocation?.isValid == true
    val photosWithinSizeLimit: Boolean =
        photos.all { photo -> photo.sizeBytes == null || photo.sizeBytes in 1..maxPhotoBytes }
    val canAttachPhoto: Boolean = photoCount < maxPhotoCount && photos.size < maxPhotoCount
    val canSave: Boolean =
        !requiresSupportRequestType &&
            selectedLocationIsValid &&
            saveStatus != MarkerSaveStatus.Saving &&
            photosWithinSizeLimit
    val opensBlockedOutbox: Boolean = false
    val photoLimitLabel: String = "사진 ${photoCount.coerceAtMost(maxPhotoCount)} / $maxPhotoCount · 파일당 10MB"
    val photoAttachAfterSaveLabel: String = "사진은 마커 저장 후 상세 화면에서 촬영하거나 앨범에서 첨부합니다."
    val photoLimitWarning: String? =
        when {
            !photosWithinSizeLimit -> "사진 파일은 10MB 이하만 첨부할 수 있습니다."
            !canAttachPhoto -> "마커당 사진은 10장까지 첨부할 수 있습니다."
            else -> null
        }

    val statusLabel: String =
        when (saveStatus) {
            MarkerSaveStatus.Editing -> "입력 중"
            MarkerSaveStatus.Saving -> "저장 중"
            MarkerSaveStatus.PendingOutbox -> "오프라인 저장됨 · 전송 대기"
            MarkerSaveStatus.Saved -> "저장 완료"
            MarkerSaveStatus.Failed -> "저장 실패"
        }

    fun visibleText(): List<String> =
        buildList {
            add("마커 생성")
            add(statusLabel)
            add(locationLabel)
            add(createdAtLabel)
            add(authorLabel)
            MarkerType.entries.forEach { type ->
                add(type.label)
                add(type.apiValue)
            }
            if (selectedType == MarkerType.SUPPORT_REQUEST) {
                add("지원 요청 유형")
                SupportRequestType.entries.forEach { type ->
                    add(type.label)
                    add(type.apiValue)
                }
            }
            if (requiresSupportRequestType) {
                add("지원 요청 유형을 선택해야 저장할 수 있습니다.")
            }
            if (memo.isBlank()) {
                add("메모 선택")
            } else {
                add(memo)
            }
            add(photoLimitLabel)
            add(photoAttachAfterSaveLabel)
            photoLimitWarning?.let(::add)
            photos.forEach { photo ->
                add(photo.fileName)
                add(photo.stage.label)
            }
        }

    companion object {
        fun default(
            selectedType: MarkerType = MarkerType.CLUE,
            supportRequestType: SupportRequestType? = null,
            memo: String = ""
        ): MarkerCreateSheetUiState =
            MarkerCreateSheetUiState(
                selectedType = selectedType,
                supportRequestType = supportRequestType,
                memo = memo,
                photoCount = 0,
                photos = emptyList(),
                saveStatus = MarkerSaveStatus.Editing,
                selectedLocation = MarkerLocationUiState(lon = 126.913400, lat = 35.163100),
                locationSource = MarkerLocationSource.Current,
                locationLabel = "현재 위치 · 35.163100, 126.913400",
                createdAtLabel = "기록 시각 · 14:24 자동 입력",
                authorLabel = "작성 · 기동대 1부대 A팀 폴리폰"
            )

        fun offlinePending(): MarkerCreateSheetUiState =
            default(selectedType = MarkerType.NOTE, memo = "북측 능선 재확인 필요")
                .copy(saveStatus = MarkerSaveStatus.PendingOutbox)

        fun photoUploading(): MarkerCreateSheetUiState =
            default(
                selectedType = MarkerType.SUPPORT_REQUEST,
                supportRequestType = SupportRequestType.DRONE,
                memo = "북측 능선 상공 확인 필요"
            ).copy(
                photoCount = 1,
                photos =
                listOf(
                    MarkerPhotoUiState("north-ridge.jpg", MarkerPhotoStage.UploadUrl, progress = 0.2f),
                    MarkerPhotoUiState("north-ridge.jpg", MarkerPhotoStage.ObjectStorageUpload, progress = 0.58f),
                    MarkerPhotoUiState("north-ridge.jpg", MarkerPhotoStage.Attach, progress = 0.82f)
                )
            )

        private const val MAX_PHOTO_COUNT = 10
        private const val MAX_PHOTO_BYTES = 10_485_760L
    }
}

fun MarkerCreateSheetUiState.withCurrentLocation(lon: Double, lat: Double): MarkerCreateSheetUiState =
    copy(
        selectedLocation = MarkerLocationUiState(lon = lon, lat = lat),
        locationSource = MarkerLocationSource.Current,
        locationLabel = markerLocationLabel(prefix = "지도 중심", lon = lon, lat = lat)
    )

fun MarkerCreateSheetUiState.withManualLocation(lon: Double, lat: Double): MarkerCreateSheetUiState =
    copy(
        selectedLocation = MarkerLocationUiState(lon = lon, lat = lat),
        locationSource = MarkerLocationSource.Manual,
        locationLabel = markerLocationLabel(prefix = "수동 조정", lon = lon, lat = lat)
    )

private fun markerLocationLabel(prefix: String, lon: Double, lat: Double): String =
    String.format(Locale.US, "%s · %.6f, %.6f", prefix, lat, lon)

@Composable
fun MarkerCreateBottomSheet(
    state: MarkerCreateSheetUiState,
    onDismiss: () -> Unit,
    onSelectMarkerType: (MarkerType) -> Unit,
    onSelectSupportRequestType: (SupportRequestType) -> Unit,
    onMemoChange: (String) -> Unit,
    onSave: () -> Unit,
    onAdjustLocation: () -> Unit,
    onRetryPhoto: (MarkerPhotoUiState) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(PoliOverlayDim), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 660.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = PoliBgSurface,
            contentColor = PoliFgPrimary,
            border = BorderStroke(1.dp, PoliBorderStrong)
        ) {
            Column(
                modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(PoliDimens.SectionPadding),
                verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
            ) {
                GrabHandle()
                SheetHeader(state = state, onDismiss = onDismiss)
                AutoInfoCard(state = state, onAdjustLocation = onAdjustLocation)
                MarkerTypeGrid(
                    selectedType = state.selectedType,
                    onSelectMarkerType = onSelectMarkerType
                )
                if (state.selectedType == MarkerType.SUPPORT_REQUEST) {
                    SupportRequestSelector(
                        selectedType = state.supportRequestType,
                        onSelectSupportRequestType = onSelectSupportRequestType
                    )
                }
                MemoSection(state = state, onMemoChange = onMemoChange)
                PhotoSection(
                    state = state,
                    onRetryPhoto = onRetryPhoto
                )
                SheetActions(state = state, onDismiss = onDismiss, onSave = onSave)
            }
        }
    }
}

@Composable
private fun GrabHandle() {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.18f).heightIn(min = 6.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = PoliBorderStrong
        ) {}
    }
}

@Composable
private fun SheetHeader(state: MarkerCreateSheetUiState, onDismiss: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)) {
            Text(text = "마커 생성", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "유형 선택만으로 현재 위치와 시각이 자동 입력됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = PoliFgMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        PoliChip(text = state.statusLabel, variant = state.saveStatusVariant)
        PoliButton(text = "닫기", onClick = onDismiss, size = PoliButtonSize.Small, variant = PoliButtonVariant.Secondary)
    }
}

@Composable
private fun AutoInfoCard(state: MarkerCreateSheetUiState, onAdjustLocation: () -> Unit) {
    PoliCard(strong = true) {
        PoliRow(title = "위치", subtitle = state.locationLabel)
        PoliButton(
            text = if (state.manualLocationAdjusted) "지도 중심 재적용" else "위치 조정",
            onClick = onAdjustLocation,
            size = PoliButtonSize.Small,
            variant = PoliButtonVariant.Secondary
        )
        PoliRow(title = "시각", subtitle = state.createdAtLabel)
        PoliRow(title = "작성", subtitle = state.authorLabel)
    }
}

@Composable
private fun MarkerTypeGrid(
    selectedType: MarkerType,
    onSelectMarkerType: (MarkerType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
        Text(text = "마커 유형", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
            MarkerTypeButton(
                type = MarkerType.CLUE,
                selected = selectedType == MarkerType.CLUE,
                onSelect = onSelectMarkerType,
                modifier = Modifier.weight(1f)
            )
            MarkerTypeButton(
                type = MarkerType.PERSON_FOUND,
                selected = selectedType == MarkerType.PERSON_FOUND,
                onSelect = onSelectMarkerType,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
            MarkerTypeButton(
                type = MarkerType.FIELD_CONDITION,
                selected = selectedType == MarkerType.FIELD_CONDITION,
                onSelect = onSelectMarkerType,
                modifier = Modifier.weight(1f)
            )
            MarkerTypeButton(
                type = MarkerType.SUPPORT_REQUEST,
                selected = selectedType == MarkerType.SUPPORT_REQUEST,
                onSelect = onSelectMarkerType,
                modifier = Modifier.weight(1f)
            )
        }
        MarkerTypeButton(
            type = MarkerType.NOTE,
            selected = selectedType == MarkerType.NOTE,
            onSelect = onSelectMarkerType,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MarkerTypeButton(
    type: MarkerType,
    selected: Boolean,
    onSelect: (MarkerType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 76.dp).clickable { onSelect(type) },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) PoliPrimaryFillSoft else PoliBgInput,
        contentColor = if (selected) PoliFgPrimary else PoliFgSecondary,
        border = BorderStroke(1.dp, if (selected) PoliPrimaryBorder else PoliBorder)
    ) {
        Column(
            modifier = Modifier.padding(PoliDimens.Space3),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = type.label, style = MaterialTheme.typography.labelLarge)
            Text(text = type.apiValue, style = MaterialTheme.typography.labelSmall, color = PoliFgMuted)
        }
    }
}

@Composable
private fun SupportRequestSelector(
    selectedType: SupportRequestType?,
    onSelectSupportRequestType: (SupportRequestType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
        Text(text = "지원 요청 유형", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
            SupportRequestType.entries.forEach { type ->
                SupportRequestButton(
                    type = type,
                    selected = type == selectedType,
                    onSelect = onSelectSupportRequestType,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SupportRequestButton(
    type: SupportRequestType,
    selected: Boolean,
    onSelect: (SupportRequestType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 64.dp).clickable { onSelect(type) },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) PoliPrimaryFillSoft else PoliBgInput,
        contentColor = if (selected) PoliFgPrimary else PoliFgSecondary,
        border = BorderStroke(1.dp, if (selected) PoliPrimaryBorder else PoliBorder)
    ) {
        Box(modifier = Modifier.padding(PoliDimens.Space2), contentAlignment = Alignment.Center) {
            Text(text = type.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MemoSection(state: MarkerCreateSheetUiState, onMemoChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
        Text(text = "메모", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.memo,
            onValueChange = onMemoChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
            placeholder = {
                Text(text = "선택 입력", color = PoliFgMuted)
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            minLines = 2,
            shape = MaterialTheme.shapes.medium
        )
    }
}

@Composable
private fun PhotoSection(
    state: MarkerCreateSheetUiState,
    onRetryPhoto: (MarkerPhotoUiState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
            Text(text = state.photoLimitLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        }
        Text(text = state.photoAttachAfterSaveLabel, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
        state.photoLimitWarning?.let { warning ->
            Text(text = warning, style = MaterialTheme.typography.bodyMedium, color = PoliWarning)
        }
        if (state.photos.isEmpty()) {
            PoliCard {
                Text(text = "첨부 없음", style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
            }
        } else {
            state.photos.forEach { photo ->
                PhotoProgressRow(photo = photo, onRetryPhoto = onRetryPhoto)
            }
        }
    }
}

@Composable
private fun PhotoProgressRow(photo: MarkerPhotoUiState, onRetryPhoto: (MarkerPhotoUiState) -> Unit) {
    PoliCard {
        PoliRow(title = photo.fileName, subtitle = photo.stage.label) {
            PoliChip(text = "${(photo.progress * 100).toInt()}%", variant = PoliChipVariant.Outbox)
        }
        PoliProgress(progress = photo.progress)
        if (photo.retryAvailable) {
            PoliButton(
                text = "업로드 재시도",
                onClick = { onRetryPhoto(photo) },
                size = PoliButtonSize.Small,
                variant = PoliButtonVariant.Secondary
            )
        }
    }
}

@Composable
private fun SheetActions(state: MarkerCreateSheetUiState, onDismiss: () -> Unit, onSave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
        if (state.requiresSupportRequestType) {
            Text(
                text = "지원 요청 유형을 선택해야 저장할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = PoliWarning
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
            PoliButton(
                text = "취소",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                variant = PoliButtonVariant.Secondary
            )
            PoliButton(
                text = "추가 정보 저장",
                onClick = onSave,
                modifier = Modifier.weight(1f),
                enabled = state.canSave
            )
        }
    }
}

private val MarkerCreateSheetUiState.saveStatusVariant: PoliChipVariant
    get() =
        when (saveStatus) {
            MarkerSaveStatus.Editing -> PoliChipVariant.Neutral
            MarkerSaveStatus.Saving -> PoliChipVariant.Outbox
            MarkerSaveStatus.PendingOutbox -> PoliChipVariant.Warn
            MarkerSaveStatus.Saved -> PoliChipVariant.Good
            MarkerSaveStatus.Failed -> PoliChipVariant.Bad
        }

fun sampleMarkerCreateSheetState(): MarkerCreateSheetUiState =
    MarkerCreateSheetUiState.default(
        selectedType = MarkerType.SUPPORT_REQUEST,
        supportRequestType = SupportRequestType.DRONE,
        memo = "북측 능선 상공 확인 필요"
    )

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun MarkerCreateBottomSheetPreview() {
    SuriMapTheme {
        MarkerCreateBottomSheet(
            state = sampleMarkerCreateSheetState(),
            onDismiss = {},
            onSelectMarkerType = {},
            onSelectSupportRequestType = {},
            onMemoChange = {},
            onSave = {},
            onAdjustLocation = {},
            onRetryPhoto = {}
        )
    }
}
