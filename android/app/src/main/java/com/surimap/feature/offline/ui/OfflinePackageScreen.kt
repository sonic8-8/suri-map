package com.surimap.feature.offline.ui

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
import androidx.compose.runtime.LaunchedEffect
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
import com.surimap.ui.components.PoliProgress
import com.surimap.ui.components.PoliRow
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgSecondary
import com.surimap.ui.theme.SuriMapTheme

enum class OfflinePackageDownloadStatus {
    ManifestCurrent,
    ManifestChanged,
    Downloading,
    AutoRetryInProgress,
    AutoRetryExhausted,
    Partial,
    Ready,
    Offline,
    Stale,
    PermissionDenied
}

data class OfflinePackageItemUiState(
    val label: String,
    val progress: Float,
    val statusLabel: String = "대기",
    val failed: Boolean = false
)

data class OfflinePackageUiState(
    val incidentTitle: String,
    val manifestRevision: Int,
    val knownManifestRevision: Int?,
    val status: OfflinePackageDownloadStatus,
    val packageItems: List<OfflinePackageItemUiState>,
    val readyForOfflineUse: Boolean,
    val autoOpenSearchMap: Boolean,
    val requiresLimitedOpenConfirmation: Boolean,
    val shouldDownloadPackage: Boolean,
    val canManualRetry: Boolean,
    val retryLabel: String?,
    val message: String
) {
    val overallProgress: Float =
        if (packageItems.isEmpty()) {
            0f
        } else {
            packageItems.map(OfflinePackageItemUiState::progress).average().toFloat().coerceIn(0f, 1f)
        }

    val progressLabel: String = "${(overallProgress * 100).toInt()}%"

    fun visibleText(): List<String> =
        buildList {
            add(incidentTitle)
            add("manifest rev $manifestRevision")
            knownManifestRevision?.let { known ->
                if (known != manifestRevision) {
                    add("manifest rev $known -> $manifestRevision")
                }
            }
            add(message)
            add(progressLabel)
            retryLabel?.let(::add)
            packageItems.forEach { item ->
                add(item.label)
                add(item.statusLabel)
            }
            if (requiresLimitedOpenConfirmation) {
                add("제한 안내 후 열기")
            }
            if (canManualRetry) {
                add("수동 재시도")
            }
        }

    companion object {
        fun defaultPackageItems(): List<OfflinePackageItemUiState> =
            listOf(
                OfflinePackageItemUiState(label = "사건 메타", progress = 0f),
                OfflinePackageItemUiState(label = "실종자", progress = 0f),
                OfflinePackageItemUiState(label = "OP", progress = 0f),
                OfflinePackageItemUiState(label = "구역", progress = 0f),
                OfflinePackageItemUiState(label = "마커", progress = 0f),
                OfflinePackageItemUiState(label = "전체 수색 구역", progress = 0f),
                OfflinePackageItemUiState(label = "타일", progress = 0f)
            )

        fun loading(incidentTitle: String = "선택한 사건"): OfflinePackageUiState =
            OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = 0,
                knownManifestRevision = null,
                status = OfflinePackageDownloadStatus.Downloading,
                packageItems = defaultPackageItems(),
                readyForOfflineUse = false,
                autoOpenSearchMap = false,
                requiresLimitedOpenConfirmation = false,
                shouldDownloadPackage = false,
                canManualRetry = false,
                retryLabel = null,
                message = "오프라인 패키지 manifest를 확인하고 있습니다."
            )

        fun manifestLoaded(
            incidentTitle: String,
            manifestRevision: Int,
            knownManifestRevision: Int?,
            packageItems: List<OfflinePackageItemUiState>
        ): OfflinePackageUiState {
            val items = packageItems.takeIf(List<OfflinePackageItemUiState>::isNotEmpty) ?: defaultPackageItems()
            val hasFailedItem = items.any(OfflinePackageItemUiState::failed)
            val allItemsComplete = items.all { item -> item.progress >= 1f && !item.failed }
            val status =
                when {
                    hasFailedItem -> OfflinePackageDownloadStatus.Partial
                    knownManifestRevision == manifestRevision && allItemsComplete ->
                        OfflinePackageDownloadStatus.ManifestCurrent
                    else -> OfflinePackageDownloadStatus.ManifestChanged
                }
            val revisionMessage =
                knownManifestRevision
                    ?.takeIf { known -> known != manifestRevision }
                    ?.let { known -> "manifest rev $known -> $manifestRevision 변경을 확인했습니다." }
                    ?: "manifest rev $manifestRevision 정보를 확인했습니다."
            return OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = manifestRevision,
                knownManifestRevision = knownManifestRevision,
                status = status,
                packageItems = items,
                readyForOfflineUse = false,
                autoOpenSearchMap = false,
                requiresLimitedOpenConfirmation = hasFailedItem,
                shouldDownloadPackage = !allItemsComplete || knownManifestRevision != manifestRevision,
                canManualRetry = hasFailedItem,
                retryLabel = null,
                message =
                if (hasFailedItem) {
                    "$revisionMessage 실패 항목이 남아 오프라인 사용 준비 완료로 표시하지 않습니다."
                } else {
                    "$revisionMessage 패키지 설치 상태와 구분해 적재를 진행합니다."
                }
            )
        }

        fun offline(incidentTitle: String = "선택한 사건"): OfflinePackageUiState =
            OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = 0,
                knownManifestRevision = null,
                status = OfflinePackageDownloadStatus.Offline,
                packageItems = defaultPackageItems(),
                readyForOfflineUse = false,
                autoOpenSearchMap = false,
                requiresLimitedOpenConfirmation = false,
                shouldDownloadPackage = false,
                canManualRetry = false,
                retryLabel = null,
                message = "내부망 연결이 없어 오프라인 패키지 manifest를 확인하지 못했습니다."
            )

        fun permissionDenied(incidentTitle: String = "선택한 사건"): OfflinePackageUiState =
            OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = 0,
                knownManifestRevision = null,
                status = OfflinePackageDownloadStatus.PermissionDenied,
                packageItems = defaultPackageItems(),
                readyForOfflineUse = false,
                autoOpenSearchMap = false,
                requiresLimitedOpenConfirmation = false,
                shouldDownloadPackage = false,
                canManualRetry = false,
                retryLabel = null,
                message = "폴리폰 배정 정보를 확인할 수 없어 오프라인 패키지를 받을 수 없습니다."
            )

        fun unavailable(
            incidentTitle: String = "선택한 사건",
            manifestRevision: Int = 0
        ): OfflinePackageUiState =
            OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = manifestRevision,
                knownManifestRevision = null,
                status = OfflinePackageDownloadStatus.AutoRetryExhausted,
                packageItems = defaultPackageItems(),
                readyForOfflineUse = false,
                autoOpenSearchMap = false,
                requiresLimitedOpenConfirmation = false,
                shouldDownloadPackage = false,
                canManualRetry = true,
                retryLabel = null,
                message = "오프라인 패키지 manifest를 불러오지 못했습니다. 내부망 확인 후 다시 시도하세요."
            )

        fun manifestCurrent(
            incidentTitle: String,
            manifestRevision: Int,
            knownManifestRevision: Int
        ): OfflinePackageUiState =
            OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = manifestRevision,
                knownManifestRevision = knownManifestRevision,
                status = OfflinePackageDownloadStatus.ManifestCurrent,
                packageItems = completeItems(),
                readyForOfflineUse = true,
                autoOpenSearchMap = true,
                requiresLimitedOpenConfirmation = false,
                shouldDownloadPackage = false,
                canManualRetry = false,
                retryLabel = null,
                message = "manifest 변경이 없어 수색 지도로 이동합니다."
            )

        fun manifestChanged(
            incidentTitle: String,
            manifestRevision: Int,
            knownManifestRevision: Int
        ): OfflinePackageUiState =
            OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = manifestRevision,
                knownManifestRevision = knownManifestRevision,
                status = OfflinePackageDownloadStatus.ManifestChanged,
                packageItems = defaultPackageItems(),
                readyForOfflineUse = false,
                autoOpenSearchMap = false,
                requiresLimitedOpenConfirmation = false,
                shouldDownloadPackage = true,
                canManualRetry = false,
                retryLabel = null,
                message = "manifest rev $knownManifestRevision -> $manifestRevision 변경을 확인했습니다. 패키지를 다시 받습니다."
            )

        fun downloading(
            incidentTitle: String,
            manifestRevision: Int,
            completedItems: Int = 3
        ): OfflinePackageUiState {
            val items = defaultPackageItems().mapIndexed { index, item ->
                when {
                    index < completedItems -> item.copy(progress = 1f, statusLabel = "완료")
                    index == completedItems -> item.copy(progress = 0.45f, statusLabel = "다운로드 중")
                    else -> item
                }
            }
            return OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = manifestRevision,
                knownManifestRevision = manifestRevision - 1,
                status = OfflinePackageDownloadStatus.Downloading,
                packageItems = items,
                readyForOfflineUse = false,
                autoOpenSearchMap = false,
                requiresLimitedOpenConfirmation = false,
                shouldDownloadPackage = true,
                canManualRetry = false,
                retryLabel = null,
                message = "사건 메타, 실종자, OP, 구역, 마커, 전체 수색 구역, 타일을 순서대로 적재합니다."
            )
        }

        fun ready(incidentTitle: String, manifestRevision: Int): OfflinePackageUiState =
            OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = manifestRevision,
                knownManifestRevision = manifestRevision,
                status = OfflinePackageDownloadStatus.Ready,
                packageItems = completeItems(),
                readyForOfflineUse = true,
                autoOpenSearchMap = true,
                requiresLimitedOpenConfirmation = false,
                shouldDownloadPackage = false,
                canManualRetry = false,
                retryLabel = null,
                message = "100% 완료되었습니다. 오프라인 사용 준비가 끝나 수색 지도로 이동합니다."
            )

        fun partial(
            incidentTitle: String,
            manifestRevision: Int,
            failedItemLabel: String
        ): OfflinePackageUiState =
            OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = manifestRevision,
                knownManifestRevision = manifestRevision,
                status = OfflinePackageDownloadStatus.Partial,
                packageItems = itemsWithFailure(failedItemLabel),
                readyForOfflineUse = false,
                autoOpenSearchMap = false,
                requiresLimitedOpenConfirmation = true,
                shouldDownloadPackage = false,
                canManualRetry = false,
                retryLabel = "자동 재시도 3/3",
                message = "지도 사용 제한이 있습니다. 실패 항목이 남아 오프라인 사용 준비 완료로 표시하지 않습니다."
            )

        fun autoRetry(
            incidentTitle: String,
            manifestRevision: Int,
            retryAttempt: Int
        ): OfflinePackageUiState =
            OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = manifestRevision,
                knownManifestRevision = manifestRevision,
                status = OfflinePackageDownloadStatus.AutoRetryInProgress,
                packageItems = itemsWithFailure("타일"),
                readyForOfflineUse = false,
                autoOpenSearchMap = false,
                requiresLimitedOpenConfirmation = false,
                shouldDownloadPackage = true,
                canManualRetry = false,
                retryLabel = "자동 재시도 ${retryAttempt.coerceIn(1, MAX_AUTO_RETRY)}/$MAX_AUTO_RETRY",
                message = "네트워크 복구를 기다리며 실패 항목만 자동 재시도합니다."
            )

        fun retryExhausted(incidentTitle: String, manifestRevision: Int): OfflinePackageUiState =
            OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = manifestRevision,
                knownManifestRevision = manifestRevision,
                status = OfflinePackageDownloadStatus.AutoRetryExhausted,
                packageItems = itemsWithFailure("타일"),
                readyForOfflineUse = false,
                autoOpenSearchMap = false,
                requiresLimitedOpenConfirmation = false,
                shouldDownloadPackage = true,
                canManualRetry = true,
                retryLabel = null,
                message = "자동 재시도 3회가 모두 실패했습니다. 네트워크 확인 또는 IT 부서 문의가 필요합니다."
            )

        fun stale(incidentTitle: String, manifestRevision: Int): OfflinePackageUiState =
            OfflinePackageUiState(
                incidentTitle = incidentTitle,
                manifestRevision = manifestRevision,
                knownManifestRevision = manifestRevision - 1,
                status = OfflinePackageDownloadStatus.Stale,
                packageItems = defaultPackageItems(),
                readyForOfflineUse = false,
                autoOpenSearchMap = false,
                requiresLimitedOpenConfirmation = false,
                shouldDownloadPackage = true,
                canManualRetry = true,
                retryLabel = null,
                message = "전체 수색 구역이 변경되었습니다. 다시 다운로드하세요."
            )

        private fun completeItems(): List<OfflinePackageItemUiState> =
            defaultPackageItems().map { it.copy(progress = 1f, statusLabel = "완료") }

        private fun itemsWithFailure(failedItemLabel: String): List<OfflinePackageItemUiState> =
            defaultPackageItems().map { item ->
                if (item.label == failedItemLabel) {
                    item.copy(progress = 0.25f, statusLabel = "실패", failed = true)
                } else {
                    item.copy(progress = 1f, statusLabel = "완료")
                }
            }

        private const val MAX_AUTO_RETRY = 3
    }
}

