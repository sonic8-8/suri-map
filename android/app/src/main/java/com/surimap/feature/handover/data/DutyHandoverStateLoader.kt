package com.surimap.feature.handover.data

import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.network.SuriMapNetworkException
import com.surimap.core.operationalperiod.DutyShiftQuery
import com.surimap.core.operationalperiod.HandoverMemoQuery
import com.surimap.core.operationalperiod.HandoverTimelineQuery
import com.surimap.core.operationalperiod.SearchHistorySummaryQuery
import com.surimap.feature.handover.ui.DutyHandoverUiState
import com.surimap.feature.handover.ui.HandoverDutyShiftOption
import com.surimap.feature.handover.ui.HandoverRecordScope
import com.surimap.feature.handover.ui.HandoverReplayControlUiState
import com.surimap.feature.handover.ui.HandoverReplayMarker
import com.surimap.feature.handover.ui.HandoverReplayPathSegment
import com.surimap.feature.handover.ui.HandoverReplayPointUi
import com.surimap.feature.handover.ui.HandoverMetric
import com.surimap.feature.handover.ui.HandoverRecord
import com.surimap.feature.handover.ui.SearchHistorySummaryStatus
import com.surimap.feature.handover.ui.SummarySourceReadiness
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

data class HandoverSessionContext(
    val incidentId: String?,
    val opId: String?,
    val opLabel: String? = null,
    val dutyShiftId: String?,
    val policePhoneId: String?
) {
    val displayOpLabel: String =
        opLabel?.takeIf(String::isNotBlank)
            ?: if (opId.isNullOrBlank()) "OP 확인 필요" else "현재 OP"
}

