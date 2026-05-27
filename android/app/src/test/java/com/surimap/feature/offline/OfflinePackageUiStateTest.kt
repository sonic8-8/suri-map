package com.surimap.feature.offline

import com.surimap.feature.offline.ui.OfflinePackageDownloadStatus
import com.surimap.feature.offline.ui.OfflinePackageItemUiState
import com.surimap.feature.offline.ui.OfflinePackageUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePackageUiStateTest {

    @Test
    fun defaultSequenceContainsAllRequiredPackageItems() {
        val labels = OfflinePackageUiState.defaultPackageItems().map { it.label }

        assertEquals(
            listOf("사건 정보", "실종자 정보", "수색 차수", "담당 구역", "마커", "전체 수색 구역", "타일"),
            labels
        )
    }

    @Test
    fun readyStateDoesNotOpenSearchMapAutomaticallyWhenPackageIsFullyReady() {
        val ready = OfflinePackageUiState.ready(
            incidentTitle = "광주 북구 산악 실종",
            manifestRevision = 17
        )

        assertEquals(OfflinePackageDownloadStatus.Ready, ready.status)
        assertEquals(1f, ready.overallProgress)
        assertTrue(ready.readyForOfflineUse)
        assertFalse(ready.autoOpenSearchMap)
        assertFalse(ready.requiresLimitedOpenConfirmation)
        assertTrue(ready.canOpenSearchMap)
        assertTrue(ready.visibleText().any { it.contains("현장 기록 열기") })
    }

    @Test
    fun partialStateRequiresLimitedOpenConfirmationAndShowsRiskCopy() {
        val partial = OfflinePackageUiState.partial(
            incidentTitle = "광주 북구 산악 실종",
            manifestRevision = 17,
            failedItemLabel = "타일"
        )

        assertEquals(OfflinePackageDownloadStatus.Partial, partial.status)
        assertFalse(partial.readyForOfflineUse)
        assertFalse(partial.autoOpenSearchMap)
        assertTrue(partial.requiresLimitedOpenConfirmation)
        assertTrue(partial.visibleText().any { it.contains("오프라인 지도 준비") })
        assertTrue(partial.visibleText().any { it.contains("지도 준비 전 현장 기록 열기") })
    }

    @Test
    fun manifestComparisonShowsWhetherPackageMustDownload() {
        val current = OfflinePackageUiState.manifestCurrent(
            incidentTitle = "광주 북구 산악 실종",
            manifestRevision = 17,
            knownManifestRevision = 17
        )
        val changed = OfflinePackageUiState.manifestChanged(
            incidentTitle = "광주 북구 산악 실종",
            manifestRevision = 18,
            knownManifestRevision = 17
        )

        assertFalse(current.shouldDownloadPackage)
        assertFalse(current.autoOpenSearchMap)
        assertTrue(current.visibleText().any { it.contains("지도 데이터 버전 17") })
        assertTrue(changed.shouldDownloadPackage)
        assertFalse(changed.autoOpenSearchMap)
        assertTrue(changed.visibleText().any { it.contains("지도 데이터 버전 17 -> 18") })
    }

    @Test
    fun manifestLoadedDoesNotMarkPackageReadyWithoutInstallationStatus() {
        val state =
            OfflinePackageUiState.manifestLoaded(
                incidentTitle = "광주 북구 산악 실종",
                manifestRevision = 18,
                knownManifestRevision = null,
                packageItems =
                listOf(
                    OfflinePackageItemUiState(label = "사건 정보", progress = 1f, statusLabel = "완료"),
                    OfflinePackageItemUiState(label = "타일", progress = 0f, statusLabel = "대기")
                )
            )

        assertEquals(OfflinePackageDownloadStatus.ManifestChanged, state.status)
        assertFalse(state.readyForOfflineUse)
        assertFalse(state.autoOpenSearchMap)
        assertTrue(state.requiresLimitedOpenConfirmation)
        assertTrue(state.shouldDownloadPackage)
        assertTrue(state.visibleText().any { it.contains("단말 준비 상태와 구분") })
        assertTrue(state.visibleText().any { it.contains("지도 준비 전 현장 기록 열기") })
    }

    @Test
    fun packageUnavailableStatesDoNotAutoOpenButAllowLimitedSearchMapEntry() {
        val offline = OfflinePackageUiState.offline(incidentTitle = "inc-001")
        val permissionDenied = OfflinePackageUiState.permissionDenied(incidentTitle = "inc-001")
        val unavailable = OfflinePackageUiState.unavailable(incidentTitle = "inc-001")
        val searchAreaPending = OfflinePackageUiState.searchAreaPending(incidentTitle = "inc-001")

        assertEquals(OfflinePackageDownloadStatus.Offline, offline.status)
        assertEquals(OfflinePackageDownloadStatus.PermissionDenied, permissionDenied.status)
        assertEquals(OfflinePackageDownloadStatus.AutoRetryExhausted, unavailable.status)
        assertEquals(OfflinePackageDownloadStatus.SearchAreaPending, searchAreaPending.status)
        assertFalse(offline.shouldDownloadPackage)
        assertFalse(permissionDenied.shouldDownloadPackage)
        assertFalse(searchAreaPending.shouldDownloadPackage)
        assertFalse(offline.autoOpenSearchMap)
        assertFalse(permissionDenied.autoOpenSearchMap)
        assertFalse(searchAreaPending.autoOpenSearchMap)
        assertTrue(offline.requiresLimitedOpenConfirmation)
        assertTrue(unavailable.requiresLimitedOpenConfirmation)
        assertTrue(offline.visibleText().any { it.contains("지도 준비 전 현장 기록 열기") })
        assertTrue(unavailable.visibleText().any { it.contains("지도 준비 전 현장 기록 열기") })
        assertTrue(searchAreaPending.visibleText().any { it.contains("수색구역 지정 전") })
        assertTrue(searchAreaPending.canOpenSearchMap)
        assertTrue(searchAreaPending.visibleText().any { it.contains("현장 기록 열기") })
        assertTrue(unavailable.canManualRetry)
        assertFalse(permissionDenied.canOpenSearchMap)
        assertFalse(permissionDenied.requiresLimitedOpenConfirmation)
    }

    @Test
    fun autoRetryInProgressDoesNotExposeManualRetryBeforeExhausted() {
        val retrying = OfflinePackageUiState.autoRetry(
            incidentTitle = "광주 북구 산악 실종",
            manifestRevision = 17,
            retryAttempt = 2
        )
        val exhausted = OfflinePackageUiState.retryExhausted(
            incidentTitle = "광주 북구 산악 실종",
            manifestRevision = 17
        )

        assertEquals("자동 재시도 2/3", retrying.retryLabel)
        assertFalse(retrying.canManualRetry)
        assertTrue(retrying.requiresLimitedOpenConfirmation)
        assertTrue(exhausted.canManualRetry)
        assertTrue(exhausted.requiresLimitedOpenConfirmation)
    }
}