@Composable
fun OfflinePackageScreen(
    state: OfflinePackageUiState,
    onBack: () -> Unit,
    onOpenSearchMap: () -> Unit,
    onRetryFailedItems: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.autoOpenSearchMap, state.readyForOfflineUse, state.status) {
        if (state.autoOpenSearchMap && state.readyForOfflineUse) {
            onOpenSearchMap()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PoliAppBar(
            title = "오프라인 패키지",
            subtitle = "${state.incidentTitle} · manifest rev ${state.manifestRevision}",
            showBack = true,
            onBack = onBack,
            trailing = {
                PoliChip(text = state.statusLabel, variant = state.statusVariant)
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PoliDimens.SectionPadding),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
        ) {
            StatusBanner(state)
            ProgressCard(state)
            PackageSequenceCard(state)
        }

        ActionBar(
            state = state,
            onBack = onBack,
            onOpenSearchMap = onOpenSearchMap,
            onRetryFailedItems = onRetryFailedItems
        )
    }
}

@Composable
private fun StatusBanner(state: OfflinePackageUiState) {
    val variant =
        when (state.status) {
            OfflinePackageDownloadStatus.Partial,
            OfflinePackageDownloadStatus.Offline,
            OfflinePackageDownloadStatus.Stale -> PoliBannerVariant.Warn
            OfflinePackageDownloadStatus.AutoRetryExhausted,
            OfflinePackageDownloadStatus.PermissionDenied -> PoliBannerVariant.Bad
            else -> PoliBannerVariant.Info
        }
    PoliBanner(text = state.message, variant = variant)
}

