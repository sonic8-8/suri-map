package com.surimap.feature.search.data

import com.surimap.core.database.LocalMarkerEntity
import com.surimap.core.database.OutboxStatusSummary
import com.surimap.core.incident.IncidentReadRepository
import com.surimap.core.marker.MarkerReadQuery
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.offline.OfflinePackageManifestQuery
import com.surimap.core.offline.OfflinePackageRepository
import com.surimap.core.path.SearchPathQuery
import com.surimap.core.path.SearchPathRepository
import com.surimap.core.searcharea.SearchAreaReadRepository
import com.surimap.feature.search.ui.SearchLayerKind
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.feature.search.ui.SearchMapLayerUiState
import com.surimap.feature.search.ui.SearchMapSyncStatus
import com.surimap.feature.search.ui.SearchMapUiState
import com.surimap.feature.search.ui.SearchMapViewportBounds
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

data class SearchMapSessionContext(
    val incidentId: String?,
    val currentOpId: String?,
    val currentDutyShiftId: String?,
    val currentOpLabel: String? = null,
    val policePhoneId: String? = null,
    val accountId: String? = null
)

class SearchMapStateLoader(
    private val incidentDetail: suspend (String) -> SuriMapApiResponse = { incidentId ->
        IncidentReadRepository().detail(incidentId)
    },
    private val overallSearchArea: suspend (String) -> SuriMapApiResponse = { incidentId ->
        SearchAreaReadRepository().activeOverall(incidentId)
    },
    private val opSearchAreas: suspend (String, String) -> SuriMapApiResponse = { incidentId, opId ->
        SearchAreaReadRepository().list(incidentId = incidentId, opId = opId, status = "ACTIVE")
    },
    private val searchPaths: suspend (SearchPathQuery) -> SuriMapApiResponse = { query ->
        SearchPathRepository().listSearchPaths(query)
    },
    private val liveMarkers: suspend (MarkerReadQuery) -> SuriMapApiResponse = {
        SuriMapApiResponse(statusCode = 404, body = null, errorCode = null)
    },
    private val initialMarkers: suspend (String, String) -> SuriMapApiResponse = { incidentId, policePhoneId ->
        OfflinePackageRepository().manifest(
            OfflinePackageManifestQuery(
                incidentId = incidentId,
                policePhoneId = policePhoneId
            )
        )
    },
    private val outboxSummary: suspend (String, String) -> OutboxStatusSummary? = { _, _ -> null },
    private val pendingMarkers: suspend (String, String) -> List<LocalMarkerEntity> = { _, _ -> emptyList() },
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun load(context: SearchMapSessionContext): SearchMapUiState {
        val areaState = withOpSearchAreas(context, withOverallSearchArea(context, fallback(context)))
        val mapState = withSearchPaths(context, areaState)
        val liveMarkerResult = withLiveMarkers(context, mapState)
        val serverMarkerState =
            if (liveMarkerResult.loaded) {
                liveMarkerResult.state
            } else {
                withInitialMarkers(context, mapState)
            }
        val markerState = withPendingMarkers(context, serverMarkerState)
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return markerState
        val response = runCatching { incidentDetail(incidentId) }.getOrNull() ?: return markerState
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return markerState
        }
        return detailState(context, response.body, markerState)
    }

    fun fallbackForRemember(context: SearchMapSessionContext): SearchMapUiState = fallbackState(context, summary = null)

    suspend fun fallback(context: SearchMapSessionContext): SearchMapUiState {
        return fallbackState(context, summary = context.outboxSummaryOrNull())
    }

    private fun fallbackState(
        context: SearchMapSessionContext,
        summary: OutboxStatusSummary?
    ): SearchMapUiState {
        val incidentTitle = context.incidentId?.takeIf(String::isNotBlank) ?: "선택한 사건"
        val hasCurrentOp = !context.currentOpId.isNullOrBlank()
        val normalUnsentCount = summary?.normalUnsentCount ?: 0
        val oldestPendingMinutes = summary?.oldestPendingClientRequestedAt?.let(::oldestPendingMinutes)
        return SearchMapUiState(
            incidentTitle = incidentTitle,
            missingPersonSummary = "실종자 정보 확인 중",
            opLabel = context.currentOpLabel?.takeIf(String::isNotBlank)
                ?: context.currentOpId?.takeIf(String::isNotBlank)?.let { opId -> "OP $opId" }
                ?: "OP 확인 필요",
            dutyShiftLabel =
            context.currentDutyShiftId
                ?.takeIf(String::isNotBlank)
                ?.let { dutyShiftId -> "DutyShift $dutyShiftId" }
                ?: "DutyShift 확인 필요",
            assignmentLabel = "",
            syncStatus =
            if (normalUnsentCount > 0) {
                SearchMapSyncStatus.Offline
            } else {
                SearchMapSyncStatus.Idle
            },
            lifecycleStatus =
            if (hasCurrentOp) {
                SearchLifecycleStatus.Active
            } else {
                SearchLifecycleStatus.OpRequired
            },
            unsentCount = normalUnsentCount,
            oldestPendingMinutes = oldestPendingMinutes,
            blockedOutboxCount = summary?.finalFailedCount ?: 0,
            elapsedLabel = "00:00",
            movementSummary = "경로 기록 대기",
            layers =
            listOf(
                SearchMapLayerUiState("전체 수색 구역", SearchLayerKind.Overall),
                SearchMapLayerUiState("담당 구역 확인 중", SearchLayerKind.Team, highlighted = true)
            ),
            handoverPrompt = null,
            incidentAlert = null
        )
    }

    private suspend fun withOverallSearchArea(
        context: SearchMapSessionContext,
        fallback: SearchMapUiState
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return fallback
        val response = runCatching { overallSearchArea(incidentId) }.getOrNull() ?: return fallback
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return fallback
        }
        val area = runCatching { JSONObject(response.body) }.getOrNull() ?: return fallback
        val geometry = area.optJSONObject("geometry") ?: return fallback
        val bounds = viewportBounds(area, geometry) ?: return fallback
        return fallback.copy(
            viewportBounds = bounds,
            layers =
            listOf(
                SearchMapLayerUiState(
                    label = "전체 수색 구역",
                    kind = SearchLayerKind.Overall,
                    overlayId = area.optString("id").ifBlank { "overall-search-area" },
                    geoJson = geometry.toString()
                )
            )
        )
    }

    private suspend fun withOpSearchAreas(
        context: SearchMapSessionContext,
        state: SearchMapUiState
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return state
        val opId = context.currentOpId?.takeIf(String::isNotBlank) ?: return state
        val response = runCatching { opSearchAreas(incidentId, opId) }.getOrNull() ?: return state
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return state
        }
        val opLayers = searchAreaLayers(response.body)
        if (opLayers.isEmpty()) {
            return state
        }
        return state.copy(
            assignmentLabel = opLayers.assignmentLabel() ?: state.assignmentLabel,
            layers =
            state.layers
                .filterNot { layer -> layer.kind != SearchLayerKind.Overall && layer.geoJson == null } + opLayers
        )
    }

    private suspend fun withSearchPaths(
        context: SearchMapSessionContext,
        state: SearchMapUiState
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return state
        val opId = context.currentOpId?.takeIf(String::isNotBlank) ?: return state
        val policePhoneId = context.policePhoneId?.takeIf(String::isNotBlank) ?: return state
        val accountId = context.accountId?.takeIf(String::isNotBlank)
        val response =
            runCatching {
                searchPaths(
                    SearchPathQuery(
                        incidentId = incidentId,
                        opId = opId,
                        includeGeometry = true,
                        geometryMode = "RENDER_SIMPLIFIED",
                        limit = 500,
                        sort = "startedAtAsc"
                    )
                )
            }.getOrNull()
                ?: return state
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return state
        }
        val pathLayerResult = searchPathLayers(response.body, accountId, policePhoneId)
        if (pathLayerResult.layers.isEmpty() && pathLayerResult.activePathId.isNullOrBlank()) {
            return state
        }
        val nextState =
            state.copy(
                movementSummary =
                if (pathLayerResult.layers.isEmpty()) {
                    state.movementSummary
                } else {
                    "경로 ${pathLayerResult.pathCount}개 표시"
                },
                layers = state.layers + pathLayerResult.layers,
                lifecycleStatus = pathLayerResult.activeLifecycleStatus ?: state.lifecycleStatus,
                activeSearchPathId = pathLayerResult.activePathId,
                activeSearchPathStartedAtEpochMs = pathLayerResult.activeStartedAtEpochMs
            )
        return nextState.withViewportFromLayers(pathLayerResult.layers)
    }

    private suspend fun withInitialMarkers(
        context: SearchMapSessionContext,
        state: SearchMapUiState
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return state
        val policePhoneId = context.policePhoneId?.takeIf(String::isNotBlank) ?: return state
        val response = runCatching { initialMarkers(incidentId, policePhoneId) }.getOrNull() ?: return state
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return state
        }
        val assignedAreaIds = assignedAreaIds(response.body)
        val areaState = state.withAssignedAreas(assignedAreaIds)
        val markerLayers = initialMarkerLayers(response.body)
        if (markerLayers.isEmpty()) {
            return areaState
        }
        return areaState.copy(layers = areaState.layers + markerLayers).withViewportFromLayers(markerLayers)
    }

    private suspend fun withLiveMarkers(
        context: SearchMapSessionContext,
        state: SearchMapUiState
    ): LiveMarkerLoadResult {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return LiveMarkerLoadResult(state, false)
        val opId = context.currentOpId?.takeIf(String::isNotBlank) ?: return LiveMarkerLoadResult(state, false)
        val response =
            runCatching {
                liveMarkers(
                    MarkerReadQuery(
                        incidentId = incidentId,
                        opId = opId
                    )
                )
            }.getOrNull()
                ?: return LiveMarkerLoadResult(state, false)
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return LiveMarkerLoadResult(state, false)
        }
        val markerLayers = liveMarkerLayers(response.body)
        return LiveMarkerLoadResult(
            state.copy(layers = state.layers + markerLayers).withViewportFromLayers(markerLayers),
            true
        )
    }

    private suspend fun withPendingMarkers(
        context: SearchMapSessionContext,
        state: SearchMapUiState
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return state
        val policePhoneId = context.policePhoneId?.takeIf(String::isNotBlank) ?: return state
        val markerLayers =
            runCatching { pendingMarkers(incidentId, policePhoneId) }
                .getOrDefault(emptyList())
                .pendingMarkerLayers()
        if (markerLayers.isEmpty()) {
            return state
        }
        return state.copy(layers = state.layers + markerLayers).withViewportFromLayers(markerLayers)
    }

    private fun detailState(
        context: SearchMapSessionContext,
        body: String,
        fallback: SearchMapUiState
    ): SearchMapUiState {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return fallback
        return fallback.copy(
            incidentTitle = json.optString("title").ifBlank {
                json.optString("incidentId").ifBlank {
                    json.optString("id").ifBlank { fallback.incidentTitle }
                }
            },
            missingPersonSummary = missingPersonSummary(json.optJSONObject("missingPerson")),
            opLabel = context.currentOpLabel?.takeIf(String::isNotBlank)
                ?: context.currentOpId?.takeIf(String::isNotBlank)?.let { opId -> "OP $opId" }
                ?: fallback.opLabel,
            dutyShiftLabel =
            context.currentDutyShiftId
                ?.takeIf(String::isNotBlank)
                ?.let { dutyShiftId -> "DutyShift $dutyShiftId" }
                ?: fallback.dutyShiftLabel
        )
    }

    private fun missingPersonSummary(missingPerson: JSONObject?): String {
        if (missingPerson == null) {
            return "실종자 정보 없음"
        }
        val displayName =
            missingPerson.optString("displayName")
                .ifBlank { missingPerson.optString("name") }
                .withoutMissingPersonCode()
                .ifBlank { "실종자" }
        val appearanceText = missingPerson.optString("appearanceText")
        return listOf(displayName, appearanceText)
            .filter(String::isNotBlank)
            .joinToString(" · ")
    }

    private suspend fun SearchMapSessionContext.outboxSummaryOrNull(): OutboxStatusSummary? {
        val incidentId = incidentId?.takeIf(String::isNotBlank) ?: return null
        val policePhoneId = policePhoneId?.takeIf(String::isNotBlank) ?: return null
        return runCatching { outboxSummary(incidentId, policePhoneId) }.getOrNull()
    }

    private fun oldestPendingMinutes(clientRequestedAt: Long): Int {
        return ((nowMs() - clientRequestedAt).coerceAtLeast(0L) / MILLIS_PER_MINUTE).toInt()
    }

    private fun searchAreaLayers(body: String): List<SearchMapLayerUiState> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val areas = root.optJSONArray("areas") ?: root.optJSONArray("items") ?: return emptyList()
        return buildList {
            repeat(areas.length()) { index ->
                val area = areas.optJSONObject(index) ?: return@repeat
                val geometry = area.optJSONObject("geometry") ?: return@repeat
                val kind = area.searchLayerKind() ?: return@repeat
                val id = area.optString("id").ifBlank { "op-${kind.name.lowercase()}-$index" }
                add(
                    SearchMapLayerUiState(
                        label = area.labelFor(kind),
                        kind = kind,
                        highlighted = kind == SearchLayerKind.Team,
                        overlayId = id,
                        geoJson = geometry.toString()
                    )
                )
            }
        }
    }

    private fun initialMarkerLayers(body: String): List<SearchMapLayerUiState> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val markers = root.optJSONArray("initialMarkers") ?: root.optJSONArray("markers") ?: return emptyList()
        return buildList {
            repeat(markers.length()) { index ->
                val marker = markers.optJSONObject(index) ?: return@repeat
                val status = marker.optString("status").uppercase()
                if (status == "DELETED") {
                    return@repeat
                }
                val location = marker.optJSONObject("location") ?: marker.optJSONObject("geometry") ?: return@repeat
                if (!location.optString("type").equals("Point", ignoreCase = true)) {
                    return@repeat
                }
                add(
                    SearchMapLayerUiState(
                        label = marker.markerLabel(),
                        kind = SearchLayerKind.Marker,
                        overlayId = marker.optString("id").ifBlank { "initial-marker-$index" },
                        geoJson = location.toString(),
                        markerType = marker.optString("type").ifBlank { null },
                        supportRequestType = marker.optString("supportRequestType").ifBlank { null }
                    )
                )
            }
        }
    }

    private fun assignedAreaIds(body: String): Set<String> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptySet()
        val areas = root.optJSONArray("assignedAreas") ?: return emptySet()
        return buildSet {
            repeat(areas.length()) { index ->
                val area = areas.optJSONObject(index) ?: return@repeat
                val areaId = area.optString("areaId").ifBlank { area.optString("id") }
                if (areaId.isNotBlank()) {
                    add(areaId)
                }
            }
        }
    }

    private fun SearchMapUiState.withAssignedAreas(areaIds: Set<String>): SearchMapUiState {
        if (areaIds.isEmpty()) {
            return this
        }
        val nextLayers =
            layers.map { layer ->
                if (layer.kind != SearchLayerKind.Team) {
                    layer
                } else {
                    val assigned = layer.overlayId != null && areaIds.contains(layer.overlayId)
                    layer.copy(highlighted = assigned, assignedToCurrentPhone = assigned)
                }
            }
        val assignedLabel =
            nextLayers
                .firstOrNull { layer -> layer.kind == SearchLayerKind.Team && layer.assignedToCurrentPhone }
                ?.label
                ?.takeIf(String::isNotBlank)
        return copy(
            assignmentLabel = assignedLabel ?: assignmentLabel,
            layers = nextLayers
        )
    }

    private fun liveMarkerLayers(body: String): List<SearchMapLayerUiState> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val markers = root.optJSONArray("markers") ?: root.optJSONArray("items") ?: return emptyList()
        return buildList {
            repeat(markers.length()) { index ->
                val marker = markers.optJSONObject(index) ?: return@repeat
                val status = marker.optString("status").uppercase()
                if (status == "DELETED") {
                    return@repeat
                }
                val location = marker.optJSONObject("location") ?: marker.optJSONObject("geometry") ?: return@repeat
                if (!location.optString("type").equals("Point", ignoreCase = true)) {
                    return@repeat
                }
                add(
                    SearchMapLayerUiState(
                        label = marker.markerLabel(),
                        kind = SearchLayerKind.Marker,
                        overlayId = marker.optString("id").ifBlank { "live-marker-$index" },
                        geoJson = location.toString(),
                        markerType = marker.optString("type").ifBlank { null },
                        supportRequestType = marker.optString("supportRequestType").ifBlank { null }
                    )
                )
            }
        }
    }

    private fun List<LocalMarkerEntity>.pendingMarkerLayers(): List<SearchMapLayerUiState> =
        map { marker ->
            SearchMapLayerUiState(
                label = "${marker.type.markerTypeLabel()} · 전송 대기",
                kind = SearchLayerKind.Marker,
                overlayId = marker.localMarkerId,
                geoJson = """{"type":"Point","coordinates":[${marker.lon},${marker.lat}]}""",
                markerType = marker.type,
                supportRequestType = marker.supportRequestType
            )
        }

    private fun List<SearchMapLayerUiState>.assignmentLabel(): String? =
        (firstOrNull { layer -> layer.kind == SearchLayerKind.Team }
            ?: firstOrNull { layer -> layer.kind == SearchLayerKind.Unit })
            ?.label
            ?.takeIf(String::isNotBlank)

    private fun searchPathLayers(
        body: String,
        currentAccountId: String?,
        currentPolicePhoneId: String
    ): SearchPathLayerResult {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return SearchPathLayerResult()
        val paths = root.optJSONArray("paths") ?: root.optJSONArray("items") ?: return SearchPathLayerResult()
        var activePathId: String? = null
        var activeStartedAtEpochMs: Long? = null
        var activeLifecycleStatus: SearchLifecycleStatus? = null
        var pathCount = 0
        val layers = buildList {
            repeat(paths.length()) { index ->
                val path = paths.optJSONObject(index) ?: return@repeat
                val status = path.optString("status").uppercase()
                val active = status in setOf("ACTIVE", "RECORDING", "PAUSED")
                val pathAccountId = path.optString("accountId").takeIf(String::isNotBlank)
                val belongsToCurrentActor =
                    if (!currentAccountId.isNullOrBlank() && !pathAccountId.isNullOrBlank()) {
                        pathAccountId.equals(currentAccountId, ignoreCase = true)
                    } else {
                        path.optString("policePhoneId").equals(currentPolicePhoneId, ignoreCase = true)
                    }
                val activeForCurrentActor = active && belongsToCurrentActor
                if (activeForCurrentActor) {
                    activePathId = path.optString("id").takeIf(String::isNotBlank) ?: activePathId
                    activeStartedAtEpochMs = path.instantMillis("startedAt") ?: activeStartedAtEpochMs
                    activeLifecycleStatus =
                        if (status == "PAUSED") {
                            SearchLifecycleStatus.Paused
                        } else {
                            SearchLifecycleStatus.Active
                        }
                }
                val geometry = path.optJSONObject("geometry") ?: return@repeat
                if (!geometry.optString("type").equals("LineString", ignoreCase = true)) {
                    return@repeat
                }
                val pathId = path.optString("id").ifBlank { "search-path-$index" }
                pathCount += 1
                add(
                    SearchMapLayerUiState(
                        label =
                        when {
                            activeForCurrentActor -> "현재 경로"
                            active -> "다른 대원 경로"
                            else -> "기존 경로"
                        },
                        kind = SearchLayerKind.Path,
                        highlighted = activeForCurrentActor,
                        overlayId = pathId,
                        geoJson = geometry.toString()
                    )
                )
                geometry.latestLineStringPoint()?.let { latestPoint ->
                    add(
                        SearchMapLayerUiState(
                            label =
                            when {
                                activeForCurrentActor -> "현재 위치"
                                active -> "다른 대원 위치"
                                else -> "기존 위치"
                            },
                            kind = SearchLayerKind.CurrentLocation,
                            highlighted = activeForCurrentActor,
                            overlayId = "$pathId-latest-location",
                            geoJson = latestPoint.toString()
                        )
                    )
                }
            }
        }
        return SearchPathLayerResult(
            layers = layers,
            pathCount = pathCount,
            activePathId = activePathId,
            activeStartedAtEpochMs = activeStartedAtEpochMs,
            activeLifecycleStatus = activeLifecycleStatus
        )
    }

    private data class SearchPathLayerResult(
        val layers: List<SearchMapLayerUiState> = emptyList(),
        val pathCount: Int = 0,
        val activePathId: String? = null,
        val activeStartedAtEpochMs: Long? = null,
        val activeLifecycleStatus: SearchLifecycleStatus? = null
    )

    private fun JSONObject.searchLayerKind(): SearchLayerKind? {
        val areaLevel =
            optString("areaLevel")
                .ifBlank { optString("area_level") }
                .uppercase()
        return when (areaLevel) {
            "UNIT" -> SearchLayerKind.Unit
            "TEAM" -> SearchLayerKind.Team
            "OVERALL" -> null
            else ->
                if (
                    optString("parentAreaId").isNotBlank() ||
                    optString("parent_area_id").isNotBlank()
                ) {
                    SearchLayerKind.Team
                } else {
                    SearchLayerKind.Unit
                }
        }
    }

    private fun JSONObject.labelFor(kind: SearchLayerKind): String {
        return optString("name")
            .ifBlank { optString("label") }
            .ifBlank { optString("displayName") }
            .ifBlank {
                when (kind) {
                    SearchLayerKind.Overall -> "전체 수색 구역"
                    SearchLayerKind.Unit -> "부대 수색 구역"
                    SearchLayerKind.Team -> "팀 담당 구역"
                    SearchLayerKind.Path -> "수색 경로"
                    SearchLayerKind.Marker -> "마커"
                    SearchLayerKind.CurrentLocation -> "현재 위치"
                }
            }
    }

    private fun JSONObject.markerLabel(): String {
        return optString("label")
            .ifBlank { optString("displayName") }
            .ifBlank {
                optString("type").markerTypeLabel()
            }
    }

    private fun JSONObject.instantMillis(name: String): Long? {
        val value =
            optString(name).ifBlank {
                when (name) {
                    "startedAt" -> optString("started_at")
                    "endedAt" -> optString("ended_at")
                    else -> ""
                }
            }
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private fun JSONObject.latestLineStringPoint(): JSONObject? {
        if (!optString("type").equals("LineString", ignoreCase = true)) {
            return null
        }
        val coordinates = optJSONArray("coordinates") ?: return null
        for (index in coordinates.length() - 1 downTo 0) {
            val point = coordinates.optJSONArray(index) ?: continue
            if (point.length() < 2 || point.opt(0) !is Number || point.opt(1) !is Number) {
                continue
            }
            val longitude = point.optDouble(0)
            val latitude = point.optDouble(1)
            if (!longitude.isFinite() || !latitude.isFinite()) {
                continue
            }
            return JSONObject()
                .put("type", "Point")
                .put("coordinates", JSONArray().put(longitude).put(latitude))
        }
        return null
    }

    private fun String.markerTypeLabel(): String =
        when (uppercase()) {
            "CLUE" -> "단서"
            "PERSON_FOUND" -> "실종자 발견"
            "FIELD_CONDITION" -> "현장 상태"
            "SUPPORT_REQUEST" -> "지원 요청"
            "NOTE" -> "메모"
            else -> "마커"
        }

    private fun String.withoutMissingPersonCode(): String =
        replace(MISSING_PERSON_CODE_TEXT, "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim(' ', '·', '-', '_')

    private fun viewportBounds(area: JSONObject, geometry: JSONObject): SearchMapViewportBounds? {
        area.optJSONArray("bbox")?.let { bbox ->
            if (bbox.length() >= 4) {
                return SearchMapViewportBounds(
                    south = bbox.optDouble(1),
                    west = bbox.optDouble(0),
                    north = bbox.optDouble(3),
                    east = bbox.optDouble(2)
                ).takeIf { it.hasFiniteEdges() }
            }
        }

        return viewportBounds(geometry)
    }

    private fun viewportBounds(geometry: JSONObject): SearchMapViewportBounds? {
        val points = mutableListOf<Pair<Double, Double>>()
        geometry.optJSONArray("coordinates")?.collectPositions(points)
        return points.toViewportBounds()
    }

    private fun SearchMapUiState.withViewportFromLayers(
        candidateLayers: List<SearchMapLayerUiState>
    ): SearchMapUiState {
        if (viewportBounds != null) {
            return this
        }
        val points = mutableListOf<Pair<Double, Double>>()
        candidateLayers.forEach { layer ->
            val geoJson = layer.geoJson?.takeIf(String::isNotBlank) ?: return@forEach
            val geometry = runCatching { JSONObject(geoJson) }.getOrNull() ?: return@forEach
            geometry.optJSONArray("coordinates")?.collectPositions(points)
        }
        return points.toViewportBounds()?.let { bounds -> copy(viewportBounds = bounds) } ?: this
    }

    private fun List<Pair<Double, Double>>.toViewportBounds(): SearchMapViewportBounds? {
        if (isEmpty()) {
            return null
        }
        val longitudes = map { it.first }
        val latitudes = map { it.second }
        val south = latitudes.minOrNull() ?: return null
        val west = longitudes.minOrNull() ?: return null
        val north = latitudes.maxOrNull() ?: return null
        val east = longitudes.maxOrNull() ?: return null
        val latDelta = if (south == north) POINT_VIEWPORT_DELTA else 0.0
        val lonDelta = if (west == east) POINT_VIEWPORT_DELTA else 0.0
        return SearchMapViewportBounds(
            south = south - latDelta,
            west = west - lonDelta,
            north = north + latDelta,
            east = east + lonDelta
        ).takeIf { it.hasFiniteEdges() }
    }

    private fun JSONArray.collectPositions(points: MutableList<Pair<Double, Double>>) {
        if (length() >= 2 && opt(0) is Number && opt(1) is Number) {
            val longitude = optDouble(0)
            val latitude = optDouble(1)
            if (longitude.isFinite() && latitude.isFinite()) {
                points.add(longitude to latitude)
            }
            return
        }
        repeat(length()) { index ->
            optJSONArray(index)?.collectPositions(points)
        }
    }

    private fun SearchMapViewportBounds.hasFiniteEdges(): Boolean {
        return south.isFinite() && west.isFinite() && north.isFinite() && east.isFinite()
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val POINT_VIEWPORT_DELTA = 0.003
        val MISSING_PERSON_CODE_TEXT = Regex("""\b[A-Z]\d+-[가-힣A-Za-z0-9]+-\d+\b""")
    }
}

private data class LiveMarkerLoadResult(
    val state: SearchMapUiState,
    val loaded: Boolean
)
