package com.surimap.feature.search

import com.surimap.core.sync.LocalWarningCode
import com.surimap.core.sync.LocalWarningSnapshot
import com.surimap.core.sync.LocalWarningUiState
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.feature.search.ui.SearchLayerKind
import com.surimap.feature.search.ui.SearchMapLayerUiState
import com.surimap.feature.search.ui.SearchMapSyncStatus
import com.surimap.feature.search.ui.SearchMapUiState
import com.surimap.testing.markerIdFixture
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMapUiStateTest {

    @Test
    fun activeSearchAllowsPathAndMarkerWrites() {
        val active = SearchMapUiState.active().copy(bottomPanelExpanded = true)

        assertEquals(SearchLifecycleStatus.Active, active.lifecycleStatus)
        assertTrue(active.canWritePath)
        assertTrue(active.canCreateMarker)
        assertEquals("일시정지", active.primaryActionLabel)
        assertTrue(active.visibleText().contains("OP 3차"))
        assertTrue(active.visibleText().any { it.contains("마커 생성") })
    }

    @Test
    fun opRequiredBlocksAllMapWritesAndShowsRetryCopy() {
        val opRequired = SearchMapUiState.opRequired().copy(bottomPanelExpanded = true)
        val opTransition = SearchMapUiState.opTransition()

        assertEquals(SearchLifecycleStatus.OpRequired, opRequired.lifecycleStatus)
        assertFalse(opRequired.canWritePath)
        assertFalse(opRequired.canCreateMarker)
        assertTrue(opRequired.visibleText().any { it.contains("OP 다시 확인") })
        assertTrue(opRequired.visibleText().any { it.contains("경로·마커 기록 차단") })
        assertEquals(SearchLifecycleStatus.OpTransition, opTransition.lifecycleStatus)
        assertFalse(opTransition.canWritePath)
        assertFalse(opTransition.canCreateMarker)
        assertTrue(opTransition.visibleText().any { it.contains("readonly") })
    }

    @Test
    fun pausedAndStoppedStatesExposeWriteAvailability() {
        val paused = SearchMapUiState.paused()
        val stopped = SearchMapUiState.stopped()

        assertEquals("재개", paused.primaryActionLabel)
        assertFalse(paused.canWritePath)
        assertFalse(paused.canCreateMarker)
        assertEquals("수색 시작", stopped.primaryActionLabel)
        assertFalse(stopped.canWritePath)
        assertFalse(stopped.canCreateMarker)
    }

    @Test
    fun normalOutboxQueueStaysInSyncChipInsteadOfBlockedQueueRoute() {
        val offline = SearchMapUiState.offline(unsentCount = 12, oldestPendingMinutes = 8)
        val sending = SearchMapUiState.sending(unsentCount = 3)

        assertEquals(SearchMapSyncStatus.Offline, offline.syncStatus)
        assertEquals("미전송 12 · 8분", offline.syncLabel)
        assertFalse(offline.shouldOpenBlockedOutbox)
        assertEquals(SearchMapSyncStatus.Sending, sending.syncStatus)
        assertEquals("전송 중 · 3", sending.syncLabel)
        assertFalse(sending.shouldOpenBlockedOutbox)
    }

    @Test
    fun localWarningsAreVisibleOnSearchMapWithoutOpeningBlockedQueueRoute() {
        val warnings =
            LocalWarningUiState.from(
                LocalWarningSnapshot(
                    setOf(
                        LocalWarningCode.GPS_STOPPED,
                        LocalWarningCode.BATTERY_LOW,
                        LocalWarningCode.PACKAGE_MISSING,
                        LocalWarningCode.OFFLINE_RECORDING,
                        LocalWarningCode.OUTBOX_BACKLOG
                    )
                )
            )
        val state = SearchMapUiState.active().copy(localWarnings = warnings)

        assertTrue(state.visibleText().contains("GPS 신호 중단"))
        assertTrue(state.visibleText().contains("배터리 부족"))
        assertTrue(state.visibleText().contains("지도 패키지 확인 필요"))
        assertTrue(state.visibleText().contains("오프라인 기록 중"))
        assertTrue(state.visibleText().contains("미전송 기록 적체"))
        assertFalse(state.shouldOpenBlockedOutbox)
    }

    @Test
    fun packageMissingWarningUsesTransientToastInsteadOfPersistentBanner() {
        val source = File("src/main/java/com/surimap/feature/search/ui/SearchMapScreen.kt").readText()

        assertTrue(source.contains("state.localWarnings.banners.filterNot { warning ->"))
        assertTrue(source.contains("warning.code == LocalWarningCode.PACKAGE_MISSING"))
        assertTrue(source.contains("persistentLocalWarnings.forEach { warning ->"))
        assertTrue(source.contains("visiblePackageWarning?.let {"))
        assertTrue(source.contains("PoliToast("))
        assertTrue(source.contains("PackageWarningToastTitle = \"오프라인 지도가 준비되지 않았어요\""))
        assertTrue(source.contains("PackageWarningToastText = \"오프라인 사용 전 다운로드가 필요합니다\""))
        assertTrue(source.contains("title = PackageWarningToastTitle"))
        assertTrue(source.contains("text = PackageWarningToastText"))
        assertTrue(source.contains("variant = PoliBannerVariant.Warn"))
        assertTrue(source.contains("delay(PackageWarningToastDurationMs)"))
        assertFalse(source.contains("actionText = \"확인\""))
        assertFalse(source.contains("state.localWarnings.banners.forEach { warning ->"))
    }

    @Test
    fun blockedOutboxIsSeparateFromNormalQueueAndHandoverPromptCanEnterP6A() {
        val state = SearchMapUiState.active(
            blockedOutboxCount = 2,
            hasUnreadHandover = true
        )

        assertTrue(state.shouldOpenBlockedOutbox)
        assertTrue(state.showHandoverPrompt)
        assertTrue(state.visibleText().any { it.contains("미전송 2건 처리 불가") })
        assertTrue(state.visibleText().any { it.contains("이전 근무 기록 있음") })
        assertTrue(state.visibleText().any { it.contains("확인") })
        assertFalse(state.visibleText().any { it.contains("추천") })
        assertFalse(state.visibleText().any { it.contains("위험") })
        assertFalse(state.visibleText().any { it.contains("미수색") })
    }

    @Test
    fun searchMapHeaderTextShowsAssignmentWithoutDutyShiftIdentifier() {
        val assigned =
            SearchMapUiState.active().copy(
                dutyShiftLabel = "DutyShift 00000000-0000-0000-0000-000000000101",
                assignmentLabel = "A팀 담당 구역"
            )
        val unassigned =
            SearchMapUiState.active().copy(
                dutyShiftLabel = "DutyShift 00000000-0000-0000-0000-000000000101",
                assignmentLabel = ""
            )

        assertEquals("A팀 담당 구역", assigned.assignmentDisplayLabel)
        assertTrue(assigned.visibleText().contains("A팀 담당 구역"))
        assertFalse(assigned.visibleText().any { it.contains("DutyShift") })
        assertEquals("담당구역 미배정", unassigned.assignmentDisplayLabel)
        assertTrue(unassigned.visibleText().contains("담당구역 미배정"))
    }

    @Test
    fun activeSearchMapCanOpenHandoverEvenWithoutUnreadPrompt() {
        val state = SearchMapUiState.active(hasUnreadHandover = false).copy(bottomPanelExpanded = true)

        assertFalse(state.showHandoverPrompt)
        assertTrue(state.visibleText().contains("인수인계"))
    }

    @Test
    fun appSearchMapRouteOwnsHandoverPromptSeenStateForP5EntryBanner() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        val routeIndex = source.indexOf("private fun SearchMapRoute")
        val currentShiftStartedIndex = source.indexOf("currentDutyShiftStartedAt", routeIndex)
        val lastSeenIndex = source.indexOf("lastSeenHandoverAt", routeIndex)
        val promptIndex = source.indexOf("HandoverPromptUiState(", routeIndex)
        val readSeenIndex = source.indexOf("readLastSeenHandoverAt", routeIndex)
        val openHandoverIndex = source.indexOf("fun openHandoverFromSearchMap", routeIndex)
        val writeSeenIndex = source.indexOf("writeLastSeenHandoverAt", openHandoverIndex)
        val navigateIndex = source.indexOf("navigateToSingleTop(PolicePhoneRoute.HandoverSummary)", openHandoverIndex)

        assertTrue(routeIndex >= 0)
        assertTrue(currentShiftStartedIndex > routeIndex)
        assertTrue(lastSeenIndex > routeIndex)
        assertTrue(promptIndex > lastSeenIndex)
        assertTrue(readSeenIndex > routeIndex)
        assertTrue(openHandoverIndex > routeIndex)
        assertTrue(writeSeenIndex > openHandoverIndex)
        assertTrue(navigateIndex > writeSeenIndex)
        assertFalse(source.contains("다음 투입"))
        assertFalse(source.contains("미수색"))
        assertFalse(source.contains("위험도"))
    }

    @Test
    fun appSearchMapRouteConnectsLocalWarningMonitorToActualUiState() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()
        val routeIndex = source.indexOf("private fun SearchMapRoute")
        val monitorIndex = source.indexOf("LocalWarningMonitor()", routeIndex)
        val evaluateIndex = source.indexOf("localWarningMonitor.evaluate(", routeIndex)
        val uiStateIndex = source.indexOf("localWarnings = LocalWarningUiState.from(localWarningSnapshot)", routeIndex)
        val batteryIndex = source.indexOf("ACTION_BATTERY_CHANGED", routeIndex)
        val locationIndex = source.indexOf("context.isLocationUsable()", routeIndex)
        val packageIndex = source.indexOf("offlinePackageInstallationDao.observe", routeIndex)

        assertTrue(routeIndex >= 0)
        assertTrue(monitorIndex > routeIndex)
        assertTrue(evaluateIndex > monitorIndex)
        assertTrue(uiStateIndex > routeIndex)
        assertTrue(batteryIndex > routeIndex)
        assertTrue(locationIndex > routeIndex)
        assertTrue(packageIndex > routeIndex)
    }

    @Test
    fun markerFocusDeeplinkHighlightsTargetMarkerWithoutChangingWriteAvailability() {
        val state =
            SearchMapUiState.active().copy(
                topHeaderExpanded = true,
                layers =
                listOf(
                    SearchMapLayerUiState(
                        label = "실종자 발견",
                        kind = SearchLayerKind.Marker,
                        highlighted = false,
                        overlayId = MARKER_ID,
                        geoJson = """{"type":"Point","coordinates":[126.91,37.51]}"""
                    )
                )
            ).withFocusedMarker(MARKER_ID)

        assertEquals(MARKER_ID, state.focusedMarkerId)
        assertTrue(state.focusedMarkerLayer!!.highlighted)
        assertEquals(37.509, state.focusedMarkerViewportBounds!!.south, 0.000001)
        assertEquals(126.909, state.focusedMarkerViewportBounds.west, 0.000001)
        assertEquals(37.511, state.focusedMarkerViewportBounds.north, 0.000001)
        assertEquals(126.911, state.focusedMarkerViewportBounds.east, 0.000001)
        assertTrue(state.visibleText().any { it.contains("마커 포커스 · 실종자 발견") })
        assertTrue(state.canCreateMarker)
        assertTrue(state.canWritePath)
    }

    @Test
    fun liveMarkerLayerWithoutFcmFocusCanOpenMarkerDetail() {
        val state =
            SearchMapUiState.active().copy(
                topHeaderExpanded = true,
                layers =
                    listOf(
                        SearchMapLayerUiState(
                            label = "지원 요청",
                            kind = SearchLayerKind.Marker,
                            overlayId = MARKER_ID,
                            geoJson = """{"type":"Point","coordinates":[126.91,37.51]}"""
                        )
                    )
            )

        assertEquals(MARKER_ID, state.markerDetailTargetId)
        assertTrue(state.visibleText().contains("마커"))
    }

    @Test
    fun mapChromeCanCollapsePanelWithoutExtraInfoToggle() {
        val state =
            SearchMapUiState.active().copy(
                topHeaderExpanded = true,
                bottomPanelExpanded = false,
                mapOverlaysVisible = false
            )

        assertFalse(state.bottomPanelExpanded)
        assertFalse(state.mapOverlaysVisible)
        assertTrue(state.visibleText().contains("지도 정보 접힘"))
        assertFalse(state.visibleText().contains("지도 오버레이 숨김"))
        assertTrue(state.visibleText().contains("전체 수색구역"))
        assertTrue(state.visibleText().contains("부대 수색구역"))
        assertTrue(state.visibleText().contains("팀 담당구역"))
        assertTrue(state.visibleText().contains("마커"))
        assertFalse(state.visibleText().contains("일시정지"))
        assertFalse(state.visibleText().contains("종료"))
        assertFalse(state.visibleText().contains("인수인계"))
        assertFalse(state.visibleText().contains("마커 생성"))
    }

    @Test
    fun mapOverlayActionsStayVisibleEvenWhenIncidentHasNoAreaOrMarkerGeometry() {
        val state =
            SearchMapUiState.active().copy(
                topHeaderExpanded = true,
                layers = emptyList(),
                mapOverlaysVisible = true
            )

        assertFalse(state.canFocusOverallSearchArea)
        assertFalse(state.canFocusUnitSearchArea)
        assertFalse(state.canFocusTeamSearchArea)
        assertFalse(state.canOpenMarkerDetail)
        assertTrue(state.visibleText().contains("전체 수색구역"))
        assertTrue(state.visibleText().contains("부대 수색구역"))
        assertTrue(state.visibleText().contains("팀 담당구역"))
        assertTrue(state.visibleText().contains("마커"))
    }

    @Test
    fun searchAreaButtonsRecenterViewportAndClearMarkerFocus() {
        val state =
            SearchMapUiState.active().copy(
                topHeaderExpanded = true,
                focusedMarkerId = MARKER_ID,
                layers =
                    listOf(
                        SearchMapLayerUiState(
                            label = "전체 수색 구역",
                            kind = SearchLayerKind.Overall,
                            geoJson =
                                """{"type":"Polygon","coordinates":[[[126.90,35.15],[126.94,35.15],[126.94,35.19],[126.90,35.19],[126.90,35.15]]]}"""
                        ),
                        SearchMapLayerUiState(
                            label = "부대 수색 구역",
                            kind = SearchLayerKind.Unit,
                            geoJson =
                                """{"type":"Polygon","coordinates":[[[126.91,35.16],[126.93,35.16],[126.93,35.18],[126.91,35.18],[126.91,35.16]]]}"""
                        ),
                        SearchMapLayerUiState(
                            label = "단서",
                            kind = SearchLayerKind.Marker,
                            overlayId = MARKER_ID,
                            geoJson = """{"type":"Point","coordinates":[126.905,35.155]}"""
                        )
                    )
            )

        val overall = state.centerOnSearchLayer(SearchLayerKind.Overall)
        val unit = state.centerOnSearchLayer(SearchLayerKind.Unit)

        assertNull(overall.focusedMarkerId)
        assertEquals(35.15, overall.viewportBounds!!.south, 0.000001)
        assertEquals(126.90, overall.viewportBounds.west, 0.000001)
        assertEquals(35.19, overall.viewportBounds.north, 0.000001)
        assertEquals(126.94, overall.viewportBounds.east, 0.000001)
        assertNull(unit.focusedMarkerId)
        assertEquals(35.16, unit.viewportBounds!!.south, 0.000001)
        assertEquals(126.91, unit.viewportBounds.west, 0.000001)
        assertEquals(35.18, unit.viewportBounds.north, 0.000001)
        assertEquals(126.93, unit.viewportBounds.east, 0.000001)
        assertTrue(state.canFocusOverallSearchArea)
        assertTrue(state.canFocusUnitSearchArea)
        assertTrue(state.canOpenMarkerDetail)
        assertTrue(state.visibleText().contains("전체 수색구역"))
        assertTrue(state.visibleText().contains("부대 수색구역"))
        assertTrue(state.visibleText().contains("마커"))
    }

    @Test
    fun multipleUnitAndTeamAreasExposeSelectableTargets() {
        val state =
            SearchMapUiState.active().copy(
                topHeaderExpanded = true,
                layers =
                    listOf(
                        SearchMapLayerUiState(
                            label = "1기동대 담당",
                            kind = SearchLayerKind.Unit,
                            overlayId = "unit-1",
                            geoJson =
                                """{"type":"Polygon","coordinates":[[[126.91,35.16],[126.92,35.16],[126.92,35.17],[126.91,35.17],[126.91,35.16]]]}"""
                        ),
                        SearchMapLayerUiState(
                            label = "2기동대 담당",
                            kind = SearchLayerKind.Unit,
                            overlayId = "unit-2",
                            geoJson =
                                """{"type":"Polygon","coordinates":[[[126.93,35.18],[126.94,35.18],[126.94,35.19],[126.93,35.19],[126.93,35.18]]]}"""
                        ),
                        SearchMapLayerUiState(
                            label = "A팀 담당",
                            kind = SearchLayerKind.Team,
                            overlayId = "team-a",
                            geoJson =
                                """{"type":"Polygon","coordinates":[[[126.95,35.20],[126.96,35.20],[126.96,35.21],[126.95,35.21],[126.95,35.20]]]}"""
                        )
                    )
            )

        assertEquals(listOf("1기동대 담당", "2기동대 담당"), state.unitSearchAreaTargets.map { it.label })
        assertEquals(listOf("A팀 담당"), state.teamSearchAreaTargets.map { it.label })
        assertTrue(state.visibleText().contains("1기동대 담당"))
        assertTrue(state.visibleText().contains("2기동대 담당"))
        assertTrue(state.visibleText().contains("A팀 담당"))

        val secondUnit = state.centerOnSearchLayer(SearchLayerKind.Unit, overlayId = "unit-2")
        val team = state.centerOnSearchLayer(SearchLayerKind.Team, overlayId = "team-a")

        assertEquals(35.18, secondUnit.viewportBounds!!.south, 0.000001)
        assertEquals(126.93, secondUnit.viewportBounds.west, 0.000001)
        assertEquals(35.19, secondUnit.viewportBounds.north, 0.000001)
        assertEquals(126.94, secondUnit.viewportBounds.east, 0.000001)
        assertEquals(35.20, team.viewportBounds!!.south, 0.000001)
        assertEquals(126.95, team.viewportBounds.west, 0.000001)
        assertEquals(35.21, team.viewportBounds.north, 0.000001)
        assertEquals(126.96, team.viewportBounds.east, 0.000001)
    }

    private companion object {
        val MARKER_ID = markerIdFixture("person-found-001")
    }
}
