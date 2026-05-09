package com.surimap.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalWarningUiStateTest {
    @Test
    fun mapsWarningSnapshotToStableUserSafeBannerState() {
        val snapshot = LocalWarningSnapshot(
            activeWarnings = setOf(
                LocalWarningCode.GPS_STOPPED,
                LocalWarningCode.BATTERY_LOW,
                LocalWarningCode.OFFLINE_RECORDING,
                LocalWarningCode.PACKAGE_MISSING,
                LocalWarningCode.OUTBOX_BACKLOG
            )
        )

        val uiState = LocalWarningUiState.from(snapshot)

        assertEquals(
            listOf("GPS 신호 중단", "배터리 부족", "오프라인 기록 중", "지도 패키지 확인 필요", "미전송 기록 적체"),
            uiState.banners.map { it.title }
        )
        assertFalse(
            uiState.banners.any { banner ->
                banner.title.contains("http_") || banner.message.contains("Exception")
            }
        )
    }
}
