package com.surimap.feature.offline

import com.surimap.feature.offline.ui.OfflinePackageDownloadStatus
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
            listOf("사건 메타", "실종자", "OP", "구역", "마커", "전체 수색 구역", "타일"),
            labels
        )
    }

    @Test
    fun readyStateAutoOpensSearchMapOnlyWhenPackageIsFullyReady() {
        val ready = OfflinePackageUiState.ready(
            incidentTitle = "광주 북구 산악 실종",
            manifestRevision = 17
        )

        assertEquals(OfflinePackageDownloadStatus.Ready, ready.status)
        assertEquals(1f, ready.overallProgress)
        assertTrue(ready.readyForOfflineUse)
        assertTrue(ready.autoOpenSearchMap)
        assertFalse(ready.requiresLimitedOpenConfirmation)
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
        assertTrue(partial.visibleText().any { it.contains("지도 사용 제한") })
        assertTrue(partial.visibleText().any { it.contains("제한 안내 후 열기") })
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
        assertTrue(current.autoOpenSearchMap)
        assertTrue(current.visibleText().any { it.contains("manifest rev 17") })
        assertTrue(changed.shouldDownloadPackage)
        assertFalse(changed.autoOpenSearchMap)
        assertTrue(changed.visibleText().any { it.contains("manifest rev 17 -> 18") })
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
        assertTrue(exhausted.canManualRetry)
    }
}
