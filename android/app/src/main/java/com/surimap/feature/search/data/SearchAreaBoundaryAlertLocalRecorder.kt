package com.surimap.feature.search.data

import com.surimap.core.location.GpsLocationFix
import com.surimap.core.searcharea.CreateSearchAreaBoundaryAlertCommand
import com.surimap.core.searcharea.SearchAreaBoundaryAlertRepository
import com.surimap.core.sync.SyncClient
import com.surimap.feature.search.domain.AssignedSearchAreaBoundary
import java.time.Instant
import java.util.UUID

class SearchAreaBoundaryAlertLocalRecorder(
    syncClient: SyncClient,
    private val now: () -> Instant = { Instant.now() },
    private val clockOffsetMs: () -> Long? = { 0L },
    private val clockSyncedAt: () -> Instant? = { now() },
    private val sequenceSource: () -> Long = { System.currentTimeMillis() },
    private val idFactory: (String) -> String = { _ -> UUID.randomUUID().toString() }
) {
    private val repository = SearchAreaBoundaryAlertRepository(syncClient = syncClient)

    suspend fun outsideAssignedArea(
        context: SearchPathWriteContext,
        boundary: AssignedSearchAreaBoundary,
        searchPathId: String?,
        fix: GpsLocationFix
    ): SearchPathWriteResult {
        val valid = context.valid() ?: return SearchPathWriteResult.Blocked
        val searchAreaId = boundary.searchAreaId.takeIf(String::isNotBlank)
            ?: return SearchPathWriteResult.Blocked
        val operationId = idFactory("op-search-area-boundary-alert")
        val result =
            repository.create(
                CreateSearchAreaBoundaryAlertCommand(
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    searchAreaId = searchAreaId,
                    policePhoneId = valid.policePhoneId,
                    alertType = "OUTSIDE_ASSIGNED_AREA",
                    lon = fix.lon,
                    lat = fix.lat,
                    searchPathId = searchPathId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    clientTs = fix.capturedAt.takeIf { it != Instant.EPOCH } ?: now(),
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )
        return SearchPathWriteResult.Enqueued(
            operationId = result.operationId,
            outboxId = result.outboxId,
            entityId = searchAreaId
        )
    }

    private fun SearchPathWriteContext.valid(): RequiredSearchPathContext? {
        val incidentId = incidentId?.takeIf(String::isNotBlank) ?: return null
        val opId = opId?.takeIf(String::isNotBlank) ?: return null
        val policePhoneId = policePhoneId?.takeIf(String::isNotBlank) ?: return null
        return RequiredSearchPathContext(
            incidentId = incidentId,
            opId = opId,
            policePhoneId = policePhoneId
        )
    }

    private data class RequiredSearchPathContext(
        val incidentId: String,
        val opId: String,
        val policePhoneId: String
    )
}
