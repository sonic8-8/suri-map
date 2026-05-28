package com.surimap.feature.search.data

import com.surimap.core.path.AppendPathBatchCommand
import com.surimap.core.path.PathPoint
import com.surimap.core.path.PatchSearchPathCommand
import com.surimap.core.path.SearchPathLifecycleAction
import com.surimap.core.path.SearchPathRepository
import com.surimap.core.path.StartSearchPathCommand
import com.surimap.core.sync.SyncClient
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val MANUAL_PATH_ACCURACY_M = 5

data class SearchPathWriteContext(
    val incidentId: String?,
    val opId: String?,
    val policePhoneId: String?
)

data class ManualSearchPathPoint(
    val lon: Double,
    val lat: Double
)

data class ManualSearchPathCommand(
    val points: List<ManualSearchPathPoint>,
    val startedAt: Instant,
    val endedAt: Instant,
    val horizontalAccuracyM: Int? = MANUAL_PATH_ACCURACY_M
)

sealed interface SearchPathWriteResult {
    data object Blocked : SearchPathWriteResult

    data class Enqueued(
        val operationId: String,
        val outboxId: String,
        val entityId: String? = null
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
        return startAt(context = context, clientTs = now())
    }

    private suspend fun startAt(
        context: SearchPathWriteContext,
        clientTs: Instant
    ): SearchPathWriteResult {
        val valid = context.valid() ?: return SearchPathWriteResult.Blocked
        val operationId = idFactory("op-path-start")
        val searchPathId = idFactory("path")
        val result =
            repository.startSearchPath(
                StartSearchPathCommand(
                    operationId = operationId,
                    searchPathId = searchPathId,
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
            outboxId = result.outboxId,
            entityId = searchPathId
        )
    }

    suspend fun saveManualPath(
        context: SearchPathWriteContext,
        command: ManualSearchPathCommand
    ): SearchPathWriteResult {
        val valid = context.valid() ?: return SearchPathWriteResult.Blocked
        val normalizedPoints = command.points.filter { point ->
            point.lon.isFinite() && point.lat.isFinite()
        }
        if (normalizedPoints.size < MIN_BATCH_POINTS || normalizedPoints.size > MAX_BATCH_POINTS) {
            return SearchPathWriteResult.Blocked
        }
        if (!command.startedAt.isBefore(command.endedAt)) {
            return SearchPathWriteResult.Blocked
        }

        val operationId = idFactory("op-path-start")
        val searchPathId = idFactory("path")
        val startResult =
            repository.startSearchPath(
                StartSearchPathCommand(
                    operationId = operationId,
                    searchPathId = searchPathId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    clientTs = command.startedAt,
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )

        val batchOperationId = idFactory("op-path-batch")
        val batchResult =
            repository.appendPathBatch(
                AppendPathBatchCommand(
                    operationId = batchOperationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    searchPathId = searchPathId,
                    policePhoneId = valid.policePhoneId,
                    idempotencyKey = "idem-$batchOperationId",
                    sequence = sequenceSource(),
                    points =
                        normalizedPoints.toPathPoints(
                            startedAt = command.startedAt,
                            endedAt = command.endedAt,
                            horizontalAccuracyM = command.horizontalAccuracyM
                        ),
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )

        val endResult =
            patchLifecycleAt(
                context = context,
                searchPathId = searchPathId,
                action = SearchPathLifecycleAction.END,
                operationPrefix = "op-path-end",
                clientTs = command.endedAt
            )
        return if (endResult is SearchPathWriteResult.Enqueued) {
            endResult.copy(entityId = searchPathId)
        } else {
            SearchPathWriteResult.Enqueued(
                operationId = batchResult.operationId,
                outboxId = batchResult.outboxId,
                entityId = searchPathId
            )
        }
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
            outboxId = result.outboxId,
            entityId = pathId
        )
    }

    suspend fun end(
        context: SearchPathWriteContext,
        searchPathId: String?
    ): SearchPathWriteResult =
        patchLifecycle(
            context = context,
            searchPathId = searchPathId,
            action = SearchPathLifecycleAction.END,
            operationPrefix = "op-path-end"
        )

    suspend fun pause(
        context: SearchPathWriteContext,
        searchPathId: String?
    ): SearchPathWriteResult =
        patchLifecycle(
            context = context,
            searchPathId = searchPathId,
            action = SearchPathLifecycleAction.PAUSE,
            operationPrefix = "op-path-pause"
        )

    suspend fun resume(
        context: SearchPathWriteContext,
        searchPathId: String?
    ): SearchPathWriteResult =
        patchLifecycle(
            context = context,
            searchPathId = searchPathId,
            action = SearchPathLifecycleAction.RESUME,
            operationPrefix = "op-path-resume"
        )

    private suspend fun patchLifecycle(
        context: SearchPathWriteContext,
        searchPathId: String?,
        action: SearchPathLifecycleAction,
        operationPrefix: String
    ): SearchPathWriteResult {
        return patchLifecycleAt(
            context = context,
            searchPathId = searchPathId,
            action = action,
            operationPrefix = operationPrefix,
            clientTs = now()
        )
    }

    private suspend fun patchLifecycleAt(
        context: SearchPathWriteContext,
        searchPathId: String?,
        action: SearchPathLifecycleAction,
        operationPrefix: String,
        clientTs: Instant
    ): SearchPathWriteResult {
        val valid = context.valid() ?: return SearchPathWriteResult.Blocked
        val pathId = searchPathId?.takeIf(String::isNotBlank) ?: return SearchPathWriteResult.Blocked
        val operationId = idFactory(operationPrefix)
        val result =
            repository.patchSearchPath(
                PatchSearchPathCommand(
                    operationId = operationId,
                    incidentId = valid.incidentId,
                    opId = valid.opId,
                    searchPathId = pathId,
                    policePhoneId = valid.policePhoneId,
                    action = action,
                    idempotencyKey = "idem-$operationId",
                    sequence = sequenceSource(),
                    clientTs = clientTs,
                    clockOffsetMs = clockOffsetMs(),
                    clockSyncedAt = clockSyncedAt()
                )
            )
        return SearchPathWriteResult.Enqueued(
            operationId = result.operationId,
            outboxId = result.outboxId,
            entityId = pathId
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

    private fun List<ManualSearchPathPoint>.toPathPoints(
        startedAt: Instant,
        endedAt: Instant,
        horizontalAccuracyM: Int?
    ): List<PathPoint> {
        val durationMillis = ChronoUnit.MILLIS.between(startedAt, endedAt).coerceAtLeast((size - 1).toLong())
        val stepMillis = (durationMillis / (size - 1)).coerceAtLeast(1L)
        return mapIndexed { index, point ->
            PathPoint(
                pointId = idFactory("path-point"),
                lon = point.lon,
                lat = point.lat,
                speedMps = null,
                horizontalAccuracyM = horizontalAccuracyM,
                clientTs = startedAt.plusMillis(stepMillis * index)
            )
        }
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
