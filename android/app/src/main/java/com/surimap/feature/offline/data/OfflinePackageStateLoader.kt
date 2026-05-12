package com.surimap.feature.offline.data

import com.surimap.core.network.SuriMapNetworkException
import com.surimap.core.offline.OfflinePackageManifestQuery
import com.surimap.core.offline.OfflinePackageRepository
import com.surimap.feature.offline.ui.OfflinePackageItemUiState
import com.surimap.feature.offline.ui.OfflinePackageUiState
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class OfflinePackageStateLoader(
    private val repository: OfflinePackageRepository = OfflinePackageRepository(),
    private val incidentId: String,
    private val policePhoneId: String,
    private val knownManifestRevision: Long? = null
) {
    suspend fun load(): OfflinePackageUiState {
        if (incidentId.isBlank() || policePhoneId.isBlank()) {
            return OfflinePackageUiState.permissionDenied()
        }
        return try {
            val response =
                repository.manifest(
                    OfflinePackageManifestQuery(
                        incidentId = incidentId,
                        policePhoneId = policePhoneId,
                        knownManifestRevision = knownManifestRevision
                    )
                )
            when {
                response.isSuccessful -> manifestState(response.body)
                response.errorCode in PERMISSION_ERROR_CODES ->
                    OfflinePackageUiState.permissionDenied(incidentTitle = incidentId)
                else -> OfflinePackageUiState.unavailable(incidentTitle = incidentId)
            }
        } catch (_: SuriMapNetworkException) {
            OfflinePackageUiState.offline(incidentTitle = incidentId)
        } catch (_: JSONException) {
            OfflinePackageUiState.unavailable(incidentTitle = incidentId)
        }
    }

    private fun manifestState(body: String?): OfflinePackageUiState {
        if (body.isNullOrBlank()) {
            return OfflinePackageUiState.unavailable(incidentTitle = incidentId)
        }
        val json = JSONObject(body)
        val manifestRevision = json.optInt("manifestVersion", knownManifestRevision?.toInt() ?: 0)
        val incidentTitle =
            json.optJSONObject("incident")
                ?.optString("title")
                ?.takeIf(String::isNotBlank)
                ?: incidentId
        return OfflinePackageUiState.manifestLoaded(
            incidentTitle = incidentTitle,
            manifestRevision = manifestRevision,
            knownManifestRevision = knownManifestRevision?.toInt(),
            packageItems = packageItemStates(json.optJSONArray("packageItems") ?: JSONArray())
        )
    }

    private fun packageItemStates(items: JSONArray): List<OfflinePackageItemUiState> {
        val statusesByLabel = linkedMapOf<String, MutableList<String>>()
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            val label = labelForItemType(item.optString("itemType")) ?: return@repeat
            statusesByLabel.getOrPut(label) { mutableListOf() }.add(item.optString("status"))
        }
        return OfflinePackageUiState.defaultPackageItems().map { defaultItem ->
            val statuses = statusesByLabel[defaultItem.label].orEmpty()
            if (statuses.isEmpty()) {
                defaultItem
            } else {
                defaultItem.copy(
                    progress = progressFor(statuses),
                    statusLabel = statusLabelFor(statuses),
                    failed = statuses.any { status -> status == "FAILED" }
                )
            }
        }
    }

    private fun labelForItemType(itemType: String): String? =
        when (itemType) {
            "INCIDENT_META" -> "사건 메타"
            "MISSING_PERSON_CACHE" -> "실종자"
            "OP_LIST" -> "OP"
            "ASSIGNED_AREA" -> "구역"
            "INITIAL_MARKER" -> "마커"
            "OVERALL_SEARCH_AREA" -> "전체 수색 구역"
            "TILE" -> "타일"
            else -> null
        }

    private fun progressFor(statuses: List<String>): Float {
        val completed = statuses.count { status -> status == "DOWNLOADED" || status == "SKIPPED" }
        return when {
            statuses.any { status -> status == "FAILED" } ->
                (completed.toFloat() / statuses.size).coerceAtLeast(0.25f)
            completed == statuses.size -> 1f
            completed > 0 -> completed.toFloat() / statuses.size
            else -> 0f
        }
    }

    private fun statusLabelFor(statuses: List<String>): String =
        when {
            statuses.any { status -> status == "FAILED" } -> "실패"
            statuses.all { status -> status == "DOWNLOADED" || status == "SKIPPED" } -> "완료"
            statuses.any { status -> status == "DOWNLOADED" || status == "SKIPPED" } -> "부분 완료"
            else -> "대기"
        }

    private companion object {
        val PERMISSION_ERROR_CODES =
            setOf(
                "police_phone_required",
                "police_phone_not_registered",
                "police_phone_not_assigned",
                "incident_access_denied",
                "team_not_assigned"
            )
    }
}
