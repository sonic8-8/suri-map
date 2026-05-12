package com.surimap.feature.offline.data

import com.surimap.core.network.SuriMapNetworkException
import com.surimap.core.offline.OfflinePackageInstallationStatus
import com.surimap.core.offline.OfflinePackageItemStatus
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
    private val knownManifestRevision: Long? = null,
    private val localInstallationStatus: suspend () -> OfflinePackageInstallationStatus? = { null },
    private val localPackageItems: suspend (String) -> List<OfflinePackageItemStatus> = { emptyList() }
) {
    suspend fun load(): OfflinePackageUiState {
        if (incidentId.isBlank() || policePhoneId.isBlank()) {
            return OfflinePackageUiState.permissionDenied()
        }
        return try {
            val localStatus = localInstallationStatus()
            val knownRevision = localStatus?.manifestVersion?.toLong() ?: knownManifestRevision
            val response =
                repository.manifest(
                    OfflinePackageManifestQuery(
                        incidentId = incidentId,
                        policePhoneId = policePhoneId,
                        knownManifestRevision = knownRevision
                    )
                )
            when {
                response.isSuccessful -> manifestState(response.body, localStatus, knownRevision)
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

    private suspend fun manifestState(
        body: String?,
        localStatus: OfflinePackageInstallationStatus?,
        knownRevision: Long?
    ): OfflinePackageUiState {
        if (body.isNullOrBlank()) {
            return OfflinePackageUiState.unavailable(incidentTitle = incidentId)
        }
        val json = JSONObject(body)
        val manifestId = json.optString("manifestId").takeIf(String::isNotBlank).orEmpty()
        val manifestRevision = json.optInt("manifestVersion", knownRevision?.toInt() ?: 0)
        val incidentTitle =
            json.optJSONObject("incident")
                ?.optString("title")
                ?.takeIf(String::isNotBlank)
                ?: incidentId
        if (localStatus.isReadyForManifest(manifestRevision)) {
            return OfflinePackageUiState.ready(
                incidentTitle = incidentTitle,
                manifestRevision = manifestRevision
            )
        }
        val localItems = manifestId.takeIf(String::isNotBlank)
            ?.let { id -> localPackageItems(id) }
            .orEmpty()
        return OfflinePackageUiState.manifestLoaded(
            incidentTitle = incidentTitle,
            manifestRevision = manifestRevision,
            knownManifestRevision = knownRevision?.toInt(),
            packageItems = if (localItems.isNotEmpty()) {
                packageItemStates(localItems)
            } else {
                packageItemStates(json.optJSONArray("packageItems") ?: JSONArray())
            }
        )
    }

    private fun packageItemStates(items: JSONArray): List<OfflinePackageItemUiState> {
        val progressesByLabel = linkedMapOf<String, MutableList<ItemProgress>>()
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            val label = labelForItemType(item.optString("itemType")) ?: return@repeat
            progressesByLabel.getOrPut(label) { mutableListOf() }.add(
                ItemProgress(status = item.optString("status"))
            )
        }
        return packageItemStates(progressesByLabel)
    }

    private fun packageItemStates(
        items: List<OfflinePackageItemStatus>
    ): List<OfflinePackageItemUiState> {
        val progressesByLabel = linkedMapOf<String, MutableList<ItemProgress>>()
        items.forEach { item ->
            val label = labelForItemType(item.itemType) ?: return@forEach
            progressesByLabel.getOrPut(label) { mutableListOf() }.add(
                ItemProgress(
                    status = item.status,
                    bytesTotal = item.bytesTotal,
                    bytesDownloaded = item.bytesDownloaded
                )
            )
        }
        return packageItemStates(progressesByLabel)
    }

    private fun packageItemStates(
        progressesByLabel: Map<String, List<ItemProgress>>
    ): List<OfflinePackageItemUiState> {
        return OfflinePackageUiState.defaultPackageItems().map { defaultItem ->
            val progresses = progressesByLabel[defaultItem.label].orEmpty()
            if (progresses.isEmpty()) {
                defaultItem
            } else {
                defaultItem.copy(
                    progress = progressFor(progresses),
                    statusLabel = statusLabelFor(progresses),
                    failed = progresses.any { item -> item.status == "FAILED" }
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

    private fun progressFor(items: List<ItemProgress>): Float {
        val completed = items.count(ItemProgress::isComplete)
        val itemProgress = items.map(ItemProgress::progress)
        return when {
            items.any { item -> item.status == "FAILED" } ->
                itemProgress.average().toFloat().coerceAtLeast(0.25f)
            completed == items.size -> 1f
            itemProgress.any { progress -> progress > 0f } ->
                itemProgress.average().toFloat().coerceIn(0f, 1f)
            else -> 0f
        }
    }

    private fun statusLabelFor(items: List<ItemProgress>): String =
        when {
            items.any { item -> item.status == "FAILED" } -> "실패"
            items.all(ItemProgress::isComplete) -> "완료"
            items.any(ItemProgress::hasByteProgress) -> "다운로드 중"
            items.any(ItemProgress::isComplete) -> "부분 완료"
            else -> "대기"
        }

    private fun OfflinePackageInstallationStatus?.isReadyForManifest(manifestRevision: Int): Boolean {
        return this != null &&
            manifestVersion == manifestRevision &&
            status == "READY" &&
            readyForOfflineUse &&
            totalItems > 0 &&
            completedItems == totalItems &&
            failedItems == 0
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

    private data class ItemProgress(
        val status: String,
        val bytesTotal: Long? = null,
        val bytesDownloaded: Long? = null
    ) {
        val isComplete: Boolean = status == "DOWNLOADED" || status == "SKIPPED"
        val hasByteProgress: Boolean =
            bytesTotal != null && bytesTotal > 0L && (bytesDownloaded ?: 0L) > 0L
        val progress: Float =
            when {
                isComplete -> 1f
                bytesTotal != null && bytesTotal > 0L ->
                    ((bytesDownloaded ?: 0L).toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
                else -> 0f
            }
    }
}
