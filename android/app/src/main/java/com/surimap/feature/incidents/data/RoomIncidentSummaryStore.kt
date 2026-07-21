package com.surimap.feature.incidents.data

import com.surimap.core.database.IncidentSummaryDao
import com.surimap.core.database.IncidentSummaryEntity
import com.surimap.feature.incidents.ui.AssignedIncidentUiModel
import com.surimap.feature.incidents.ui.IncidentPackageStatus

class RoomIncidentSummaryStore(
    private val dao: IncidentSummaryDao,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun findByAccountId(accountId: String): List<AssignedIncidentUiModel> =
        dao.findByAccountId(accountId).map(IncidentSummaryEntity::toUiModel)

    suspend fun replaceForAccount(
        accountId: String,
        incidents: List<AssignedIncidentUiModel>
    ) {
        val updatedAt = nowMillis()
        dao.replaceForAccount(
            accountId = accountId,
            incidents = incidents.mapIndexed { index, incident ->
                incident.toEntity(accountId, index, updatedAt)
            }
        )
    }
}

private fun IncidentSummaryEntity.toUiModel(): AssignedIncidentUiModel =
    AssignedIncidentUiModel(
        incidentId = incidentId,
        currentOpId = currentOpId,
        currentOpLabel = currentOpLabel,
        currentDutyShiftId = currentDutyShiftId,
        title = title,
        summary = summary,
        packageStatus = IncidentPackageStatus.valueOf(packageStatus)
    )

private fun AssignedIncidentUiModel.toEntity(
    accountId: String,
    displayOrder: Int,
    updatedAt: Long
): IncidentSummaryEntity =
    IncidentSummaryEntity(
        accountId = accountId,
        incidentId = incidentId,
        currentOpId = currentOpId,
        currentOpLabel = currentOpLabel,
        currentDutyShiftId = currentDutyShiftId,
        title = title,
        summary = summary,
        packageStatus = packageStatus.name,
        displayOrder = displayOrder,
        updatedAt = updatedAt
    )
