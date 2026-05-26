package com.surimap.feature.marker.data

import com.surimap.core.marker.MarkerReadQuery
import com.surimap.core.marker.MarkerRepository
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.feature.marker.ui.MarkerDetailPhotoStatus
import com.surimap.feature.marker.ui.MarkerDetailPhotoUiState
import com.surimap.feature.marker.ui.MarkerDetailUiState
import com.surimap.feature.marker.ui.MarkerSaveStatus
import com.surimap.feature.marker.ui.MarkerType
import java.util.Locale
import org.json.JSONObject

data class MarkerDetailSessionContext(
    val incidentId: String?,
    val opId: String?,
    val policePhoneId: String?,
    val markerId: String?
)

class MarkerDetailStateLoader(
    private val markerRead: suspend (MarkerReadQuery) -> SuriMapApiResponse = { query ->
        MarkerRepository().listMarkers(query)
    }
) {
    suspend fun load(context: MarkerDetailSessionContext): MarkerDetailUiState {
        val markerId = context.markerId?.takeIf(String::isNotBlank) ?: return MarkerDetailUiState.unavailable("marker-id-missing")
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return MarkerDetailUiState.unavailable(markerId)
        val response =
            runCatching {
                markerRead(
                    MarkerReadQuery(
                        incidentId = incidentId,
                        opId = context.opId?.takeIf(String::isNotBlank)
                    )
                )
            }.getOrNull()
                ?: return MarkerDetailUiState.unavailable(markerId)
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return MarkerDetailUiState.unavailable(markerId)
        }
        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return MarkerDetailUiState.unavailable(markerId)
        val markers = root.optJSONArray("markers") ?: root.optJSONArray("items") ?: return MarkerDetailUiState.unavailable(markerId)
        repeat(markers.length()) { index ->
            val marker = markers.optJSONObject(index) ?: return@repeat
            if (marker.optString("id") == markerId) {
                return marker.toDetailState(context)
            }
        }
        return MarkerDetailUiState.unavailable(markerId)
    }

    private fun JSONObject.toDetailState(context: MarkerDetailSessionContext): MarkerDetailUiState {
        val markerId = optString("id")
        val markerType = markerType()
        val coordinates = pointCoordinates()
        val accountId = optString("accountId").ifBlank { "account-unknown" }
        val markerPolicePhoneId = optString("policePhoneId")
        val canEdit = markerPolicePhoneId.isNotBlank() && markerPolicePhoneId == context.policePhoneId
        val version = optLong("version", 0L)
        return MarkerDetailUiState(
            markerId = markerId,
            markerType = markerType,
            title = markerType.label,
            memo = optString("memo"),
            version = version,
            lon = coordinates?.first,
            lat = coordinates?.second,
            createdByAccountId = accountId,
            securityContextAccountId = if (canEdit) accountId else context.policePhoneId.orEmpty(),
            canManageAllMarkers = false,
            canEditByContext = canEdit,
            policePhoneLabel = markerPolicePhoneId.toSafePolicePhoneLabel(),
            accountLabel = accountId.toSafeAccountLabel(),
            locationLabel = coordinates?.let { (lon, lat) ->
                "${String.format(Locale.US, "%.6f", lat)}, ${String.format(Locale.US, "%.6f", lon)}"
            } ?: "위치 미확인",
            occurredAtLabel = optString("occurredAt").ifBlank { "시각 미확인" },
            versionLabel = version.toVersionLabel(),
            syncLabel = optString("status").toMarkerSyncLabel(),
            mutationStatus = MarkerSaveStatus.Editing,
            photos = photoStates(),
            showDeleteConfirm = false
        )
    }

    private fun JSONObject.markerType(): MarkerType {
        val value = optString("type")
        return MarkerType.entries.firstOrNull { it.apiValue == value } ?: MarkerType.NOTE
    }

    private fun JSONObject.pointCoordinates(): Pair<Double, Double>? {
        val location = optJSONObject("location") ?: optJSONObject("geometry") ?: return null
        if (!location.optString("type").equals("Point", ignoreCase = true)) {
            return null
        }
        val coordinates = location.optJSONArray("coordinates") ?: return null
        if (coordinates.length() < 2) {
            return null
        }
        val lon = coordinates.optDouble(0)
        val lat = coordinates.optDouble(1)
        if (!lon.isFinite() || !lat.isFinite()) {
            return null
        }
        return lon to lat
    }

    private fun JSONObject.photoStates(): List<MarkerDetailPhotoUiState> {
        val photos = optJSONArray("photoSummary") ?: return emptyList()
        return buildList {
            repeat(photos.length()) { index ->
                val photo = photos.optJSONObject(index) ?: return@repeat
                add(
                    MarkerDetailPhotoUiState(
                        photoId = photo.optString("photoId").ifBlank { "photo-$index" },
                        label = "사진 ${index + 1}",
                        status = photo.optString("status").toPhotoStatus(),
                        progress = 1f,
                        contentType = photo.optString("contentType").ifBlank { null },
                        sizeBytes = photo.optLongOrNull("sizeBytes"),
                        attachedAtLabel = photo.optString("attachedAt").ifBlank { null },
                        photoUrl = photo.optString("photoUrl").ifBlank { null },
                        thumbnailUrl = photo.optString("thumbnailUrl").ifBlank { null }
                    )
                )
            }
        }
    }

    private fun String.toPhotoStatus(): MarkerDetailPhotoStatus =
        when (uppercase()) {
            "ATTACHING", "PENDING" -> MarkerDetailPhotoStatus.Attaching
            "DELETING" -> MarkerDetailPhotoStatus.Deleting
            "FAILED", "FAILED_FINAL", "FAILED_RETRYABLE" -> MarkerDetailPhotoStatus.Failed
            else -> MarkerDetailPhotoStatus.Attached
        }
}

private fun String.toSafePolicePhoneLabel(): String =
    if (isBlank()) {
        "작성 단말 미확인"
    } else {
        "작성 단말 확인됨"
    }

private fun String.toSafeAccountLabel(): String =
    if (isBlank() || startsWith("acct-", ignoreCase = true)) {
        "작성자 확인됨"
    } else {
        this
    }

private fun Long.toVersionLabel(): String =
    if (this > 0L) {
        "수정 이력 ${this}회"
    } else {
        "수정 이력 없음"
    }

private fun String.toMarkerSyncLabel(): String =
    when (uppercase()) {
        "ACTIVE", "ACKED", "SYNCED" -> "동기화"
        "PENDING", "PENDING_SYNC" -> "전송 대기"
        "FAILED", "FAILED_RETRYABLE", "FAILED_FINAL" -> "전송 확인 필요"
        else -> ifBlank { "조회됨" }
    }

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) {
        optLong(name)
    } else {
        null
    }