class DutyHandoverStateLoader(
    private val handoverTimeline: suspend (String, HandoverTimelineQuery) -> SuriMapApiResponse = { _, _ ->
        notFoundResponse()
    },
    private val handoverMemos: suspend (HandoverMemoQuery) -> SuriMapApiResponse = {
        notFoundResponse()
    },
    private val dutyShifts: suspend (DutyShiftQuery) -> SuriMapApiResponse = {
        notFoundResponse()
    },
    private val searchHistorySummaries: suspend (String, SearchHistorySummaryQuery) -> SuriMapApiResponse = { _, _ ->
        notFoundResponse()
    }
) {
    suspend fun load(
        context: HandoverSessionContext,
        selectedDutyShiftId: String? = null
    ): DutyHandoverUiState {
        val valid = context.valid() ?: return emptyState(context)
        return try {
            val dutyShiftOptions = loadDutyShiftOptions(valid, context)
            val selectedDutyShift =
                dutyShiftOptions.selectDutyShift(
                    currentDutyShiftId = context.dutyShiftId,
                    selectedDutyShiftId = selectedDutyShiftId
                )
            val initialDutyShiftId = selectedDutyShift?.dutyShiftId ?: context.dutyShiftId?.takeIf(String::isNotBlank)
            val requestedDutyShiftId = selectedDutyShiftId?.takeIf(String::isNotBlank)
            val initialTimelineSelection =
                DutyTimelineSelection(
                    dutyShift = selectedDutyShift,
                    dutyShiftId = initialDutyShiftId,
                    timeline = loadDutyTimeline(valid, initialDutyShiftId)
                )
            val timelineSelection =
                initialTimelineSelection.withReplayFallback(
                    valid = valid,
                    dutyShiftOptions = dutyShiftOptions,
                    currentDutyShiftId = context.dutyShiftId,
                    requestedDutyShiftId = requestedDutyShiftId
                )
            val selectedTimelineDutyShift = timelineSelection.dutyShift
            val dutyShiftId = timelineSelection.dutyShiftId
            val dutyTimeline = timelineSelection.timeline
            val markedDutyShiftOptions =
                dutyShiftOptions
                    .visibleDutyShiftOptions(
                        currentDutyShiftId = context.dutyShiftId,
                        selectedDutyShiftId = requestedDutyShiftId
                    )
                    .markSelected(dutyShiftId)
            if (dutyTimeline.hasDisplayableEvidence) {
                val summary = dutyTimeline.summary ?: loadDutyShiftSummary(valid, requireNotNull(dutyShiftId))
                return summary.toUiState(
                    context = context,
                    recordScope = HandoverRecordScope.DutyShift,
                    dutyShiftOptions = markedDutyShiftOptions,
                    selectedDutyShift = selectedTimelineDutyShift,
                    metrics = dutyTimeline.metrics,
                    records = dutyTimeline.records,
                    replayPathSegments = dutyTimeline.replayPathSegments,
                    replayMarkers = dutyTimeline.replayMarkers,
                    replayPoints = dutyTimeline.replayPoints,
                    replayDurationMs = dutyTimeline.replayDurationMs
                )
            }
            if (dutyShiftId != null && (selectedTimelineDutyShift?.previous == true || requestedDutyShiftId != null)) {
                val summary = loadDutyShiftSummary(valid, dutyShiftId)
                return summary.toUiState(
                    context = context,
                    recordScope = HandoverRecordScope.DutyShift,
                    dutyShiftOptions = markedDutyShiftOptions,
                    selectedDutyShift = selectedTimelineDutyShift,
                    metrics = dutyTimeline.metrics,
                    records = dutyTimeline.records,
                    replayPathSegments = dutyTimeline.replayPathSegments,
                    replayMarkers = dutyTimeline.replayMarkers,
                    replayPoints = dutyTimeline.replayPoints,
                    replayDurationMs = dutyTimeline.replayDurationMs
                )
            }
            val memoResponse =
                handoverMemos(
                    HandoverMemoQuery(
                        incidentId = valid.incidentId,
                        opId = valid.opId
                    )
                )
            val memos = parseMemos(memoResponse)
            val summary =
                if (dutyShiftId == null) {
                    SummaryReadModel.empty()
                } else {
                    loadDutyShiftSummary(valid, dutyShiftId)
                }
            summary.toUiState(
                context = context,
                recordScope = HandoverRecordScope.DutyShift,
                dutyShiftOptions = markedDutyShiftOptions,
                selectedDutyShift = selectedTimelineDutyShift,
                metrics = listOf(HandoverMetric("${memos.size}건", "메모")),
                records = memos.map { memo -> memo.toRecord() }
            )
        } catch (_: SuriMapNetworkException) {
            unavailableState(context)
        }
    }

    fun fallback(context: HandoverSessionContext): DutyHandoverUiState =
        if (context.valid() == null) {
            emptyState(context)
        } else {
            DutyHandoverUiState.generating().copy(
                title = TITLE,
                subtitle = context.subtitle(),
                records = emptyList(),
                metrics = emptyList()
            )
        }

    private fun SummaryReadModel.toUiState(
        context: HandoverSessionContext,
        recordScope: HandoverRecordScope,
        dutyShiftOptions: List<HandoverDutyShiftOption>,
        selectedDutyShift: DutyShiftOptionReadModel?,
        metrics: List<HandoverMetric>,
        records: List<HandoverRecord>,
        replayPathSegments: List<HandoverReplayPathSegment> = emptyList(),
        replayMarkers: List<HandoverReplayMarker> = emptyList(),
        replayPoints: List<HandoverReplayPointUi> = emptyList(),
        replayDurationMs: Long = 0L
    ): DutyHandoverUiState =
        DutyHandoverUiState(
            title = recordScope.title(selectedDutyShift),
            subtitle = context.subtitle(recordScope, selectedDutyShift),
            recordScope = recordScope,
            summaryStatus = status,
            generatedAtLabel = generatedAtLabel,
            summary = content,
            sourceReadiness = sourceReadiness,
            metrics = metrics,
            records = records,
            dutyShiftOptions = dutyShiftOptions,
            replayPathSegments = replayPathSegments,
            replayMarkers = replayMarkers,
            replayPoints = replayPoints,
            replayControl = HandoverReplayControlUiState(displayDurationMs = replayDurationMs),
            canRequestSummaryGeneration = false
        )

    private suspend fun loadDutyShiftSummary(
        valid: RequiredHandoverSessionContext,
        dutyShiftId: String
    ): SummaryReadModel =
        parseSummary(
            searchHistorySummaries(
                valid.opId,
                SearchHistorySummaryQuery(
                    incidentId = valid.incidentId,
                    scopeType = "DUTY_SHIFT",
                    scopeId = dutyShiftId,
                    dutyShiftId = dutyShiftId
                )
            )
        )

    private suspend fun loadDutyTimeline(
        valid: RequiredHandoverSessionContext,
        dutyShiftId: String?
    ): TimelineReadModel =
        if (dutyShiftId == null) {
            TimelineReadModel.unavailable()
        } else {
            parseTimeline(
                handoverTimeline(
                    valid.opId,
                    HandoverTimelineQuery(
                        incidentId = valid.incidentId,
                        scopeType = "DUTY_SHIFT",
                        dutyShiftId = dutyShiftId,
                        includeOtherActors = false
                    )
                )
            )
        }

    private suspend fun DutyTimelineSelection.withReplayFallback(
        valid: RequiredHandoverSessionContext,
        dutyShiftOptions: List<DutyShiftOptionReadModel>,
        currentDutyShiftId: String?,
        requestedDutyShiftId: String?
    ): DutyTimelineSelection {
        if (requestedDutyShiftId != null || timeline.hasReplayEvidence) {
            return this
        }
        dutyShiftOptions
            .replayFallbackCandidates(
                initialDutyShiftId = dutyShiftId,
                currentDutyShiftId = currentDutyShiftId
            )
            .forEach { candidate ->
                val candidateTimeline = loadDutyTimeline(valid, candidate.dutyShiftId)
                if (candidateTimeline.hasReplayEvidence) {
                    return DutyTimelineSelection(
                        dutyShift = candidate,
                        dutyShiftId = candidate.dutyShiftId,
                        timeline = candidateTimeline
                    )
                }
            }
        return this
    }

    private suspend fun loadDutyShiftOptions(
        valid: RequiredHandoverSessionContext,
        context: HandoverSessionContext
    ): List<DutyShiftOptionReadModel> {
        val response =
            dutyShifts(
                DutyShiftQuery(
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    policePhoneId = context.policePhoneId?.takeIf(String::isNotBlank)
                )
            )
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return emptyList()
        }
        return parseDutyShiftOptions(
            body = response.body,
            currentDutyShiftId = context.dutyShiftId,
            samePhoneScoped = !context.policePhoneId.isNullOrBlank()
        )
    }

    private fun parseDutyShiftOptions(
        body: String,
        currentDutyShiftId: String?,
        samePhoneScoped: Boolean
    ): List<DutyShiftOptionReadModel> {
        val items = parseItems(body)
        return buildList {
            repeat(items.length()) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                val dutyShiftId =
                    item.optString("id")
                        .ifBlank { item.optString("dutyShiftId") }
                        .takeIf(String::isNotBlank)
                        ?: return@repeat
                val startedAt = item.optString("startedAt").ifBlank { item.optString("startAt") }.toInstantOrNull()
                val endedAt = item.optString("endedAt").ifBlank { item.optString("endAt") }.toInstantOrNull()
                val status = item.optString("status").ifBlank { if (endedAt == null) "ACTIVE" else "ENDED" }
                val current = dutyShiftId == currentDutyShiftId
                val dutyLabel = dutyShiftLabel(current, endedAt, status)
                val actorLabel = item.dutyShiftActorLabel()
                add(
                    DutyShiftOptionReadModel(
                        dutyShiftId = dutyShiftId,
                        startedAt = startedAt,
                        endedAt = endedAt,
                        status = status,
                        current = current,
                        dutyLabel = dutyLabel,
                        actorLabel = actorLabel,
                        label = actorLabel ?: dutyLabel,
                        subtitle = dutyShiftSubtitle(dutyShiftId, startedAt, endedAt, status, dutyLabel, samePhoneScoped)
                    )
                )
            }
        }.sortedWith(
            compareByDescending<DutyShiftOptionReadModel> { it.endedAt ?: it.startedAt ?: Instant.EPOCH }
                .thenByDescending { it.startedAt ?: Instant.EPOCH }
        )
    }

    private fun parseMemos(response: SuriMapApiResponse): List<HandoverMemoReadModel> {
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return emptyList()
        }
        val items = parseItems(response.body)
        return buildList {
            repeat(items.length()) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                val content = item.optString("content").takeIf(String::isNotBlank) ?: return@repeat
                val sourceKey =
                    item.optString("id")
                        .ifBlank { item.optString("handoverMemoId") }
                        .ifBlank { "handover-memo-$index" }
                add(
                    HandoverMemoReadModel(
                        targetType = item.optString("memoTargetType").ifBlank { "MEMO" },
                        content = content,
                        sourceKey = sourceKey
                    )
                )
            }
        }
    }

    private fun parseSummary(response: SuriMapApiResponse): SummaryReadModel {
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return SummaryReadModel.unavailable()
        }
        val items = parseItems(response.body)
        if (items.length() == 0) {
            return SummaryReadModel.empty()
        }
        val item = items.optJSONObject(0) ?: return SummaryReadModel.empty()
        return parseSummaryItem(item)
    }

    private fun parseSummaryItem(item: JSONObject): SummaryReadModel {
        val statusText =
            item.optString("displayStatus")
                .ifBlank { item.optString("status") }
                .ifBlank { "EMPTY" }
        val content = item.optString("content").takeIf(String::isNotBlank)
        val updatedAt =
            item.optString("updatedAt")
                .ifBlank { item.optString("generatedAt") }
                .ifBlank { "서버 처리 시간 없음" }
        return SummaryReadModel(
            status = statusText.toSummaryStatus(),
            generatedAtLabel = updatedAt,
            content = content,
            sourceReadiness = item.optString("sourceReadiness").toSourceReadiness()
        )
    }

    private fun parseTimeline(response: SuriMapApiResponse): TimelineReadModel {
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return TimelineReadModel.unavailable()
        }
        val body = runCatching { JSONObject(response.body) }.getOrNull() ?: return TimelineReadModel.unavailable()
        val actors = parseActors(body.optJSONArray("actors") ?: JSONArray())
        val paths = body.optJSONArray("paths") ?: JSONArray()
        val events = body.optJSONArray("events") ?: JSONArray()
        val metrics = body.optJSONObject("metrics")
        val firstReplayAt = firstReplayInstant(paths)
        val replayPoints = parseReplayPoints(paths, firstReplayAt)
        val replayDurationMs = replayPoints.maxOfOrNull(HandoverReplayPointUi::elapsedMs) ?: 0L
        val replayPathSegments = parseReplayPathSegments(paths, actors, firstReplayAt)
        val replayMarkers = parseReplayMarkers(events, firstReplayAt)
        val records = parseTimelineRecords(events, actors)
        val summary =
            body.optJSONObject("summary")
                ?.let(::parseSummaryItem)
        val sourceReadiness = summary?.sourceReadiness ?: metrics.optSyncStatus().toSourceReadiness()
        return TimelineReadModel(
            available = true,
            summary = summary?.copy(sourceReadiness = sourceReadiness),
            metrics = parseMetrics(metrics),
            records = records,
            replayPathSegments = replayPathSegments,
            replayMarkers = replayMarkers,
            replayPoints = replayPoints,
            replayDurationMs = replayDurationMs
        )
    }

    private fun parseActors(items: JSONArray): Map<String, String> =
        buildMap {
            repeat(items.length()) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                val actorId = item.optString("actorId").takeIf(String::isNotBlank) ?: return@repeat
                val displayName = item.optString("displayName").ifBlank { "현장 기록자" }
                put(actorId, displayName)
            }
        }

    private fun firstReplayInstant(paths: JSONArray): Instant? {
        val timedPoints = mutableListOf<TimedReplayPoint>()
        repeat(paths.length()) { pathIndex ->
            val path = paths.optJSONObject(pathIndex) ?: return@repeat
            val points = path.optJSONArray("points") ?: return@repeat
            repeat(points.length()) { pointIndex ->
                val point = points.optJSONObject(pointIndex) ?: return@repeat
                val at = point.optString("at").toInstantOrNull() ?: return@repeat
                val lat = point.optDouble("lat", Double.NaN)
                val lng = point.optDouble("lng", Double.NaN)
                if (!lat.isFinite() || !lng.isFinite()) {
                    return@repeat
                }
                timedPoints.add(TimedReplayPoint(at = at, lat = lat, lng = lng))
            }
        }
        return timedPoints.minOfOrNull(TimedReplayPoint::at)
    }

    private fun parseReplayPoints(paths: JSONArray, firstAt: Instant?): List<HandoverReplayPointUi> {
        val origin = firstAt ?: return emptyList()
        val timedPoints = mutableListOf<TimedReplayPoint>()
        repeat(paths.length()) { pathIndex ->
            val path = paths.optJSONObject(pathIndex) ?: return@repeat
            parsePathPoints(path, origin).forEach { point ->
                timedPoints.add(TimedReplayPoint(at = origin.plusMillis(point.elapsedMs), lat = point.lat, lng = point.lng))
            }
        }
        return timedPoints
            .sortedBy(TimedReplayPoint::at)
            .map { point ->
                HandoverReplayPointUi(
                    elapsedMs = Duration.between(origin, point.at).toMillis().coerceAtLeast(0L),
                    lat = point.lat,
                    lng = point.lng
                )
            }
    }

    private fun parseReplayPathSegments(
        paths: JSONArray,
        actors: Map<String, String>,
        firstAt: Instant?
    ): List<HandoverReplayPathSegment> =
        buildList {
            val origin = firstAt ?: return@buildList
            repeat(paths.length()) { index ->
                val path = paths.optJSONObject(index) ?: return@repeat
                val actor = actors[path.optString("actorId")] ?: "현장 기록자"
                val mode = path.optString("mode").ifBlank { "UNKNOWN" }
                val points = path.optJSONArray("points") ?: JSONArray()
                val replayPoints = parsePathPoints(path, origin)
                add(
                    HandoverReplayPathSegment(
                        label = "$actor · ${mode.toMovementLabel()} 경로",
                        timeRangeLabel = timeRangeLabel(path.optString("startedAt"), path.optString("endedAt")),
                        distanceLabel = "GPS ${points.length()}점",
                        modeLabel = mode.toMovementLabel(),
                        sourceKey = path.optString("pathId").ifBlank { "handover-path-$index" },
                        points = replayPoints
                    )
                )
            }
        }

    private fun parsePathPoints(path: JSONObject, firstAt: Instant): List<HandoverReplayPointUi> =
        buildList {
            val points = path.optJSONArray("points") ?: return@buildList
            repeat(points.length()) { pointIndex ->
                val point = points.optJSONObject(pointIndex) ?: return@repeat
                val at = point.optString("at").toInstantOrNull() ?: return@repeat
                val lat = point.optDouble("lat", Double.NaN)
                val lng = point.optDouble("lng", Double.NaN)
                if (!lat.isFinite() || !lng.isFinite()) {
                    return@repeat
                }
                add(
                    HandoverReplayPointUi(
                        elapsedMs = Duration.between(firstAt, at).toMillis().coerceAtLeast(0L),
                        lat = lat,
                        lng = lng
                    )
                )
            }
        }.sortedBy(HandoverReplayPointUi::elapsedMs)

    private fun parseReplayMarkers(events: JSONArray, firstAt: Instant?): List<HandoverReplayMarker> =
        buildList {
            repeat(events.length()) { index ->
                val event = events.optJSONObject(index) ?: return@repeat
                if (event.optString("type") != "MARKER") {
                    return@repeat
                }
                val detail = event.optJSONObject("detail") ?: JSONObject()
                val location = detail.optJSONObject("location")
                val markerType = detail.optString("markerType").ifBlank { "NOTE" }
                val memo = detail.optString("memo").takeIf(String::isNotBlank)
                val photoCount = detail.optInt("photoCount", 0).coerceAtLeast(0)
                val elapsedMs =
                    firstAt?.let { origin ->
                        event.optString("occurredAt")
                            .toInstantOrNull()
                            ?.let { occurredAt -> Duration.between(origin, occurredAt).toMillis().coerceAtLeast(0L) }
                    }
                add(
                    HandoverReplayMarker(
                        title = "${markerType.toMarkerTypeLabel()} 마커",
                        timeLabel = event.optString("occurredAt").toTimeLabel(),
                        typeLabel = markerType.toMarkerTypeLabel(),
                        photoCountLabel = "사진 ${photoCount}장",
                        elapsedMs = elapsedMs,
                        lat = location?.optDouble("lat", Double.NaN)?.takeIf(Double::isFinite),
                        lng = location?.optDouble("lng", Double.NaN)?.takeIf(Double::isFinite)
                    ).let { marker ->
                        if (memo.isNullOrBlank()) marker else marker.copy(title = "${marker.title} · $memo")
                    }
                )
            }
        }

    private fun parseTimelineRecords(
        events: JSONArray,
        actors: Map<String, String>
    ): List<HandoverRecord> =
        buildList {
            repeat(events.length()) { index ->
                val event = events.optJSONObject(index) ?: return@repeat
                val eventId = event.optString("eventId").ifBlank { "timeline-event-$index" }
                val type = event.optString("type")
                val label = event.optString("label").ifBlank { type.toTimelineLabel() }
                val actor = actors[event.optString("actorId")] ?: "현장 기록자"
                val detail = event.optJSONObject("detail") ?: JSONObject()
                val subtitle =
                    when (type) {
                        "HANDOVER_MEMO" -> detail.optString("content").ifBlank { "${event.optString("occurredAt").toTimeLabel()} · $actor" }
                        "MARKER" -> markerSubtitle(event, detail, actor)
                        else -> "${event.optString("occurredAt").toTimeLabel()} · $actor"
                    }
                add(
                    HandoverRecord(
                        title = label,
                        subtitle = subtitle,
                        actionLabel = "보기",
                        sourceKey = eventId
                    )
                )
            }
        }

    private fun markerSubtitle(event: JSONObject, detail: JSONObject, actor: String): String {
        val markerType = detail.optString("markerType").toMarkerTypeLabel()
        val memo = detail.optString("memo").takeIf(String::isNotBlank)
        val photoCount = detail.optInt("photoCount", 0).coerceAtLeast(0)
        return listOfNotNull(
            event.optString("occurredAt").toTimeLabel(),
            actor,
            markerType,
            "사진 ${photoCount}장",
            memo
        ).joinToString(" · ")
    }

    private fun parseMetrics(metrics: JSONObject?): List<HandoverMetric> {
        if (metrics == null) {
            return emptyList()
        }
        return listOf(
            HandoverMetric(metrics.optLong("distanceMeters", 0L).toDistanceLabel(), "총 이동"),
            HandoverMetric("${metrics.optInt("markerCount", 0).coerceAtLeast(0)}건", "마커"),
            HandoverMetric("${metrics.optInt("handoverMemoCount", 0).coerceAtLeast(0)}건", "메모")
        )
    }

    private fun HandoverMemoReadModel.toRecord(): HandoverRecord =
        HandoverRecord(
            title = "운영 메모 · $targetType",
            subtitle = content,
            actionLabel = "열기",
            sourceKey = sourceKey
        )

    private fun HandoverSessionContext.valid(): RequiredHandoverSessionContext? {
        val incidentId = incidentId?.takeIf(String::isNotBlank) ?: return null
        val opId = opId?.takeIf(String::isNotBlank) ?: return null
        return RequiredHandoverSessionContext(
            incidentId = incidentId,
            opId = opId
        )
    }

    private fun emptyState(
        context: HandoverSessionContext,
        recordScope: HandoverRecordScope = HandoverRecordScope.DutyShift
    ): DutyHandoverUiState =
        DutyHandoverUiState.empty().copy(
            title = recordScope.title(),
            subtitle = context.subtitle(recordScope),
            recordScope = recordScope
        )

    private fun unavailableState(
        context: HandoverSessionContext,
        recordScope: HandoverRecordScope = HandoverRecordScope.DutyShift
    ): DutyHandoverUiState =
        DutyHandoverUiState.unavailable().copy(
            title = recordScope.title(),
            subtitle = context.subtitle(recordScope),
            recordScope = recordScope,
            records = emptyList(),
            metrics = emptyList()
        )

    private fun HandoverSessionContext.subtitle(
        recordScope: HandoverRecordScope = HandoverRecordScope.DutyShift,
        selectedDutyShift: DutyShiftOptionReadModel? = null
    ): String =
        "$displayOpLabel · ${selectedDutyShift?.contextLabel ?: "교대 인수인계"}"

    private fun HandoverRecordScope.title(selectedDutyShift: DutyShiftOptionReadModel? = null): String =
        if (this == HandoverRecordScope.DutyShift && selectedDutyShift != null) {
            selectedDutyShift.title
        } else {
            TITLE
        }

    private val DutyShiftOptionReadModel.title: String
        get() =
            when {
                current -> "현재 근무 확인"
                previous -> TITLE
                else -> "근무 구간 확인"
            }

    private fun parseItems(body: String): JSONArray {
        val trimmed = body.trim()
        return runCatching {
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                JSONObject(trimmed).optJSONArray("items") ?: JSONArray()
            }
        }.getOrElse { JSONArray() }
    }

    private fun List<DutyShiftOptionReadModel>.selectDutyShift(
        currentDutyShiftId: String?,
        selectedDutyShiftId: String?
    ): DutyShiftOptionReadModel? {
        val requested = selectedDutyShiftId?.takeIf(String::isNotBlank)
        if (requested != null) {
            firstOrNull { option -> option.dutyShiftId == requested }?.let { return it }
        }
        val current = currentDutyShiftId?.takeIf(String::isNotBlank)
        return firstOrNull { option -> option.dutyShiftId == current }
            ?: firstOrNull { option -> !option.previous }
    }

    private fun List<DutyShiftOptionReadModel>.replayFallbackCandidates(
        initialDutyShiftId: String?,
        currentDutyShiftId: String?
    ): List<DutyShiftOptionReadModel> {
        val current = currentDutyShiftId?.takeIf(String::isNotBlank)
        return buildList<DutyShiftOptionReadModel> {
            if (current != null) {
                this@replayFallbackCandidates
                    .firstOrNull { option -> option.dutyShiftId == current }
                    ?.let(::add)
            }
            addAll(this@replayFallbackCandidates.filter { option -> !option.previous && option.dutyShiftId != current })
        }.distinctBy(DutyShiftOptionReadModel::dutyShiftId)
            .filter { option -> option.dutyShiftId != initialDutyShiftId }
    }

    private fun List<DutyShiftOptionReadModel>.visibleDutyShiftOptions(
        currentDutyShiftId: String?,
        selectedDutyShiftId: String?
    ): List<DutyShiftOptionReadModel> {
        val current = currentDutyShiftId?.takeIf(String::isNotBlank)
        val selected = selectedDutyShiftId?.takeIf(String::isNotBlank)
        return filter { option ->
            !option.previous ||
                option.dutyShiftId == current ||
                option.dutyShiftId == selected
        }
    }

    private fun List<DutyShiftOptionReadModel>.markSelected(selectedDutyShiftId: String?): List<HandoverDutyShiftOption> =
        map { option ->
            HandoverDutyShiftOption(
                dutyShiftId = option.dutyShiftId,
                label = option.label,
                subtitle = option.subtitle,
                selected = selectedDutyShiftId != null && option.dutyShiftId == selectedDutyShiftId
            )
        }

    private fun String.toSummaryStatus(): SearchHistorySummaryStatus =
        when (uppercase()) {
            "READY" -> SearchHistorySummaryStatus.Ready
            "GENERATING", "PENDING", "PROCESSING" -> SearchHistorySummaryStatus.Generating
            "NEEDS_SUMMARY", "NEEDS_REGENERATION", "STALE" -> SearchHistorySummaryStatus.NeedsSummary
            "FAILED", "SUMMARY_UNAVAILABLE", "UNAVAILABLE" -> SearchHistorySummaryStatus.Unavailable
            else -> SearchHistorySummaryStatus.Empty
        }

    private fun String.toSourceReadiness(): SummarySourceReadiness =
        when (uppercase()) {
            "PENDING_SYNC" -> SummarySourceReadiness.PendingSync
            "STALE" -> SummarySourceReadiness.Stale
            else -> SummarySourceReadiness.Ready
        }

    private fun JSONObject?.optSyncStatus(): String =
        this?.optString("syncStatus").orEmpty()

    private fun String.toMovementLabel(): String =
        when (uppercase()) {
            "FOOT" -> "도보"
            "VEHICLE" -> "차량"
            "MIXED" -> "혼합"
            else -> "이동"
        }

    private fun String.toDutyShiftStatusLabel(): String =
        when (uppercase()) {
            "ACTIVE" -> "진행 중"
            "ENDED", "END" -> "종료"
            else -> ""
        }

    private fun String.toMarkerTypeLabel(): String =
        when (uppercase()) {
            "CLUE" -> "단서"
            "PERSON_FOUND" -> "발견"
            "FIELD_CONDITION" -> "현장 상태"
            "SUPPORT_REQUEST" -> "지원 요청"
            "NOTE" -> "메모"
            else -> "기록"
        }

    private fun String.toTimelineLabel(): String =
        when (uppercase()) {
            "PATH_START" -> "경로 시작"
            "PATH_SEGMENT" -> "이동 구간"
            "PATH_END" -> "경로 종료"
            "MARKER" -> "마커 기록"
            "HANDOVER_MEMO" -> "인수인계 메모"
            else -> "타임라인 기록"
        }

    private fun Long.toDistanceLabel(): String =
        if (this >= 1_000L) {
            "%.1fkm".format(this / 1_000.0)
        } else {
            "${coerceAtLeast(0L)}m"
        }

    private fun timeRangeLabel(startAt: String, endAt: String): String {
        val start = startAt.toTimeLabel()
        val end = endAt.toTimeLabel()
        return when {
            start == "시간 없음" && end == "시간 없음" -> "시간 없음"
            start == "시간 없음" -> end
            end == "시간 없음" -> start
            else -> "$start-$end"
        }
    }

    private fun dutyShiftLabel(current: Boolean, endedAt: Instant?, status: String): String =
        when {
            current -> "현재 근무"
            endedAt != null || status.isEndedStatus() -> "이전 근무"
            else -> "근무 구간"
        }

    private fun String.isEndedStatus(): Boolean =
        uppercase() in setOf("ENDED", "END")

    private fun dutyShiftSubtitle(
        dutyShiftId: String,
        startedAt: Instant?,
        endedAt: Instant?,
        status: String,
        dutyLabel: String,
        samePhoneScoped: Boolean
    ): String =
        listOf(
            if (samePhoneScoped) "같은 폴리폰" else "",
            dutyLabel,
            timeRangeLabel(startedAt?.toString().orEmpty(), endedAt?.toString().orEmpty()),
            status.toDutyShiftStatusLabel()
        ).filter { it.isNotBlank() && it != "시간 없음" }
            .joinToString(" · ")
            .ifBlank { dutyShiftId }

    private fun JSONObject.dutyShiftActorLabel(): String? =
        optString("policePhoneLabel")
            .ifBlank { optString("policePhoneDisplayName") }
            .ifBlank { optString("displayName") }
            .ifBlank { optString("policePhoneCode") }
            .ifBlank { optString("phoneCode") }
            .ifBlank {
                optString("policePhoneId")
                    .takeIf(String::isNotBlank)
                    ?.let { "폴리폰 ${it.takeLast(4)}" }
                    .orEmpty()
            }
            .takeIf(String::isNotBlank)

    private fun String.toTimeLabel(): String =
        toInstantOrNull()?.let(TimeFormatter::format) ?: "시간 없음"

    private fun String.toInstantOrNull(): Instant? =
        takeIf(String::isNotBlank)
            ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }

    private data class RequiredHandoverSessionContext(
        val incidentId: String,
        val opId: String
    )

    private data class TimedReplayPoint(
        val at: Instant,
        val lat: Double,
        val lng: Double
    )

    private data class TimelineReadModel(
        val available: Boolean,
        val summary: SummaryReadModel?,
        val metrics: List<HandoverMetric>,
        val records: List<HandoverRecord>,
        val replayPathSegments: List<HandoverReplayPathSegment>,
        val replayMarkers: List<HandoverReplayMarker>,
        val replayPoints: List<HandoverReplayPointUi>,
        val replayDurationMs: Long
    ) {
        companion object {
            fun unavailable(): TimelineReadModel =
                TimelineReadModel(
                    available = false,
                    summary = null,
                    metrics = emptyList(),
                    records = emptyList(),
                    replayPathSegments = emptyList(),
                    replayMarkers = emptyList(),
                    replayPoints = emptyList(),
                    replayDurationMs = 0L
                )
        }
    }

    private data class DutyTimelineSelection(
        val dutyShift: DutyShiftOptionReadModel?,
        val dutyShiftId: String?,
        val timeline: TimelineReadModel
    )

    private val TimelineReadModel.hasDisplayableEvidence: Boolean
        get() =
            available &&
                (
                    summary?.status in setOf(
                        SearchHistorySummaryStatus.Ready,
                        SearchHistorySummaryStatus.Generating,
                        SearchHistorySummaryStatus.NeedsSummary,
                        SearchHistorySummaryStatus.Unavailable
                    ) ||
                        !summary?.content.isNullOrBlank() ||
                        records.isNotEmpty() ||
                        replayPathSegments.isNotEmpty() ||
                        replayMarkers.isNotEmpty() ||
                        replayPoints.size >= 2
                    )

    private val TimelineReadModel.hasReplayEvidence: Boolean
        get() =
            available &&
                (
                    replayPoints.size >= 2 ||
                        replayPathSegments.any { segment -> segment.points.size >= 2 }
                    )

    private data class HandoverMemoReadModel(
        val targetType: String,
        val content: String,
        val sourceKey: String
    )

    private data class DutyShiftOptionReadModel(
        val dutyShiftId: String,
        val startedAt: Instant?,
        val endedAt: Instant?,
        val status: String,
        val current: Boolean,
        val dutyLabel: String,
        val actorLabel: String?,
        val label: String,
        val subtitle: String
    ) {
        val previous: Boolean = !current && (endedAt != null || status.uppercase() in setOf("ENDED", "END"))
        val contextLabel: String =
            listOfNotNull(dutyLabel, actorLabel)
                .filter(String::isNotBlank)
                .joinToString(" · ")
    }

    private data class SummaryReadModel(
        val status: SearchHistorySummaryStatus,
        val generatedAtLabel: String,
        val content: String?,
        val sourceReadiness: SummarySourceReadiness
    ) {
        companion object {
            fun empty(): SummaryReadModel =
                SummaryReadModel(
                    status = SearchHistorySummaryStatus.Empty,
                    generatedAtLabel = "이전 기록 없음",
                    content = null,
                    sourceReadiness = SummarySourceReadiness.Ready
                )

            fun unavailable(): SummaryReadModel =
                SummaryReadModel(
                    status = SearchHistorySummaryStatus.Unavailable,
                    generatedAtLabel = "요약을 불러오지 못했습니다 · 원본 기록 유지",
                    content = null,
                    sourceReadiness = SummarySourceReadiness.Ready
                )
        }
    }

    private companion object {
        const val TITLE = "이전 근무 확인"
        val TimeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul"))

        fun notFoundResponse(): SuriMapApiResponse =
            SuriMapApiResponse(statusCode = 404, body = null, errorCode = null)
    }
}
