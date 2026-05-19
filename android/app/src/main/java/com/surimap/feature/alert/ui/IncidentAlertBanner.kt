package com.surimap.feature.alert.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonSize
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.theme.PoliBgSurface
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliEmphasis
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliWarning
import com.surimap.ui.theme.SuriMapTheme
import com.surimap.ui.navigation.SearchMapDeepLink

enum class IncidentAlertType {
    PERSON_FOUND,
    SUPPORT_REQUEST
}

data class IncidentAlertUiState(
    val type: IncidentAlertType,
    val eventId: String,
    val incidentId: String,
    val focusMarkerId: String,
    val title: String,
    val subtitle: String,
    val body: String
) {
    val confirmActionLabel: String = "확인"
    val mapActionLabel: String = "지도 열기"

    fun visibleText(): List<String> =
        listOf(
            title,
            subtitle,
            body,
            confirmActionLabel,
            mapActionLabel,
            focusMarkerId
        )

    companion object {
        fun personFound(markerId: String): IncidentAlertUiState =
            IncidentAlertUiState(
                type = IncidentAlertType.PERSON_FOUND,
                eventId = "evt-person-found-001",
                incidentId = "inc-precinct-first-001",
                focusMarkerId = markerId,
                title = "실종자 발견",
                subtitle = "기동대 1부대 A팀 · 14:23",
                body = "북측 능선에서 실종자 발견 마커가 생성되었습니다."
            )

        fun supportRequest(markerId: String): IncidentAlertUiState =
            IncidentAlertUiState(
                type = IncidentAlertType.SUPPORT_REQUEST,
                eventId = "evt-support-request-001",
                incidentId = "inc-precinct-first-001",
                focusMarkerId = markerId,
                title = "지원 요청",
                subtitle = "기동대 1부대 A팀 · 14:23",
                body = "북측 능선 진입 불가로 드론 지원 요청이 생성되었습니다."
            )

        fun fromMarkerNotification(
            type: IncidentAlertType,
            eventId: String,
            incidentId: String,
            markerId: String,
            locationLabel: String?
        ): IncidentAlertUiState {
            val title = if (type == IncidentAlertType.PERSON_FOUND) "실종자 발견" else "지원 요청"
            val locationText =
                locationLabel
                    ?.takeIf(String::isNotBlank)
                    ?.let { "위치 $it" }
                    ?: "마커 위치 확인 필요"
            val body =
                if (type == IncidentAlertType.PERSON_FOUND) {
                    "실종자 발견 마커가 생성되었습니다. 지도에서 위치를 확인하세요."
                } else {
                    "지원 요청 마커가 생성되었습니다. 지도에서 위치를 확인하세요."
                }
            return IncidentAlertUiState(
                type = type,
                eventId = eventId,
                incidentId = incidentId,
                focusMarkerId = markerId,
                title = title,
                subtitle = locationText,
                body = body
            )
        }

        fun personFoundSample(): IncidentAlertUiState = personFound(markerId = "mk-precinct-person-found-001")
    }
}

data class IncidentFcmPayload(
    val eventId: String,
    val type: String,
    val incidentId: String,
    val markerId: String?,
    val locationLabel: String? = null
)

sealed interface IncidentFcmRoute {
    sealed interface Alert : IncidentFcmRoute

    data class MarkerFocus(
        val eventId: String,
        val incidentId: String,
        val markerId: String,
        val searchMapRoute: String,
        val alert: IncidentAlertUiState
    ) : Alert

    data class IncidentClosed(
        val eventId: String,
        val incidentId: String
    ) : IncidentFcmRoute

    data object Ignore : IncidentFcmRoute
}

object IncidentFcmRouteMapper {
    fun route(payload: IncidentFcmPayload): IncidentFcmRoute =
        when (payload.type) {
            "PERSON_FOUND" -> payload.toMarkerFocus(IncidentAlertType.PERSON_FOUND)
            "SUPPORT_REQUEST",
            "SUPPORT_REQUEST_CREATED" -> payload.toMarkerFocus(IncidentAlertType.SUPPORT_REQUEST)
            "INCIDENT_CLOSED" ->
                IncidentFcmRoute.IncidentClosed(
                    eventId = payload.eventId,
                    incidentId = payload.incidentId
                )
            else -> IncidentFcmRoute.Ignore
        }

    private fun IncidentFcmPayload.toMarkerFocus(type: IncidentAlertType): IncidentFcmRoute {
        val markerId = markerId ?: return IncidentFcmRoute.Ignore
        val alert =
            IncidentAlertUiState.fromMarkerNotification(
                type = type,
                eventId = eventId,
                incidentId = incidentId,
                markerId = markerId,
                locationLabel = locationLabel
            )
        return IncidentFcmRoute.MarkerFocus(
            eventId = eventId,
            incidentId = incidentId,
            markerId = markerId,
            searchMapRoute = SearchMapDeepLink.markerFocusRoute(markerId),
            alert = alert
        )
    }
}

@Composable
fun IncidentAlertBanner(
    state: IncidentAlertUiState,
    onConfirm: () -> Unit,
    onOpenMap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (state.type == IncidentAlertType.PERSON_FOUND) PoliEmphasis else PoliWarning
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = PoliBgSurface,
        contentColor = accent,
        border = BorderStroke(2.dp, accent)
    ) {
        Row(
            modifier = Modifier.padding(PoliDimens.Space4),
            horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PoliChip(
                text = if (state.type == IncidentAlertType.PERSON_FOUND) "발견" else "지원",
                variant = if (state.type == IncidentAlertType.PERSON_FOUND) PoliChipVariant.Bad else PoliChipVariant.Warn
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1)) {
                Text(text = state.title, style = MaterialTheme.typography.titleMedium)
                Text(text = state.subtitle, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
                Text(text = state.body, style = MaterialTheme.typography.bodyMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
                PoliButton(
                    text = state.confirmActionLabel,
                    onClick = onConfirm,
                    size = PoliButtonSize.Small,
                    variant = PoliButtonVariant.Secondary
                )
                PoliButton(
                    text = state.mapActionLabel,
                    onClick = { onOpenMap(state.focusMarkerId) },
                    size = PoliButtonSize.Small
                )
            }
        }
    }
}

@Preview(widthDp = 412)
@Composable
private fun IncidentAlertBannerPreview() {
    SuriMapTheme {
        IncidentAlertBanner(
            state = IncidentAlertUiState.personFoundSample(),
            onConfirm = {},
            onOpenMap = {}
        )
    }
}
