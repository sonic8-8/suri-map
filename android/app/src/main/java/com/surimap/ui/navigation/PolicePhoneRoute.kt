package com.surimap.ui.navigation

import android.util.Base64
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.surimap.core.network.AccessTokenProvider
import java.nio.charset.StandardCharsets
import org.json.JSONObject

enum class PolicePhoneRoute(val route: String) {
    AuthBootstrap("auth_bootstrap"),
    IncidentList("incident_list"),
    IncidentHome("incident_home"),
    OfflinePackage("offline_package"),
    SearchMap("search_map"),
    HandoverSummary("handover_summary"),
    HandoverMemo("handover_memo"),
    MarkerDetail("marker_detail"),
    BlockedOutbox("blocked_outbox")
}

object PolicePhoneRoutes {
    val all: List<PolicePhoneRoute> =
        listOf(
            PolicePhoneRoute.AuthBootstrap,
            PolicePhoneRoute.IncidentList,
            PolicePhoneRoute.IncidentHome,
            PolicePhoneRoute.OfflinePackage,
            PolicePhoneRoute.SearchMap,
            PolicePhoneRoute.HandoverSummary,
            PolicePhoneRoute.HandoverMemo,
            PolicePhoneRoute.MarkerDetail,
            PolicePhoneRoute.BlockedOutbox
        )

    fun fromNavigationRoute(route: String?): PolicePhoneRoute? {
        if (route.isNullOrBlank()) return null
        return all.firstOrNull { candidate ->
            route == candidate.route ||
                route.startsWith("${candidate.route}?") ||
                route.startsWith("${candidate.route}/")
        }
    }
}

data class PolicePhoneBottomNavItem(
    val route: PolicePhoneRoute,
    val label: String,
    val contentDescription: String
)

object PolicePhoneBottomNavigation {
    val items: List<PolicePhoneBottomNavItem> =
        listOf(
            PolicePhoneBottomNavItem(
                route = PolicePhoneRoute.IncidentHome,
                label = "사건",
                contentDescription = "현재 사건 정보"
            ),
            PolicePhoneBottomNavItem(
                route = PolicePhoneRoute.SearchMap,
                label = "지도",
                contentDescription = "수색 지도"
            ),
            PolicePhoneBottomNavItem(
                route = PolicePhoneRoute.HandoverSummary,
                label = "인수인계",
                contentDescription = "인수인계"
            ),
            PolicePhoneBottomNavItem(
                route = PolicePhoneRoute.BlockedOutbox,
                label = "미전송",
                contentDescription = "처리 불가 미전송 큐"
            )
        )

    private val itemRoutes: Set<PolicePhoneRoute> = items.mapTo(mutableSetOf()) { it.route }
    private val incidentContextRoutes: Set<PolicePhoneRoute> =
        itemRoutes + PolicePhoneRoute.OfflinePackage + PolicePhoneRoute.HandoverMemo + PolicePhoneRoute.MarkerDetail

    fun shouldShow(currentRoute: PolicePhoneRoute?, hasIncidentContext: Boolean): Boolean =
        hasIncidentContext && currentRoute != null && currentRoute in incidentContextRoutes

    fun selectedRouteFor(currentRoute: PolicePhoneRoute?): PolicePhoneRoute? =
        when (currentRoute) {
            PolicePhoneRoute.OfflinePackage -> PolicePhoneRoute.IncidentHome
            PolicePhoneRoute.HandoverMemo -> PolicePhoneRoute.HandoverSummary
            PolicePhoneRoute.MarkerDetail -> PolicePhoneRoute.SearchMap
            else -> currentRoute?.takeIf(itemRoutes::contains)
        }
}

object PolicePhoneBackNavigation {
    fun parentRouteFor(currentRoute: PolicePhoneRoute?): PolicePhoneRoute? =
        when (currentRoute) {
            PolicePhoneRoute.OfflinePackage -> PolicePhoneRoute.IncidentHome
            PolicePhoneRoute.HandoverMemo -> PolicePhoneRoute.HandoverSummary
            PolicePhoneRoute.MarkerDetail -> PolicePhoneRoute.SearchMap
            PolicePhoneRoute.IncidentHome,
            PolicePhoneRoute.SearchMap,
            PolicePhoneRoute.HandoverSummary,
            PolicePhoneRoute.BlockedOutbox,
            PolicePhoneRoute.AuthBootstrap,
            PolicePhoneRoute.IncidentList,
            null -> null
        }
}

data class IncidentContext(
    val incidentId: String,
    val currentOpId: String? = null,
    val currentOpLabel: String? = null,
    val currentDutyShiftId: String? = null
)

data class PolicePhoneContext(
    val policePhoneId: String,
    val apiBaseUrl: String,
    val tileBaseUrl: String,
    val objectStorageBaseUrl: String,
    val allowedHosts: Set<String> = emptySet(),
    val accessToken: String? = null,
    val accessTokenExpiresAtEpochMs: Long? = null,
    val accountId: String? = accessToken.accountIdClaim()
)

fun PolicePhoneContext?.accessTokenProvider(): AccessTokenProvider =
    AccessTokenProvider { this?.accessToken?.takeIf(String::isNotBlank) }

fun String?.accountIdClaim(): String? {
    val token = this?.takeIf(String::isNotBlank) ?: return null
    val payloadPart = token.split('.').getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
    return runCatching {
        val decoded = Base64.decode(payloadPart, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        JSONObject(String(decoded, StandardCharsets.UTF_8))
            .optString("accountId")
            .takeIf(String::isNotBlank)
    }.getOrNull()
}

@Stable
class IncidentSessionState(
    initialIncidentContext: IncidentContext? = null,
    initialPolicePhoneContext: PolicePhoneContext? = null
) {
    var incidentContext by mutableStateOf(initialIncidentContext)
        private set
    var policePhoneContext by mutableStateOf(initialPolicePhoneContext)
        private set

    fun activatePolicePhoneContext(context: PolicePhoneContext) {
        policePhoneContext = context
    }

    fun activateIncidentContext(context: IncidentContext) {
        incidentContext = context
    }

    fun clearIncidentContext() {
        incidentContext = null
    }

    fun clearPolicePhoneContext() {
        incidentContext = null
        policePhoneContext = null
    }
}
