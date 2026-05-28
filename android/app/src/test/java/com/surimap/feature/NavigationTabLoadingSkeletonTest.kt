package com.surimap.feature

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTabLoadingSkeletonTest {

    @Test
    fun incidentSupportTabsUseSharedShimmerSkeletonDuringInitialLoad() {
        val appSource = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()
        val incidentHomeSource = File("src/main/java/com/surimap/feature/incidents/ui/IncidentHomeScreen.kt").readText()
        val handoverSource = File("src/main/java/com/surimap/feature/handover/ui/DutyHandoverScreen.kt").readText()
        val outboxSource = File("src/main/java/com/surimap/feature/outbox/ui/BlockedOutboxScreen.kt").readText()
        val skeletonSource = File("src/main/java/com/surimap/ui/components/PoliSkeleton.kt").readText()

        assertTrue(appSource.contains("loading = initialLoading"))
        assertTrue(appSource.contains("val isInitialLoad = initialLoading && refreshNonce == 0"))

        assertTrue(incidentHomeSource.contains("IncidentHomeLoadingContent"))
        assertTrue(incidentHomeSource.contains("rememberPoliShimmerBrush(label = \"incident-home-skeleton\")"))
        assertTrue(incidentHomeSource.contains("PoliSkeletonCard(title = \"사건 개요\""))
        assertTrue(incidentHomeSource.contains("PoliSkeletonCard(title = \"현장 상태\""))
        assertTrue(incidentHomeSource.contains("contentDescription = \"사건 정보 불러오는 중\""))

        assertTrue(handoverSource.contains("HandoverLoadingContent"))
        assertTrue(handoverSource.contains("rememberPoliShimmerBrush(label = \"handover-summary-skeleton\")"))
        assertTrue(handoverSource.contains("PoliSkeletonCard(title = \"지도 리플레이\""))
        assertTrue(handoverSource.contains("contentDescription = \"\${mode.title} 불러오는 중\""))

        assertTrue(outboxSource.contains("BlockedOutboxLoadingContent"))
        assertTrue(outboxSource.contains("rememberPoliShimmerBrush(label = \"blocked-outbox-skeleton\")"))
        assertTrue(outboxSource.contains("PoliSkeletonCard(title = \"처리 불가 항목\""))
        assertTrue(outboxSource.contains("contentDescription = \"미전송 기록 불러오는 중\""))

        assertTrue(skeletonSource.contains("rememberInfiniteTransition"))
        assertTrue(skeletonSource.contains("Brush.linearGradient"))
        assertTrue(skeletonSource.contains("PoliSkeletonLine"))
    }
}
