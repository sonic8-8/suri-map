package com.surimap.feature.search.data

import androidx.room.withTransaction
import com.surimap.core.database.SearchRecordingStateEntity
import com.surimap.core.database.SuriMapDatabase
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.feature.search.ui.SearchMapUiState

class RoomSearchRecordingStateStore(
    private val database: SuriMapDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val dao = database.searchRecordingStateDao()
    private val outboxDao = database.outboxDao()

    suspend fun saveServerState(
        context: SearchMapSessionContext,
        state: SearchMapUiState
    ) {
        val accountId = context.accountId?.takeIf(String::isNotBlank) ?: return
        val incidentId = context.incidentId?.takeIf(String::isNotBlank) ?: return
        database.withTransaction {
            if (outboxDao.countUnresolvedSearchPathLifecycleRequests(incidentId) > 0) {
                return@withTransaction
            }

            val searchPathId = state.activeSearchPathId?.takeIf(String::isNotBlank)
            val lifecycleStatus =
                when (state.lifecycleStatus) {
                    SearchLifecycleStatus.Active -> "ACTIVE"
                    SearchLifecycleStatus.Paused -> "PAUSED"
                    else -> null
                }
            if (searchPathId == null || lifecycleStatus == null) {
                dao.delete(accountId, incidentId)
                return@withTransaction
            }

            val opId = context.currentOpId?.takeIf(String::isNotBlank) ?: return@withTransaction
            val now = nowMillis()
            dao.upsert(
                SearchRecordingStateEntity(
                    accountId = accountId,
                    incidentId = incidentId,
                    opId = opId,
                    searchPathId = searchPathId,
                    lifecycleStatus = lifecycleStatus,
                    activeStartedAt =
                        if (lifecycleStatus == "ACTIVE") {
                            state.activeSearchPathStartedAtEpochMs ?: now
                        } else {
                            null
                        },
                    accumulatedElapsed = 0L,
                    updatedAt = now
                )
            )
        }
    }
}
