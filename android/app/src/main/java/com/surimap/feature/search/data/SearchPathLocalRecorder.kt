package com.surimap.feature.search.data

import com.surimap.core.path.AppendPathBatchCommand
import com.surimap.core.path.EndSearchPathCommand
import com.surimap.core.path.PathPoint
import com.surimap.core.path.SearchPathRepository
import com.surimap.core.path.StartSearchPathCommand
import com.surimap.core.sync.SyncClient
import java.time.Instant
import java.util.UUID

data class SearchPathWriteContext(
    val incidentId: String?,
    val opId: String?,
    val policePhoneId: String?
)

sealed interface SearchPathWriteResult {
    data object Blocked : SearchPathWriteResult

    data class Enqueued(
        val operationId: String,
        val outboxId: String
    ) : SearchPathWriteResult
}

class SearchPathLocalRecorder(
    syncClient: SyncClient,
    private val now: () -> Instant = { Instant.now() },
    private val clockOffsetMs: () -> Long? = { 0L },
    private val clockSyncedAt: () -> Instant? = { now() },
    private val sequenceSource: () -> Long = { System.currentTimeMillis() },
    private val idFactory: (String) -> String = { _ -> UUID.randomUUID().toString() }
) {
    private val repository = SearchPathRepository(syncClient = syncClient)

    suspend fun start(context: SearchPathWriteContext): SearchPathWriteResult {
        val valid = context.valid() ?: return SearchPathWriteResult.Blocked
        val operationId = idFactory("op-path-start")
        val clientTs = now()
        val result =
            repository.startSearchPath(
                StartSearchPathCommand(
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    clientTs = clientTs,
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )
        return SearchPathWriteResult.Enqueued(
            operationId = result.operationId,
            outboxId = result.outboxId
        )
    }

    suspend fun appendBatch(
        context: SearchPathWriteContext,
        searchPathId: String?,
        points: List<PathPoint>
    ): SearchPathWriteResult {
        val valid = context.valid() ?: return SearchPathWriteResult.Blocked
        val pathId = searchPathId?.takeIf(String::isNotBlank) ?: return SearchPathWriteResult.Blocked
        if (points.size < MIN_BATCH_POINTS || points.size > MAX_BATCH_POINTS) {
            return SearchPathWriteResult.Blocked
        }
        val operationId = idFactory("op-path-batch")
        val result =
            repository.appendPathBatch(
                AppendPathBatchCommand(
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    searchPathId = pathId,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    points = points,
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )
        return SearchPathWriteResult.Enqueued(
            operationId = result.operationId,
            outboxId = result.outboxId
        )
    }

    suspend fun end(
        context: SearchPathWriteContext,
        searchPathId: String?
    ): SearchPathWriteResult {
        val valid = context.valid() ?: return SearchPathWriteResult.Blocked
        val pathId = searchPathId?.takeIf(String::isNotBlank) ?: return SearchPathWriteResult.Blocked
        val operationId = idFactory("op-path-end")
        val clientTs = now()
        val result =
            repository.endSearchPath(
                EndSearchPathCommand(
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    searchPathId = pathId,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    clientTs = clientTs,
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )
        return SearchPathWriteResult.Enqueued(
            operationId = result.operationId,
            outboxId = result.outboxId
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

    private companion object {
        const val MIN_BATCH_POINTS = 2
        const val MAX_BATCH_POINTS = 120
    }
}