@Composable
private fun ProgressCard(state: OfflinePackageUiState) {
    PoliCard(strong = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
                Text(text = state.progressLabel, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = if (state.readyForOfflineUse) "오프라인 사용 준비 완료" else "오프라인 사용 준비 중",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PoliFgMuted
                )
            }
            state.retryLabel?.let { retryLabel ->
                PoliChip(text = retryLabel, variant = PoliChipVariant.Warn)
            }
        }
        PoliProgress(progress = state.overallProgress)
        ManifestComparison(state)
    }
}

@Composable
private fun ManifestComparison(state: OfflinePackageUiState) {
    val knownRevision = state.knownManifestRevision
    val text =
        when {
            knownRevision == null -> "이전 manifest revision 없음"
            knownRevision == state.manifestRevision -> "manifest rev ${state.manifestRevision} 최신"
            else -> "manifest rev $knownRevision -> ${state.manifestRevision}"
        }
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = PoliFgSecondary)
}

@Composable
private fun PackageSequenceCard(state: OfflinePackageUiState) {
    PoliCard {
        Text(text = "다운로드 순서", style = MaterialTheme.typography.titleMedium)
        state.packageItems.forEach { item ->
            PoliRow(title = item.label, subtitle = item.statusLabel) {
                PoliChip(text = "${(item.progress * 100).toInt()}%", variant = item.variant)
            }
        }
    }
}

