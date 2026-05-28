package com.surimap.ui.navigation

import com.surimap.testing.dutyShiftIdFixture
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.opIdFixture
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicePhoneNavigationContractTest {
    @Test
    fun routeRegistryContainsAuiT02RoutesInOrder() {
        assertEquals(
            listOf(
                "auth_bootstrap",
                "incident_list",
                "incident_home",
                "offline_package",
                "search_map",
                "work_status",
                "handover_summary",
                "handover_memo",
                "marker_detail",
                "blocked_outbox"
            ),
            PolicePhoneRoutes.all.map { it.route }
        )
    }

    @Test
    fun routeRegistryMatchesDeepLinkNavigationPatterns() {
        assertEquals(
            PolicePhoneRoute.SearchMap,
            PolicePhoneRoutes.fromNavigationRoute("search_map?focusMarkerId={focusMarkerId}")
        )
        assertEquals(PolicePhoneRoute.MarkerDetail, PolicePhoneRoutes.fromNavigationRoute("marker_detail/{markerId}"))
        assertEquals(PolicePhoneRoute.HandoverMemo, PolicePhoneRoutes.fromNavigationRoute("handover_memo"))
        assertNull(PolicePhoneRoutes.fromNavigationRoute("unknown"))
    }

    @Test
    fun bottomNavigationExposesIncidentContextDestinationsOnly() {
        assertEquals(
            listOf("incident_home", "search_map", "handover_summary", "blocked_outbox"),
            PolicePhoneBottomNavigation.items.map { it.route.route }
        )
        assertEquals(listOf("사건", "지도", "인수인계", "미전송"), PolicePhoneBottomNavigation.items.map { it.label })

        assertFalse(PolicePhoneBottomNavigation.shouldShow(PolicePhoneRoute.AuthBootstrap, hasIncidentContext = true))
        assertFalse(PolicePhoneBottomNavigation.shouldShow(PolicePhoneRoute.IncidentList, hasIncidentContext = true))
        assertFalse(PolicePhoneBottomNavigation.shouldShow(PolicePhoneRoute.SearchMap, hasIncidentContext = false))
        assertTrue(PolicePhoneBottomNavigation.shouldShow(PolicePhoneRoute.SearchMap, hasIncidentContext = true))
        assertTrue(PolicePhoneBottomNavigation.shouldShow(PolicePhoneRoute.WorkStatus, hasIncidentContext = true))
        assertTrue(PolicePhoneBottomNavigation.shouldShow(PolicePhoneRoute.HandoverMemo, hasIncidentContext = true))
        assertTrue(PolicePhoneBottomNavigation.shouldShow(PolicePhoneRoute.MarkerDetail, hasIncidentContext = true))
    }

    @Test
    fun bottomNavigationMapsNestedScreensToParentTab() {
        assertEquals(
            PolicePhoneRoute.IncidentHome,
            PolicePhoneBottomNavigation.selectedRouteFor(PolicePhoneRoute.OfflinePackage)
        )
        assertEquals(
            PolicePhoneRoute.SearchMap,
            PolicePhoneBottomNavigation.selectedRouteFor(PolicePhoneRoute.WorkStatus)
        )
        assertEquals(
            PolicePhoneRoute.SearchMap,
            PolicePhoneBottomNavigation.selectedRouteFor(PolicePhoneRoute.MarkerDetail)
        )
        assertEquals(
            PolicePhoneRoute.HandoverSummary,
            PolicePhoneBottomNavigation.selectedRouteFor(PolicePhoneRoute.HandoverMemo)
        )
        assertEquals(
            PolicePhoneRoute.BlockedOutbox,
            PolicePhoneBottomNavigation.selectedRouteFor(PolicePhoneRoute.BlockedOutbox)
        )
        assertNull(PolicePhoneBottomNavigation.selectedRouteFor(PolicePhoneRoute.IncidentList))
    }

    @Test
    fun backNavigationUsesScreenHierarchyInsteadOfVisitHistory() {
        assertNull(PolicePhoneBackNavigation.parentRouteFor(PolicePhoneRoute.IncidentHome))
        assertEquals(
            PolicePhoneRoute.IncidentHome,
            PolicePhoneBackNavigation.parentRouteFor(PolicePhoneRoute.OfflinePackage)
        )
        assertNull(PolicePhoneBackNavigation.parentRouteFor(PolicePhoneRoute.SearchMap))
        assertEquals(
            PolicePhoneRoute.SearchMap,
            PolicePhoneBackNavigation.parentRouteFor(PolicePhoneRoute.WorkStatus)
        )
        assertNull(PolicePhoneBackNavigation.parentRouteFor(PolicePhoneRoute.HandoverSummary))
        assertNull(PolicePhoneBackNavigation.parentRouteFor(PolicePhoneRoute.BlockedOutbox))
        assertEquals(
            PolicePhoneRoute.HandoverSummary,
            PolicePhoneBackNavigation.parentRouteFor(PolicePhoneRoute.HandoverMemo)
        )
        assertEquals(
            PolicePhoneRoute.SearchMap,
            PolicePhoneBackNavigation.parentRouteFor(PolicePhoneRoute.MarkerDetail)
        )
        assertNull(PolicePhoneBackNavigation.parentRouteFor(PolicePhoneRoute.AuthBootstrap))
        assertNull(PolicePhoneBackNavigation.parentRouteFor(PolicePhoneRoute.IncidentList))
    }

    @Test
    fun incidentTopLevelNavigationCollapsesTabVisitHistory() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()
        val navHostIndex = source.indexOf("NavHost(")
        val backHandlerIndex = source.indexOf("PolicePhoneBackPolicyHandler(", navHostIndex)

        assertTrue(source.contains("navController.navigateToIncidentBottomTab(route)"))
        assertTrue(backHandlerIndex > navHostIndex)
        assertTrue(source.contains("private fun NavHostController.navigateToIncidentHomeRoot()"))
        assertTrue(source.contains("navigate(PolicePhoneRoute.SearchMap.route)"))
        assertTrue(source.contains("if (route != PolicePhoneRoute.SearchMap)"))
        assertTrue(source.contains("private fun NavHostController.navigateToIncidentBottomTab(route: PolicePhoneRoute)"))
        assertTrue(source.contains("popBackStack(PolicePhoneRoute.IncidentHome.route, inclusive = false)"))
        assertTrue(source.contains("popUpTo(PolicePhoneRoute.IncidentList.route)"))
        assertTrue(source.contains("currentRoute == PolicePhoneRoute.HandoverSummary"))
        assertTrue(source.contains("currentRoute == PolicePhoneRoute.WorkStatus"))
        assertTrue(source.contains("currentRoute == PolicePhoneRoute.BlockedOutbox"))
        assertTrue(source.contains("onIncidentSupportTabBack = { navController.navigateToIncidentTopLevel(PolicePhoneRoute.SearchMap) }"))
        assertTrue(source.contains("else -> onRequestIncidentExit()"))
        assertFalse(source.contains("navigateToIncidentHomeRoot()\n    navigateToSingleTop(route)"))
        assertFalse(source.contains("onBack = { navController.navigateToSingleTop(PolicePhoneRoute.IncidentHome) }"))
        assertFalse(source.contains("onOpenSearchMap = { navController.navigateToSingleTop(PolicePhoneRoute.SearchMap) }"))
    }

    @Test
    fun bottomTabSelectionDoesNotRouteThroughSearchMapForSupportTabs() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()
        val helperIndex = source.indexOf("private fun NavHostController.navigateToIncidentBottomTab")
        val nextHelperIndex = source.indexOf("private fun NavHostController.navigateToIncidentHomeRoot", helperIndex)
        val helperSource = source.substring(helperIndex, nextHelperIndex)

        assertTrue(helperSource.contains("if (route == PolicePhoneRoute.SearchMap)"))
        assertTrue(helperSource.contains("navigateToIncidentTopLevel(PolicePhoneRoute.SearchMap)"))
        assertTrue(helperSource.contains("navigate(route.route)"))
        assertTrue(helperSource.contains("popUpTo(PolicePhoneRoute.IncidentList.route)"))
        assertFalse(helperSource.contains("navigate(PolicePhoneRoute.SearchMap.route)"))
    }

    @Test
    fun incidentWorkspaceKeepsSearchMapMountedBehindSupportTabs() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertTrue(source.contains("val showPersistentIncidentMap ="))
        assertTrue(source.contains("PersistentIncidentMapHost("))
        assertTrue(source.contains("if (showPersistentIncidentMap)"))
        assertTrue(source.contains("if (!showPersistentIncidentMap)"))
        assertTrue(source.contains("backHandlingEnabled = currentRoute == PolicePhoneRoute.SearchMap"))
        assertTrue(source.contains("BackHandler(enabled = backHandlingEnabled)"))
    }

    @Test
    fun incidentSupportTabsRemainOpaquePagesOverPersistentMap() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertTrue(source.contains("private fun IncidentWorkspacePage("))
        assertTrue(source.contains(".background(PoliBgBase)"))
        assertTrue(source.contains(".consumeUnclaimedPointerEvents()"))
        assertTrue(source.contains("awaitPointerEvent(PointerEventPass.Final)"))
        assertTrue(source.contains("change.consume()"))
        assertTrue(source.contains("IncidentWorkspacePage {\n                            IncidentHomeRoute("))
        assertTrue(source.contains("IncidentWorkspacePage {\n                            HandoverSummaryRoute("))
        assertTrue(source.contains("IncidentWorkspacePage {\n                            BlockedOutboxRoute("))
    }

    @Test
    fun incidentSupportTabTransitionShieldsPersistentMapBeforeDestinationDraws() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertTrue(source.contains("var pendingTopLevelRoute by remember"))
        assertTrue(source.contains("val shouldShieldPersistentMapDuringTransition ="))
        assertTrue(source.contains("currentRoute != pendingSupportRoute"))
        assertTrue(source.contains("pendingTopLevelRoute = route"))
        assertTrue(source.contains("if (pendingTopLevelRoute == currentRoute)"))
        assertTrue(source.contains("IncidentWorkspaceTransitionShield()"))
        assertTrue(source.contains("private fun IncidentWorkspaceTransitionShield()"))
    }

    @Test
    fun incidentTopLevelTabsDoNotExposeAppBarBackButtons() {
        val handoverSource = File("src/main/java/com/surimap/feature/handover/ui/DutyHandoverScreen.kt").readText()
        val outboxSource = File("src/main/java/com/surimap/feature/outbox/ui/BlockedOutboxScreen.kt").readText()

        assertTrue(handoverSource.contains("showBack = false"))
        assertTrue(outboxSource.contains("showBack = false"))
    }

    @Test
    fun searchMapMarkerDetailUsesModalInsteadOfRouteNavigation() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()
        val routeIndex = source.indexOf("private fun SearchMapRoute(")
        val routeEndIndex = source.indexOf("private fun MarkerDetailModal(", routeIndex)
        val searchMapRoute = source.substring(routeIndex, routeEndIndex)

        assertTrue(searchMapRoute.contains("var markerDetailModalId by rememberSaveable"))
        assertTrue(searchMapRoute.contains("markerDetailModalId = markerId"))
        assertTrue(searchMapRoute.contains("MarkerDetailModal("))
        assertFalse(searchMapRoute.contains("MarkerDetailDeepLink.route(markerId)"))
    }

    @Test
    fun incidentSessionHolderClearsIncidentContext() {
        val holder = IncidentSessionState()

        holder.activateIncidentContext(
            IncidentContext(
                incidentId = INCIDENT_ID,
                currentOpId = OP_ID,
                currentDutyShiftId = DUTY_SHIFT_ID
            )
        )
        assertEquals(INCIDENT_ID, holder.incidentContext?.incidentId)

        holder.clearIncidentContext()

        assertNull(holder.incidentContext)
    }

    @Test
    fun policePhoneContextCarriesBootstrapAccessTokenForProtectedAppRequests() {
        val context =
            PolicePhoneContext(
                policePhoneId = "00000000-0000-0000-0000-000000000101",
                apiBaseUrl = "https://suri-map.internal/api",
                tileBaseUrl = "https://suri-map.internal",
                objectStorageBaseUrl = "https://suri-map.internal",
                accessToken = "bootstrap-token-1"
            )

        assertEquals("bootstrap-token-1", context.accessTokenProvider().accessToken())
    }

    @Test
    fun searchMapMarkerFocusRoutePreservesMarkerIdAsDeeplinkArgument() {
        val route = SearchMapDeepLink.markerFocusRoute("mk-person/found 001")

        assertEquals("search_map?focusMarkerId=mk-person%2Ffound+001", route)
        assertEquals("mk-person/found 001", SearchMapDeepLink.focusMarkerIdFromRoute(route))
        assertNull(SearchMapDeepLink.focusMarkerIdFromRoute(PolicePhoneRoute.SearchMap.route))
    }

    @Test
    fun markerDetailRoutePreservesMarkerIdAsPathArgument() {
        val route = MarkerDetailDeepLink.route("mk-person/found 001")

        assertEquals("marker_detail/mk-person%2Ffound%20001", route)
        assertEquals("mk-person/found 001", MarkerDetailDeepLink.markerIdFromRoute(route))
        assertNull(MarkerDetailDeepLink.markerIdFromRoute(PolicePhoneRoute.MarkerDetail.route))
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("003")
        val DUTY_SHIFT_ID = dutyShiftIdFixture("014")
    }
}
