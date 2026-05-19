package com.surimap.feature.handover.data

import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.network.SuriMapNetworkException
import com.surimap.core.operationalperiod.HandoverMemoQuery
import com.surimap.core.operationalperiod.SearchHistorySummaryQuery
import com.surimap.feature.handover.ui.DutyHandoverUiState
import com.surimap.feature.handover.ui.HandoverMetric
import com.surimap.feature.handover.ui.HandoverRecord
import com.surimap.feature.handover.ui.SearchHistorySummaryStatus
import com.surimap.feature.handover.ui.SummarySourceReadiness
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
    private val handoverMemos: suspend (HandoverMemoQuery) -> SuriMapApiResponse = {
        notFoundResponse()
    },
    private val searchHistorySummaries: suspend (String, SearchHistorySummaryQuery) -> SuriMapApiResponse = { _, _ ->
        notFoundResponse()
    }
) {
    suspend fun load(context: HandoverSessionContext): DutyHandoverUiState {
        val valid = context.valid() ?: return emptyState(context)
        return try {
            val memoResponse =
                handoverMemos(
                    HandoverMemoQuery(
                        incidentId = valid.incidentId,
                        opId = valid.opId
                    )
                )
            val dutyShiftId = context.dutyShiftId?.takeIf(String::isNotBlank)
            val memos = parseMemos(memoResponse)
            val summary =
                if (dutyShiftId == null) {
                    SummaryReadModel.empty()
                } else {
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
                }
            summary.toUiState(
                context = context,
                memoCount = memos.size,
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
        memoCount: Int,
        records: List<HandoverRecord>
    ): DutyHandoverUiState =
        DutyHandoverUiState(
            title = TITLE,
            subtitle = context.subtitle(),
            summaryStatus = status,
            generatedAtLabel = generatedAtLabel,
            summary = content,
            sourceReadiness = sourceReadiness,
            metrics = listOf(HandoverMetric("${memoCount}건", "메모")),
            records = records,
            canRequestSummaryGeneration = false
        )

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

    private fun emptyState(context: HandoverSessionContext): DutyHandoverUiState =
        DutyHandoverUiState.empty().copy(
            title = TITLE,
            subtitle = context.subtitle()
        )

    private fun unavailableState(context: HandoverSessionContext): DutyHandoverUiState =
        DutyHandoverUiState.unavailable().copy(
            title = TITLE,
            subtitle = context.subtitle(),
            records = emptyList(),
            metrics = emptyList()
        )

    private fun HandoverSessionContext.subtitle(): String {
        return "$displayOpLabel · 교대 인수인계"
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

    private data class RequiredHandoverSessionContext(
        val incidentId: String,
        val opId: String
    )

    private data class HandoverMemoReadModel(
        val targetType: String,
        val content: String,
        val sourceKey: String
    )

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

        fun notFoundResponse(): SuriMapApiResponse =
            SuriMapApiResponse(statusCode = 404, body = null, errorCode = null)
    }
}