@Composable
private fun ActionBar(
    state: OfflinePackageUiState,
    onBack: () -> Unit,
    onOpenSearchMap: () -> Unit,
    onRetryFailedItems: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(PoliDimens.SectionPadding),
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
    ) {
        if (state.requiresLimitedOpenConfirmation) {
            PoliButton(text = "제한 안내 후 열기", onClick = onOpenSearchMap, modifier = Modifier.fillMaxWidth())
        }
        if (state.canManualRetry) {
            PoliButton(text = "수동 재시도", onClick = onRetryFailedItems, modifier = Modifier.fillMaxWidth())
        }
        PoliButton(
            text = "사건 선택으로 돌아가기",
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            variant = PoliButtonVariant.Secondary
        )
    }
}

private val OfflinePackageUiState.statusLabel: String
    get() =
        when (status) {
            OfflinePackageDownloadStatus.ManifestCurrent -> "최신"
            OfflinePackageDownloadStatus.ManifestChanged -> "변경 감지"
            OfflinePackageDownloadStatus.Downloading -> "다운로드"
            OfflinePackageDownloadStatus.AutoRetryInProgress -> "자동 재시도"
            OfflinePackageDownloadStatus.AutoRetryExhausted -> "재시도 필요"
            OfflinePackageDownloadStatus.Partial -> "부분 성공"
            OfflinePackageDownloadStatus.Ready -> "준비 완료"
            OfflinePackageDownloadStatus.Offline -> "오프라인"
            OfflinePackageDownloadStatus.Stale -> "만료"
            OfflinePackageDownloadStatus.PermissionDenied -> "권한 없음"
        }

