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
import com.surimap.feature.search.ui.SearchMapAssignmentUiState
import com.surimap.feature.search.ui.SearchMapLayerVisualStyle
import com.surimap.feature.search.ui.SearchMapLayerUiState
import com.surimap.feature.search.ui.SearchMapSyncStatus
import com.surimap.feature.search.ui.SearchMapUiState
import com.surimap.feature.search.ui.SearchMapViewportBounds
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

data class SearchMapSessionContext(
    val incidentId: String?,
    val currentOpId: String?,
    val currentDutyShiftId: String?,
    val currentOpLabel: String? = null,
    val policePhoneId: String? = null,
    val accountId: String? = null,
    val apiBaseUrl: String? = null,
    val objectStorageBaseUrl: String? = null
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
    private val mapRevisions: suspend (SearchMapSessionContext) -> SuriMapApiResponse = {
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
    private val responseCache: SearchMapResponseCache? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun load(context: SearchMapSessionContext): SearchMapUiState {
        val areaColorRegistry = WebAreaColorRegistry(context.incidentId)
        val initialState = cached(context, areaColorRegistry) ?: fallback(context)
        val revisionSnapshot = revisionSnapshot(context)
        val areaState =
            withOpSearchAreas(
                context,
                withOverallSearchArea(context, initialState, revisionSnapshot, areaColorRegistry),
                revisionSnapshot,
                areaColorRegistry
            )
        val mapState = withSearchPaths(context, areaState, revisionSnapshot)
        val liveMarkerResult = withLiveMarkers(context, mapState, revisionSnapshot)
        val serverMarkerState =
            if (liveMarkerResult.loaded) {
                liveMarkerResult.state
            } else {
                withInitialMarkers(context, mapState, revisionSnapshot)
            }
        val markerState = withPendingMarkers(context, serverMarkerState)
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return markerState
        if (!shouldFetch(CACHE_SOURCE_INCIDENT_DETAIL, revisionSnapshot)) {
            return markerState
        }
        val response = runCatching { incidentDetail(incidentId) }.getOrNull() ?: return markerState
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return markerState
        }
        cacheResponse(context, CACHE_SOURCE_INCIDENT_DETAIL, response.body, revisionSnapshot)
        return detailState(context, response.body, markerState)
    }

    suspend fun cached(context: SearchMapSessionContext): SearchMapUiState? =
        cached(context, WebAreaColorRegistry(context.incidentId))

    private suspend fun cached(
        context: SearchMapSessionContext,
        areaColorRegistry: WebAreaColorRegistry
    ): SearchMapUiState? {
        val cache = responseCache ?: return null
        var state = fallback(context)
        var cacheHit = false
        cache.read(context, CACHE_SOURCE_OVERALL_SEARCH_AREA)?.let { body ->
            cacheHit = true
            state = withOverallSearchAreaBody(state, body, areaColorRegistry)
        }
        cache.read(context, CACHE_SOURCE_OP_SEARCH_AREAS)?.let { body ->
            cacheHit = true
            state = withOpSearchAreasBody(state, body, areaColorRegistry)
        }
        cache.read(context, CACHE_SOURCE_SEARCH_PATHS)?.let { body ->
            cacheHit = true
            state = withSearchPathsBody(context, state, body)
        }
        val liveMarkerBody = cache.read(context, CACHE_SOURCE_LIVE_MARKERS)
        if (liveMarkerBody != null) {
            cacheHit = true
            state = withLiveMarkersBody(state, liveMarkerBody).state
        } else {
            cache.read(context, CACHE_SOURCE_INITIAL_MARKERS)?.let { body ->
                cacheHit = true
                state = withInitialMarkersBody(state, body)
            }
        }
        state = withPendingMarkers(context, state)
        cache.read(context, CACHE_SOURCE_INCIDENT_DETAIL)?.let { body ->
            cacheHit = true
            state = detailState(context, body, state)
        }
        return if (cacheHit) state else null
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
        fallback: SearchMapUiState,
        revisionSnapshot: MapRevisionSnapshot,
        areaColorRegistry: WebAreaColorRegistry
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return fallback
        if (!shouldFetch(CACHE_SOURCE_OVERALL_SEARCH_AREA, revisionSnapshot)) {
            return fallback
        }
        val response = runCatching { overallSearchArea(incidentId) }.getOrNull() ?: return fallback
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return fallback
        }
        cacheResponse(context, CACHE_SOURCE_OVERALL_SEARCH_AREA, response.body, revisionSnapshot)
        return withOverallSearchAreaBody(fallback, response.body, areaColorRegistry)
    }

    private fun withOverallSearchAreaBody(
        fallback: SearchMapUiState,
        body: String,
        areaColorRegistry: WebAreaColorRegistry
    ): SearchMapUiState {
        val area = runCatching { JSONObject(body) }.getOrNull() ?: return fallback
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
                    geoJson = geometry.toString(),
                    visualStyle =
                            searchAreaVisualStyle(
                                areaId = area.optString("id").ifBlank { "overall-search-area" },
                                colorToken = area.areaColorToken(),
                                kind = SearchLayerKind.Overall,
                                highlighted = false,
                                areaColorRegistry = areaColorRegistry
                        )
                )
            ) + fallback.layers.filterNot { layer ->
                layer.kind == SearchLayerKind.Overall ||
                    (layer.geoJson == null && layer.kind in setOf(SearchLayerKind.Unit, SearchLayerKind.Team))
            }
        )
    }

    private suspend fun withOpSearchAreas(
        context: SearchMapSessionContext,
        state: SearchMapUiState,
        revisionSnapshot: MapRevisionSnapshot,
        areaColorRegistry: WebAreaColorRegistry
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return state
        val opId = context.currentOpId?.takeIf(String::isNotBlank) ?: return state
        if (!shouldFetch(CACHE_SOURCE_OP_SEARCH_AREAS, revisionSnapshot)) {
            return state
        }
        val response = runCatching { opSearchAreas(incidentId, opId) }.getOrNull() ?: return state
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return state
        }
        cacheResponse(context, CACHE_SOURCE_OP_SEARCH_AREAS, response.body, revisionSnapshot)
        return withOpSearchAreasBody(state, response.body, areaColorRegistry)
    }

    private fun withOpSearchAreasBody(
        state: SearchMapUiState,
        body: String,
        areaColorRegistry: WebAreaColorRegistry
    ): SearchMapUiState {
        val opLayers = searchAreaLayers(body, areaColorRegistry)
        if (opLayers.isEmpty()) {
            return state
        }
        return state.copy(
            assignmentLabel = opLayers.assignmentLabel() ?: state.assignmentLabel,
            layers =
                state.layers
                    .filterNot { layer ->
                        layer.kind in setOf(SearchLayerKind.Unit, SearchLayerKind.Team) ||
                            (layer.kind != SearchLayerKind.Overall && layer.geoJson == null)
                    } + opLayers
        )
    }

    private suspend fun withSearchPaths(
        context: SearchMapSessionContext,
        state: SearchMapUiState,
        revisionSnapshot: MapRevisionSnapshot
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return state
        val opId = context.currentOpId?.takeIf(String::isNotBlank) ?: return state
        context.accountId?.takeIf(String::isNotBlank) ?: return state
        if (!shouldFetch(CACHE_SOURCE_SEARCH_PATHS, revisionSnapshot)) {
            val cachedBody = responseCache?.read(context, CACHE_SOURCE_SEARCH_PATHS)
            return if (cachedBody.isNullOrBlank()) {
                state
            } else {
                withSearchPathsBody(context, state, cachedBody)
            }
        }
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
        cacheResponse(context, CACHE_SOURCE_SEARCH_PATHS, response.body, revisionSnapshot)
        return withSearchPathsBody(context, state, response.body)
    }

    private fun withSearchPathsBody(
        context: SearchMapSessionContext,
        state: SearchMapUiState,
        body: String
    ): SearchMapUiState {
        val accountId = context.accountId?.takeIf(String::isNotBlank) ?: return state
        val pathLayerResult = searchPathLayers(
            body = body,
            currentAccountId = accountId,
            areaColorCandidates = state.routeAreaColorCandidates()
        )
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
                layers = state.layers.withoutSearchPathLayers() + pathLayerResult.layers,
                lifecycleStatus = pathLayerResult.activeLifecycleStatus ?: state.lifecycleStatus,
                activeSearchPathId = pathLayerResult.activePathId,
                activeSearchPathStartedAtEpochMs = pathLayerResult.activeStartedAtEpochMs
            )
        return nextState.withViewportFromLayers(pathLayerResult.layers)
    }

    private suspend fun withInitialMarkers(
        context: SearchMapSessionContext,
        state: SearchMapUiState,
        revisionSnapshot: MapRevisionSnapshot
    ): SearchMapUiState {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return state
        val policePhoneId = context.policePhoneId?.takeIf(String::isNotBlank) ?: return state
        if (!shouldFetch(CACHE_SOURCE_INITIAL_MARKERS, revisionSnapshot)) {
            return state
        }
        val response = runCatching { initialMarkers(incidentId, policePhoneId) }.getOrNull() ?: return state
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return state
        }
        cacheResponse(context, CACHE_SOURCE_INITIAL_MARKERS, response.body, revisionSnapshot)
        return withInitialMarkersBody(state, response.body)
    }

    private fun withInitialMarkersBody(
        state: SearchMapUiState,
        body: String
    ): SearchMapUiState {
        val assignedAreaIds = assignedAreaIds(body)
        val areaState =
            state.withAssignedAreas(assignedAreaIds).let { assignedState ->
                assignedState.copy(layers = assignedState.layers.withoutMarkerLayers())
            }
        val markerLayers = initialMarkerLayers(body)
        if (markerLayers.isEmpty()) {
            return areaState
        }
        return areaState.copy(layers = areaState.layers.withoutMarkerLayers() + markerLayers)
            .withViewportFromLayers(markerLayers)
    }

    private suspend fun withLiveMarkers(
        context: SearchMapSessionContext,
        state: SearchMapUiState,
        revisionSnapshot: MapRevisionSnapshot
    ): LiveMarkerLoadResult {
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return LiveMarkerLoadResult(state, false)
        val opId = context.currentOpId?.takeIf(String::isNotBlank) ?: return LiveMarkerLoadResult(state, false)
        if (!shouldFetch(CACHE_SOURCE_LIVE_MARKERS, revisionSnapshot)) {
            return LiveMarkerLoadResult(state, true)
        }
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
        cacheResponse(context, CACHE_SOURCE_LIVE_MARKERS, response.body, revisionSnapshot)
        return withLiveMarkersBody(state, response.body)
    }

    private suspend fun revisionSnapshot(context: SearchMapSessionContext): MapRevisionSnapshot {
        val localRevisions = responseCache?.revisions(context).orEmpty()
        val incidentId = context.incidentId?.takeIf(String::isNotBlank)
            ?: return MapRevisionSnapshot(remote = null, local = localRevisions)
        val response = runCatching { mapRevisions(context.copy(incidentId = incidentId)) }.getOrNull()
        val remote =
            response
                ?.takeIf { it.isSuccessful && !it.body.isNullOrBlank() }
                ?.body
                ?.let(::parseRevisionMap)
                ?.takeIf(Map<String, String>::isNotEmpty)
        return MapRevisionSnapshot(remote = remote, local = localRevisions)
    }

    private fun shouldFetch(
        source: String,
        revisionSnapshot: MapRevisionSnapshot
    ): Boolean {
        val remoteRevision = revisionSnapshot.remote?.get(source) ?: return true
        val localRevision = revisionSnapshot.local[source] ?: return true
        return localRevision != remoteRevision
    }

    private suspend fun cacheResponse(
        context: SearchMapSessionContext,
        source: String,
        body: String,
        revisionSnapshot: MapRevisionSnapshot
    ) {
        responseCache?.upsertIfChanged(
            context = context,
            source = source,
            body = body,
            sourceRevision = revisionSnapshot.remote?.get(source)
        )
    }

    private fun parseRevisionMap(body: String): Map<String, String> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyMap()
        val sources = root.optJSONArray("sources") ?: return emptyMap()
        return buildMap {
            repeat(sources.length()) { index ->
                val source = sources.optJSONObject(index) ?: return@repeat
                val sourceName =
                    source.optString("source").takeIf(String::isNotBlank) ?: return@repeat
                val revision =
                    source.optString("revision").takeIf(String::isNotBlank) ?: return@repeat
                put(sourceName, revision)
            }
        }
    }

    private fun withLiveMarkersBody(
        state: SearchMapUiState,
        body: String
    ): LiveMarkerLoadResult {
        val markerLayers = liveMarkerLayers(body)
        return LiveMarkerLoadResult(
            state.copy(layers = state.layers.withoutMarkerLayers() + markerLayers)
                .withViewportFromLayers(markerLayers),
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

    private fun List<SearchMapLayerUiState>.withoutSearchPathLayers(): List<SearchMapLayerUiState> =
        filterNot { layer ->
            layer.kind == SearchLayerKind.Path ||
                (layer.kind == SearchLayerKind.CurrentLocation && layer.overlayId?.endsWith("-latest-location") == true)
        }

    private fun List<SearchMapLayerUiState>.withoutMarkerLayers(): List<SearchMapLayerUiState> =
        filterNot { layer -> layer.kind == SearchLayerKind.Marker }

    private fun detailState(
        context: SearchMapSessionContext,
        body: String,
        fallback: SearchMapUiState
    ): SearchMapUiState {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return fallback
        val missingPerson = json.optJSONObject("missingPerson")
        val assignments = json.optJSONArray("assignments").toIncidentAssignmentReadModel()
        return fallback.copy(
            incidentTitle = json.optString("title").ifBlank {
                json.optString("incidentId").ifBlank {
                    json.optString("id").ifBlank { fallback.incidentTitle }
                }
            },
            incidentStatusLabel = json.optString("status").toIncidentStatusLabel(),
            openedAtLabel = json.optString("openedAt").toKstDateTimeLabel(),
            missingPersonSummary = missingPersonSummary(missingPerson),
            missingPersonName = missingPersonDisplayName(missingPerson),
            missingPersonPhotoUrl = missingPerson.photoUrl(context.objectStorageBaseUrl ?: context.apiBaseUrl),
            lastSeenAtLabel = missingPerson?.optString("lastSeenAt").toKstDateTimeLabel(),
            lastSeenLocationLabel = missingPerson?.optString("lastSeenLocationText")?.takeIf(String::isNotBlank),
            appearanceLabel = missingPerson?.optString("appearanceText")?.takeIf(String::isNotBlank),
            assignmentCountLabel = assignments.countLabel,
            assignmentRoleSummary = assignments.roleSummary,
            assignmentItems = assignments.items,
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
        val displayName = missingPersonDisplayName(missingPerson) ?: "실종자"
        val appearanceText = missingPerson.optString("appearanceText")
        return listOf(displayName, appearanceText)
            .filter(String::isNotBlank)
            .joinToString(" · ")
    }

    private fun missingPersonDisplayName(missingPerson: JSONObject?): String? =
        missingPerson
            ?.optString("displayName")
            ?.ifBlank { missingPerson.optString("name") }
            ?.withoutMissingPersonCode()
            ?.takeIf(String::isNotBlank)

    private fun JSONObject?.photoUrl(resourceBaseUrl: String?): String? {
        val rawUrl = this?.optString("photoUrl")?.takeIf(String::isNotBlank) ?: return null
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl
        }
        val baseUrl = resourceBaseUrl?.takeIf(String::isNotBlank) ?: return rawUrl
        return "${baseUrl.trimEnd('/').removeSuffix("/api")}/${rawUrl.trimStart('/')}"
    }

    private fun JSONArray?.toIncidentAssignmentReadModel(): IncidentAssignmentReadModel {
        val assignments = this ?: return IncidentAssignmentReadModel("참여 계정 확인 중", null, emptyList())
        if (assignments.length() == 0) {
            return IncidentAssignmentReadModel("0개", "참여 계정 없음", emptyList())
        }
        val roleCounts = linkedMapOf(
            "INCIDENT_COMMANDER" to 0,
            "FIELD_COMMANDER" to 0,
            "MEMBER" to 0
        )
        val items = mutableListOf<SearchMapAssignmentUiState>()
        repeat(assignments.length()) { index ->
            val assignment = assignments.optJSONObject(index)
            val role = assignment?.optString("incidentRole")?.uppercase().orEmpty()
            if (role in roleCounts) {
                roleCounts[role] = roleCounts.getValue(role) + 1
            }
            if (assignment != null) {
                items +=
                    SearchMapAssignmentUiState(
                        displayName = assignment.displayName(role),
                        roleLabel = role.toIncidentRoleLabel(),
                        accountTypeLabel = assignment.optString("accountType").toAccountTypeLabel(),
                        organizationLabel = assignment.optString("organizationType").toOrganizationTypeLabel(),
                        assignedAtLabel = assignment.optString("assignedAt").toKstDateTimeLabel()
                    )
            }
        }
        val roleSummary =
            roleCounts
                .mapNotNull { (role, count) ->
                    if (count > 0) {
                        "${role.toIncidentRoleLabel()} $count"
                    } else {
                        null
                    }
                }
                .joinToString(" · ")
                .ifBlank { "역할 확인 필요" }
        val sortedItems =
            items.sortedWith(
                compareBy<SearchMapAssignmentUiState> { it.roleLabel.assignmentRolePriority() }
                    .thenBy { it.assignedAtLabel ?: "" }
            )
        return IncidentAssignmentReadModel(
            countLabel = "${assignments.length()}개",
            roleSummary = roleSummary,
            items = sortedItems
        )
    }

    private fun String.toIncidentRoleLabel(): String =
        when (this) {
            "INCIDENT_COMMANDER" -> "사건 지휘"
            "FIELD_COMMANDER" -> "현장 지휘"
            "MEMBER" -> "수색 대원"
            else -> "참여 계정"
        }

    private fun String.assignmentRolePriority(): Int =
        when (this) {
            "사건 지휘" -> 0
            "현장 지휘" -> 1
            "수색 대원" -> 2
            else -> 3
        }

    private fun JSONObject.displayName(role: String): String {
        optString("accountDisplayName").takeIf(String::isNotBlank)?.let { return it }
        val fallback =
            listOfNotNull(
                optString("organizationType").toOrganizationTypeLabel(),
                optString("accountType").toAccountTypeLabel(),
                role.toIncidentRoleLabel()
            )
                .filter(String::isNotBlank)
                .joinToString(" ")
        return fallback.ifBlank { "참여 계정" }
    }

    private fun String.toAccountTypeLabel(): String? =
        when (uppercase()) {
            "TEAM" -> "팀"
            "PATROL_CAR" -> "순찰차"
            "COMMAND" -> "지휘"
            "" -> null
            else -> "기타 계정"
        }

    private fun String.toOrganizationTypeLabel(): String? =
        when (uppercase()) {
            "MISSING_TEAM" -> "실종팀"
            "SUPPORT_UNIT" -> "지원부대"
            "POLICE_SUBSTATION" -> "파출소"
            "" -> null
            else -> "기타 조직"
        }

    private fun String.toIncidentStatusLabel(): String =
        when (uppercase()) {
            "CLOSED" -> "종료"
            "OPEN" -> "진행 중"
            else -> takeIf(String::isNotBlank) ?: "진행 상태 확인 중"
        }

    private fun String?.toKstDateTimeLabel(): String? =
        this?.takeIf(String::isNotBlank)
            ?.let { value -> runCatching { KstDateTimeFormatter.format(Instant.parse(value)) }.getOrNull() }

    private suspend fun SearchMapSessionContext.outboxSummaryOrNull(): OutboxStatusSummary? {
        val incidentId = incidentId?.takeIf(String::isNotBlank) ?: return null
        val policePhoneId = policePhoneId?.takeIf(String::isNotBlank) ?: return null
        return runCatching { outboxSummary(incidentId, policePhoneId) }.getOrNull()
    }

    private fun oldestPendingMinutes(clientRequestedAt: Long): Int {
        return ((nowMs() - clientRequestedAt).coerceAtLeast(0L) / MILLIS_PER_MINUTE).toInt()
    }

    private fun searchAreaLayers(
        body: String,
        areaColorRegistry: WebAreaColorRegistry
    ): List<SearchMapLayerUiState> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val areas = root.optJSONArray("areas") ?: root.optJSONArray("items") ?: return emptyList()
        return buildList {
            repeat(areas.length()) { index ->
                val area = areas.optJSONObject(index) ?: return@repeat
                val geometry = area.optJSONObject("geometry") ?: return@repeat
                val kind = area.searchLayerKind() ?: return@repeat
                val id = area.optString("id").ifBlank { "op-${kind.name.lowercase()}-$index" }
                val highlighted = kind == SearchLayerKind.Team
                add(
                    SearchMapLayerUiState(
                        label = area.labelFor(kind),
                        kind = kind,
                        highlighted = highlighted,
                        overlayId = id,
                        geoJson = geometry.toString(),
                        visualStyle =
                            searchAreaVisualStyle(
                                areaId = id,
                                colorToken = area.areaColorToken(),
                                kind = kind,
                                highlighted = highlighted,
                                areaColorRegistry = areaColorRegistry
                            )
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
                    layer.copy(
                        highlighted = assigned,
                        assignedToCurrentPhone = assigned,
                        visualStyle = layer.visualStyle?.withAreaHighlight(layer.kind, assigned)
                    )
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
        firstOrNull { layer -> layer.kind == SearchLayerKind.Team }
            ?.label
            ?.takeIf(String::isNotBlank)

    private fun searchPathLayers(
        body: String,
        currentAccountId: String,
        areaColorCandidates: List<RouteAreaColorCandidate>
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
                val belongsToCurrentActor = pathAccountId.equals(currentAccountId, ignoreCase = true)
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
                val routeColor = path.routeCoreColor(pathId, geometry, areaColorCandidates)
                val routeVisualStyle = searchPathVisualStyle(routeColor, activeForCurrentActor)
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
                        geoJson = geometry.toString(),
                        visualStyle = routeVisualStyle
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
                            geoJson = latestPoint.toString(),
                            visualStyle = currentLocationVisualStyle(routeColor, activeForCurrentActor)
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

    private fun searchAreaVisualStyle(
        areaId: String,
        colorToken: String?,
        kind: SearchLayerKind,
        highlighted: Boolean,
        areaColorRegistry: WebAreaColorRegistry
    ): SearchMapLayerVisualStyle {
        val token = areaColorRegistry.token(areaId, colorToken)
        val baseLineWidth =
            when (kind) {
                SearchLayerKind.Overall -> 2.0f
                SearchLayerKind.Unit -> 1.75f
                SearchLayerKind.Team -> 2.0f
                SearchLayerKind.Path,
                SearchLayerKind.Marker,
                SearchLayerKind.CurrentLocation -> 2.0f
            }
        return SearchMapLayerVisualStyle(
            fillColor = token.lineColor,
            fillOpacity = token.fillOpacity,
            lineColor = token.lineColor,
            lineWidth = baseLineWidth + if (highlighted) 0.75f else 0.0f,
            lineOpacity = if (highlighted) 0.96f else 0.82f
        )
    }

    private fun SearchMapLayerVisualStyle.withAreaHighlight(
        kind: SearchLayerKind,
        highlighted: Boolean
    ): SearchMapLayerVisualStyle {
        val baseLineWidth =
            when (kind) {
                SearchLayerKind.Overall -> 2.0f
                SearchLayerKind.Unit -> 1.75f
                SearchLayerKind.Team -> 2.0f
                SearchLayerKind.Path,
                SearchLayerKind.Marker,
                SearchLayerKind.CurrentLocation -> lineWidth ?: 2.0f
            }
        return copy(
            lineWidth = baseLineWidth + if (highlighted) 0.75f else 0.0f,
            lineOpacity = if (highlighted) 0.96f else 0.82f
        )
    }

    private fun searchPathVisualStyle(
        routeColor: String,
        highlighted: Boolean
    ): SearchMapLayerVisualStyle =
        SearchMapLayerVisualStyle(
            lineColor = routeColor,
            lineWidth = if (highlighted) 4.6f else 3.4f,
            lineOpacity = if (highlighted) 0.98f else 0.86f
        )

    private fun currentLocationVisualStyle(
        routeColor: String,
        highlighted: Boolean
    ): SearchMapLayerVisualStyle =
        SearchMapLayerVisualStyle(
            fillColor = routeColor,
            lineColor = routeColor,
            lineOpacity = if (highlighted) 0.98f else 0.86f
        )

    private fun JSONObject.routeCoreColor(
        pathId: String,
        geometry: JSONObject,
        areaColorCandidates: List<RouteAreaColorCandidate>
    ): String {
        val routeOpId = optString("opId").ifBlank { optString("op_id") }.takeIf(String::isNotBlank)
        resolveRouteColorByGeometry(
            routeCoordinates = geometry.lineStringCoordinates(),
            areaCandidates = areaColorCandidates,
            routeOpId = routeOpId
        )?.let { return it }
        val explicitColor =
            optString("routeColor")
                .ifBlank { optString("route_color") }
                .takeIf(String::isNotBlank)
        if (explicitColor != null) {
            return explicitColor.normalizedHexColor()
        }
        val routeKey =
            optString("accountId")
                .ifBlank { optString("account_id") }
                .ifBlank { pathId }
        return routeFallbackColor(routeKey)
    }

    private fun JSONObject.areaColorToken(): String? =
        optString("colorToken")
            .ifBlank { optString("color_token") }
            .takeIf(String::isNotBlank)

    private fun SearchMapUiState.routeAreaColorCandidates(): List<RouteAreaColorCandidate> =
        layers.mapNotNull { layer ->
            if (
                layer.kind !in setOf(SearchLayerKind.Overall, SearchLayerKind.Unit, SearchLayerKind.Team) ||
                layer.geoJson.isNullOrBlank()
            ) {
                return@mapNotNull null
            }
            val lineColor = layer.visualStyle?.lineColor?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val geometry = runCatching { JSONObject(layer.geoJson) }.getOrNull() ?: return@mapNotNull null
            RouteAreaColorCandidate(
                id = layer.overlayId.orEmpty(),
                opId = null,
                kind = layer.kind,
                coordinates = geometry.polygonOuterRing(),
                lineColor = lineColor
            )
        }

    private fun resolveRouteColorByGeometry(
        routeCoordinates: List<Pair<Double, Double>>,
        areaCandidates: List<RouteAreaColorCandidate>,
        routeOpId: String?
    ): String? {
        if (routeCoordinates.size < 2 || areaCandidates.isEmpty()) {
            return null
        }
        val samples = createLineSamples(routeCoordinates)
        val matchingAreaCandidates =
            if (routeOpId == null) {
                areaCandidates
            } else {
                areaCandidates.filter { candidate -> candidate.opId == null || candidate.opId == routeOpId }
            }
        val nonOverallCandidates = matchingAreaCandidates.filter { candidate -> candidate.kind != SearchLayerKind.Overall }
        val candidatesToScore = nonOverallCandidates.ifEmpty { matchingAreaCandidates }
        return candidatesToScore
            .mapNotNull { candidate ->
                if (candidate.coordinates.size < 4) {
                    return@mapNotNull null
                }
                val hitCount = samples.count { sample -> sample.isInPolygon(candidate.coordinates) }
                if (hitCount == 0) {
                    return@mapNotNull null
                }
                ScoredRouteAreaColorCandidate(
                    candidate = candidate,
                    sampleHitCount = hitCount,
                    priority = candidate.kind.routeAreaPriority,
                    polygonArea = kotlin.math.abs(candidate.coordinates.polygonArea())
                )
            }
            .sortedWith(
                compareByDescending<ScoredRouteAreaColorCandidate> { it.sampleHitCount }
                    .thenByDescending { it.priority }
                    .thenBy { it.polygonArea }
            )
            .firstOrNull()
            ?.candidate
            ?.lineColor
    }

    private fun createLineSamples(coordinates: List<Pair<Double, Double>>): List<Pair<Double, Double>> =
        buildList {
            addAll(coordinates)
            for (index in 0 until coordinates.lastIndex) {
                val current = coordinates[index]
                val next = coordinates[index + 1]
                add((current.first + next.first) / 2.0 to (current.second + next.second) / 2.0)
            }
        }

    private fun Pair<Double, Double>.isInPolygon(polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        val pointX = first
        val pointY = second
        var previousIndex = polygon.lastIndex
        for (index in polygon.indices) {
            val current = polygon[index]
            val previous = polygon[previousIndex]
            if (isPointOnSegment(this, previous, current)) {
                return true
            }
            val intersects =
                (current.second > pointY) != (previous.second > pointY) &&
                    pointX < ((previous.first - current.first) * (pointY - current.second)) /
                    (previous.second - current.second) + current.first
            if (intersects) {
                inside = !inside
            }
            previousIndex = index
        }
        return inside
    }

    private fun isPointOnSegment(
        point: Pair<Double, Double>,
        start: Pair<Double, Double>,
        end: Pair<Double, Double>
    ): Boolean {
        val minX = minOf(start.first, end.first) - 1e-12
        val maxX = maxOf(start.first, end.first) + 1e-12
        val minY = minOf(start.second, end.second) - 1e-12
        val maxY = maxOf(start.second, end.second) + 1e-12
        if (point.first < minX || point.first > maxX || point.second < minY || point.second > maxY) {
            return false
        }
        val crossProduct =
            (point.second - start.second) * (end.first - start.first) -
                (point.first - start.first) * (end.second - start.second)
        if (kotlin.math.abs(crossProduct) > 1e-12) {
            return false
        }
        val dotProduct =
            (point.first - start.first) * (end.first - start.first) +
                (point.second - start.second) * (end.second - start.second)
        if (dotProduct < 0.0) {
            return false
        }
        val segmentLengthSquared =
            (end.first - start.first) * (end.first - start.first) +
                (end.second - start.second) * (end.second - start.second)
        return dotProduct <= segmentLengthSquared
    }

    private fun List<Pair<Double, Double>>.polygonArea(): Double =
        foldIndexed(0.0) { index, area, current ->
            val next = this[(index + 1) % size]
            area + current.first * next.second - next.first * current.second
        } / 2.0

    private fun JSONObject.lineStringCoordinates(): List<Pair<Double, Double>> {
        if (!optString("type").equals("LineString", ignoreCase = true)) {
            return emptyList()
        }
        return optJSONArray("coordinates").coordinatePairs()
    }

    private fun JSONObject.polygonOuterRing(): List<Pair<Double, Double>> {
        if (!optString("type").equals("Polygon", ignoreCase = true)) {
            return emptyList()
        }
        return optJSONArray("coordinates")?.optJSONArray(0).coordinatePairs()
    }

    private fun JSONArray?.coordinatePairs(): List<Pair<Double, Double>> {
        val coordinates = this ?: return emptyList()
        return buildList {
            repeat(coordinates.length()) { index ->
                val point = coordinates.optJSONArray(index) ?: return@repeat
                if (point.length() < 2 || point.opt(0) !is Number || point.opt(1) !is Number) {
                    return@repeat
                }
                val longitude = point.optDouble(0)
                val latitude = point.optDouble(1)
                if (longitude.isFinite() && latitude.isFinite()) {
                    add(longitude to latitude)
                }
            }
        }
    }

    private fun routeFallbackColor(routeKey: String): String {
        val index = (webHashString(routeKey) % WEB_AREA_COLOR_TOKENS.size).toInt()
        return WEB_AREA_COLOR_TOKENS[index].lineColor
    }

    private fun webHashString(value: String): Long =
        value.fold(17L) { hash, char ->
            (hash * 31L + char.code.toLong()) and 0xFFFF_FFFFL
        }

    private fun String.normalizedHexColor(): String {
        val value = trim()
        return if (value.length == 4 && value.startsWith("#")) {
            buildString {
                append('#')
                append(value[1])
                append(value[1])
                append(value[2])
                append(value[2])
                append(value[3])
                append(value[3])
            }
        } else {
            value
        }
    }

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
        const val CACHE_SOURCE_INCIDENT_DETAIL = "incident_detail"
        const val CACHE_SOURCE_OVERALL_SEARCH_AREA = "overall_search_area"
        const val CACHE_SOURCE_OP_SEARCH_AREAS = "op_search_areas"
        const val CACHE_SOURCE_SEARCH_PATHS = "search_paths"
        const val CACHE_SOURCE_LIVE_MARKERS = "live_markers"
        const val CACHE_SOURCE_INITIAL_MARKERS = "initial_markers"
        const val MILLIS_PER_MINUTE = 60_000L
        const val POINT_VIEWPORT_DELTA = 0.003
        val KstDateTimeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"))
        val MISSING_PERSON_CODE_TEXT = Regex("""\b[A-Z]\d+-[가-힣A-Za-z0-9]+-\d+\b""")
        val WEB_AREA_COLOR_TOKENS =
            listOf(
                WebAreaColorToken("#2563eb", "rgba(37, 99, 235, 0.16)", 0.16f),
                WebAreaColorToken("#f97316", "rgba(249, 115, 22, 0.16)", 0.16f),
                WebAreaColorToken("#22c55e", "rgba(34, 197, 94, 0.16)", 0.16f),
                WebAreaColorToken("#a855f7", "rgba(168, 85, 247, 0.16)", 0.16f),
                WebAreaColorToken("#06b6d4", "rgba(6, 182, 212, 0.18)", 0.18f),
                WebAreaColorToken("#e11d48", "rgba(225, 29, 72, 0.18)", 0.18f),
                WebAreaColorToken("#facc15", "rgba(250, 204, 21, 0.18)", 0.18f),
                WebAreaColorToken("#ef4444", "rgba(239, 68, 68, 0.18)", 0.18f),
                WebAreaColorToken("#14b8a6", "rgba(20, 184, 166, 0.18)", 0.18f),
                WebAreaColorToken("#8b5cf6", "rgba(139, 92, 246, 0.18)", 0.18f),
                WebAreaColorToken("#84cc16", "rgba(132, 204, 22, 0.18)", 0.18f),
                WebAreaColorToken("#f59e0b", "rgba(245, 158, 11, 0.18)", 0.18f),
                WebAreaColorToken("#ec4899", "rgba(236, 72, 153, 0.18)", 0.18f),
                WebAreaColorToken("#0ea5e9", "rgba(14, 165, 233, 0.18)", 0.18f),
                WebAreaColorToken("#10b981", "rgba(16, 185, 129, 0.18)", 0.18f),
                WebAreaColorToken("#d946ef", "rgba(217, 70, 239, 0.18)", 0.18f),
                WebAreaColorToken("#dc2626", "rgba(220, 38, 38, 0.18)", 0.18f),
                WebAreaColorToken("#7c3aed", "rgba(124, 58, 237, 0.18)", 0.18f),
                WebAreaColorToken("#0891b2", "rgba(8, 145, 178, 0.18)", 0.18f),
                WebAreaColorToken("#ca8a04", "rgba(202, 138, 4, 0.18)", 0.18f),
                WebAreaColorToken("#16a34a", "rgba(22, 163, 74, 0.18)", 0.18f),
                WebAreaColorToken("#db2777", "rgba(219, 39, 119, 0.18)", 0.18f),
                WebAreaColorToken("#4f46e5", "rgba(79, 70, 229, 0.18)", 0.18f),
                WebAreaColorToken("#ea580c", "rgba(234, 88, 12, 0.18)", 0.18f),
                WebAreaColorToken("#1d4ed8", "rgba(29, 78, 216, 0.18)", 0.18f),
                WebAreaColorToken("#fb923c", "rgba(251, 146, 60, 0.18)", 0.18f),
                WebAreaColorToken("#15803d", "rgba(21, 128, 61, 0.18)", 0.18f),
                WebAreaColorToken("#9333ea", "rgba(147, 51, 234, 0.18)", 0.18f),
                WebAreaColorToken("#0284c7", "rgba(2, 132, 199, 0.18)", 0.18f),
                WebAreaColorToken("#be123c", "rgba(190, 18, 60, 0.18)", 0.18f),
                WebAreaColorToken("#eab308", "rgba(234, 179, 8, 0.18)", 0.18f),
                WebAreaColorToken("#b91c1c", "rgba(185, 28, 28, 0.18)", 0.18f),
                WebAreaColorToken("#0d9488", "rgba(13, 148, 136, 0.18)", 0.18f),
                WebAreaColorToken("#6d28d9", "rgba(109, 40, 217, 0.18)", 0.18f),
                WebAreaColorToken("#65a30d", "rgba(101, 163, 13, 0.18)", 0.18f),
                WebAreaColorToken("#d97706", "rgba(217, 119, 6, 0.18)", 0.18f),
                WebAreaColorToken("#c026d3", "rgba(192, 38, 211, 0.18)", 0.18f),
                WebAreaColorToken("#0369a1", "rgba(3, 105, 161, 0.18)", 0.18f),
                WebAreaColorToken("#059669", "rgba(5, 150, 105, 0.18)", 0.18f),
                WebAreaColorToken("#c026d3", "rgba(192, 38, 211, 0.18)", 0.18f),
                WebAreaColorToken("#f43f5e", "rgba(244, 63, 94, 0.18)", 0.18f),
                WebAreaColorToken("#6366f1", "rgba(99, 102, 241, 0.18)", 0.18f),
                WebAreaColorToken("#0f766e", "rgba(15, 118, 110, 0.18)", 0.18f),
                WebAreaColorToken("#f97316", "rgba(249, 115, 22, 0.18)", 0.18f),
                WebAreaColorToken("#3b82f6", "rgba(59, 130, 246, 0.18)", 0.18f),
                WebAreaColorToken("#fb7185", "rgba(251, 113, 133, 0.18)", 0.18f),
                WebAreaColorToken("#7e22ce", "rgba(126, 34, 206, 0.18)", 0.18f),
                WebAreaColorToken("#f59e0b", "rgba(245, 158, 11, 0.18)", 0.18f),
                WebAreaColorToken("#0ea5e9", "rgba(14, 165, 233, 0.18)", 0.18f),
                WebAreaColorToken("#f97316", "rgba(249, 115, 22, 0.18)", 0.18f),
                WebAreaColorToken("#22c55e", "rgba(34, 197, 94, 0.18)", 0.18f),
                WebAreaColorToken("#a21caf", "rgba(162, 28, 175, 0.18)", 0.18f),
                WebAreaColorToken("#06b6d4", "rgba(6, 182, 212, 0.18)", 0.18f),
                WebAreaColorToken("#e11d48", "rgba(225, 29, 72, 0.18)", 0.18f),
                WebAreaColorToken("#facc15", "rgba(250, 204, 21, 0.18)", 0.18f),
                WebAreaColorToken("#ef4444", "rgba(239, 68, 68, 0.18)", 0.18f),
                WebAreaColorToken("#2dd4bf", "rgba(45, 212, 191, 0.18)", 0.18f),
                WebAreaColorToken("#8b5cf6", "rgba(139, 92, 246, 0.18)", 0.18f),
                WebAreaColorToken("#a3e635", "rgba(163, 230, 53, 0.18)", 0.18f),
                WebAreaColorToken("#fbbf24", "rgba(251, 191, 36, 0.18)", 0.18f),
                WebAreaColorToken("#f472b6", "rgba(244, 114, 182, 0.18)", 0.18f),
                WebAreaColorToken("#38bdf8", "rgba(56, 189, 248, 0.18)", 0.18f),
                WebAreaColorToken("#34d399", "rgba(52, 211, 153, 0.18)", 0.18f),
                WebAreaColorToken("#e879f9", "rgba(232, 121, 249, 0.18)", 0.18f),
                WebAreaColorToken("#f87171", "rgba(248, 113, 113, 0.18)", 0.18f),
                WebAreaColorToken("#818cf8", "rgba(129, 140, 248, 0.18)", 0.18f),
                WebAreaColorToken("#22d3ee", "rgba(34, 211, 238, 0.18)", 0.18f),
                WebAreaColorToken("#fde047", "rgba(253, 224, 71, 0.18)", 0.18f),
                WebAreaColorToken("#4ade80", "rgba(74, 222, 128, 0.18)", 0.18f),
                WebAreaColorToken("#f0abfc", "rgba(240, 171, 252, 0.18)", 0.18f),
                WebAreaColorToken("#60a5fa", "rgba(96, 165, 250, 0.18)", 0.18f),
                WebAreaColorToken("#fdba74", "rgba(253, 186, 116, 0.18)", 0.18f)
            )
        val WEB_AREA_COLOR_TOKEN_NAMES =
            listOf(
                "AREA_BLUE_01",
                "AREA_ORANGE_01",
                "AREA_GREEN_01",
                "AREA_PURPLE_01",
                "AREA_CYAN_01",
                "AREA_ROSE_01",
                "AREA_YELLOW_01",
                "AREA_RED_01",
                "AREA_TEAL_01",
                "AREA_VIOLET_01",
                "AREA_LIME_01",
                "AREA_AMBER_01",
                "AREA_PINK_01",
                "AREA_SKY_01",
                "AREA_EMERALD_01",
                "AREA_FUCHSIA_01",
                "AREA_RED_02",
                "AREA_VIOLET_02",
                "AREA_CYAN_02",
                "AREA_YELLOW_02",
                "AREA_GREEN_02",
                "AREA_PINK_02",
                "AREA_INDIGO_01",
                "AREA_ORANGE_02",
                "AREA_BLUE_02",
                "AREA_ORANGE_03",
                "AREA_GREEN_03",
                "AREA_PURPLE_02",
                "AREA_SKY_02",
                "AREA_ROSE_02",
                "AREA_YELLOW_03",
                "AREA_RED_03",
                "AREA_TEAL_02",
                "AREA_VIOLET_03",
                "AREA_LIME_02",
                "AREA_AMBER_02",
                "AREA_FUCHSIA_02",
                "AREA_SKY_03",
                "AREA_EMERALD_02",
                "AREA_FUCHSIA_03",
                "AREA_ROSE_03",
                "AREA_INDIGO_02",
                "AREA_TEAL_03",
                "AREA_ORANGE_04",
                "AREA_BLUE_03",
                "AREA_ROSE_04",
                "AREA_PURPLE_03",
                "AREA_AMBER_03",
                "AREA_SKY_04",
                "AREA_ORANGE_05",
                "AREA_GREEN_04",
                "AREA_FUCHSIA_04",
                "AREA_CYAN_03",
                "AREA_ROSE_05",
                "AREA_YELLOW_04",
                "AREA_RED_04",
                "AREA_TEAL_04",
                "AREA_VIOLET_04",
                "AREA_LIME_03",
                "AREA_AMBER_04",
                "AREA_PINK_03",
                "AREA_SKY_05",
                "AREA_EMERALD_03",
                "AREA_FUCHSIA_05",
                "AREA_RED_05",
                "AREA_INDIGO_03",
                "AREA_CYAN_04",
                "AREA_YELLOW_05",
                "AREA_GREEN_05",
                "AREA_FUCHSIA_06",
                "AREA_BLUE_04",
                "AREA_ORANGE_06"
            )
        val WEB_AREA_COLOR_TOKENS_BY_NAME =
            WEB_AREA_COLOR_TOKEN_NAMES.zip(WEB_AREA_COLOR_TOKENS).toMap()
    }

    private class WebAreaColorRegistry(incidentId: String?) {
        private val tokenIndicesByAreaId =
            incidentId?.takeIf(String::isNotBlank)
                ?.let { linkedMapOf("$it:overall" to 0) }
                ?: linkedMapOf()

        fun token(areaId: String, colorToken: String?): WebAreaColorToken {
            WEB_AREA_COLOR_TOKENS_BY_NAME[colorToken]?.let { token ->
                tokenIndicesByAreaId[areaId] = WEB_AREA_COLOR_TOKENS.indexOf(token)
                return token
            }
            tokenIndicesByAreaId[areaId]?.let { index -> return WEB_AREA_COLOR_TOKENS[index] }
            val usedTokenIndices = tokenIndicesByAreaId.values.toSet()
            val nextTokenIndex =
                WEB_AREA_COLOR_TOKENS.indices.firstOrNull { index -> index !in usedTokenIndices }
                    ?: (hashString(areaId) % WEB_AREA_COLOR_TOKENS.size).toInt()
            tokenIndicesByAreaId[areaId] = nextTokenIndex
            return WEB_AREA_COLOR_TOKENS[nextTokenIndex]
        }

        private fun hashString(value: String): Long =
            value.fold(17L) { hash, char ->
                (hash * 31L + char.code.toLong()) and 0xFFFF_FFFFL
            }
    }
}

private data class WebAreaColorToken(
    val lineColor: String,
    val fillColor: String,
    val fillOpacity: Float
)

private data class RouteAreaColorCandidate(
    val id: String,
    val opId: String?,
    val kind: SearchLayerKind,
    val coordinates: List<Pair<Double, Double>>,
    val lineColor: String
)

private data class ScoredRouteAreaColorCandidate(
    val candidate: RouteAreaColorCandidate,
    val sampleHitCount: Int,
    val priority: Int,
    val polygonArea: Double
)

private val SearchLayerKind.routeAreaPriority: Int
    get() =
        when (this) {
            SearchLayerKind.Overall -> 1
            SearchLayerKind.Unit -> 2
            SearchLayerKind.Team -> 3
            SearchLayerKind.Path,
            SearchLayerKind.Marker,
            SearchLayerKind.CurrentLocation -> 0
        }

private data class LiveMarkerLoadResult(
    val state: SearchMapUiState,
    val loaded: Boolean
)

private data class IncidentAssignmentReadModel(
    val countLabel: String,
    val roleSummary: String?,
    val items: List<SearchMapAssignmentUiState>
)

private data class MapRevisionSnapshot(
    val remote: Map<String, String>?,
    val local: Map<String, String>
)
