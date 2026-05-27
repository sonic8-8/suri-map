package com.surimap.feature.outbox.data

import com.surimap.core.database.OutboxEntity
import com.surimap.core.database.OutboxStatusSummary
import com.surimap.core.sync.OutboxDiagnosticsClassifier
import com.surimap.core.sync.OutboxStatus
import com.surimap.feature.outbox.ui.BlockedOutboxItemUiState
import com.surimap.feature.outbox.ui.BlockedOutboxReason
import com.surimap.feature.outbox.ui.BlockedOutboxUiState
import com.surimap.feature.outbox.ui.PendingOutboxSummaryUiState
import java.time.Instant

data class BlockedOutboxQuery(
    val incidentId: String?,
    val policePhoneId: String?
)

class BlockedOutboxStateLoader(
    private val rowsByIncident: suspend (String) -> List<OutboxEntity> = { emptyList() },
    private val statusSummary: suspend (String, String) -> OutboxStatusSummary? = { _, _ -> null },
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun load(query: BlockedOutboxQuery): BlockedOutboxUiState {
        val incidentId = query.incidentId?.takeIf(String::isNotBlank)
        val policePhoneId = query.policePhoneId?.takeIf(String::isNotBlank)
        if (incidentId == null || policePhoneId == null) {
            return fallback(incidentId = incidentId, policePhoneId = policePhoneId)
        }
        val rows =
            rowsByIncident(incidentId)
                .filter { row ->
                    row.policePhoneId == policePhoneId &&
                        row.idempotencyStatus !in setOf(OutboxStatus.ACKED.name, OutboxStatus.PURGED.name)
                }
        val summary = statusSummary(incidentId, policePhoneId)
        return BlockedOutboxUiState(
            title = "미전송 기록",
            subtitle = "현재 사건 · 단말 기록",
            blockedItems = rows.mapNotNull(::blockedItem).sortedBy { it.timestampLabel },
            pendingSummary = summary?.pendingSummary()
        )
    }

    private fun fallback(incidentId: String?, policePhoneId: String?): BlockedOutboxUiState =
        BlockedOutboxUiState(
            title = "미전송 기록",
            subtitle = listOfNotNull(policePhoneId, incidentId).joinToString(" · ").ifBlank { "사건 선택 필요" },
            blockedItems = emptyList(),
            pendingSummary = null
        )

    private fun blockedItem(row: OutboxEntity): BlockedOutboxItemUiState? {
        val diagnostic = OutboxDiagnosticsClassifier.classify(row)
        val include =
            diagnostic.retryable || row.idempotencyStatus == OutboxStatus.FAILED_FINAL.name
        if (!include) {
            return null
        }
        return BlockedOutboxItemUiState(
            operationId = row.operationId,
            title = row.title(),
            timestampLabel = Instant.ofEpochMilli(row.clientRequestedAt).toString(),
            reason = diagnostic.userSafeFailureCategory.toReason(),
            retryable = diagnostic.retryable
        )
    }

    private fun OutboxStatusSummary.pendingSummary(): PendingOutboxSummaryUiState? {
        val count = normalUnsentCount
        if (count <= 0) {
            return null
        }
        val oldestAgeLabel =
            oldestPendingClientRequestedAt
                ?.let { oldest -> "${((nowMs() - oldest).coerceAtLeast(0L) / 60_000L).coerceAtLeast(1L)}분 전" }
                ?: "확인 중"
        return PendingOutboxSummaryUiState(
            count = count,
            detailLabel = "자동 대기 $pendingCount · 재전송 $retryableCount",
            oldestAgeLabel = oldestAgeLabel
        )
    }

    private fun OutboxEntity.title(): String =
        when (dependencyGroup) {
            "PATH" -> "경로 기록"
            "MARKER" -> "마커 기록"
            "PHOTO" -> "사진 첨부"
            "HANDOVER_MEMO" -> "인수인계 메모"
            "DUTY_SHIFT" -> "근무 교대"
            "PACKAGE_INSTALLATION" -> "지도 데이터 상태"
            else -> requestPath
        }

    private fun String.toReason(): BlockedOutboxReason =
        when (this) {
            "CLOCK_RESYNC_REQUIRED" -> BlockedOutboxReason.ClockResyncRequired
            "RETRYABLE_NETWORK" -> BlockedOutboxReason.RetryableNetwork
            "CLOSED_NO_RETRY" -> BlockedOutboxReason.IncidentClosed
            "POLICE_PHONE_ACCESS_REQUIRED" -> BlockedOutboxReason.PolicePhoneAccessRequired
            "NON_RETRYABLE_CONFLICT" -> BlockedOutboxReason.PayloadValidationFailure
            else -> BlockedOutboxReason.PayloadValidationFailure
        }
}