private val OfflinePackageUiState.statusVariant: PoliChipVariant
    get() =
        when (status) {
            OfflinePackageDownloadStatus.ManifestCurrent,
            OfflinePackageDownloadStatus.Ready -> PoliChipVariant.Good
            OfflinePackageDownloadStatus.ManifestChanged,
            OfflinePackageDownloadStatus.Downloading,
            OfflinePackageDownloadStatus.AutoRetryInProgress,
            OfflinePackageDownloadStatus.Partial,
            OfflinePackageDownloadStatus.Offline,
            OfflinePackageDownloadStatus.Stale -> PoliChipVariant.Warn
            OfflinePackageDownloadStatus.AutoRetryExhausted,
            OfflinePackageDownloadStatus.PermissionDenied -> PoliChipVariant.Bad
        }

private val OfflinePackageItemUiState.variant: PoliChipVariant
    get() =
        when {
            failed -> PoliChipVariant.Bad
            progress >= 1f -> PoliChipVariant.Good
            progress > 0f -> PoliChipVariant.Warn
            else -> PoliChipVariant.Neutral
        }

fun sampleOfflinePackageState(): OfflinePackageUiState =
    OfflinePackageUiState.downloading(
        incidentTitle = "광주 북구 산악 실종",
        manifestRevision = 18,
        completedItems = 4
    )

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun OfflinePackageScreenPreview() {
    SuriMapTheme {
        OfflinePackageScreen(
            state = sampleOfflinePackageState(),
            onBack = {},
            onOpenSearchMap = {},
            onRetryFailedItems = {}
        )
    }
}
