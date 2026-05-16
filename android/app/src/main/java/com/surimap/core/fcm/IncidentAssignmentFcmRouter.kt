package com.surimap.core.fcm

data class IncidentAssignmentFcmRefresh(
    val eventType: String,
    val incidentId: String
)

object IncidentAssignmentFcmRouter {
    fun refreshPayload(data: Map<String, String>): IncidentAssignmentFcmRefresh? {
        val eventType = data["type"] ?: data["eventType"] ?: return null
        if (eventType != "INCIDENT_CREATED" && eventType != "INCIDENT_ASSIGNMENT_CHANGED") {
            return null
        }
        return IncidentAssignmentFcmRefresh(
            eventType = eventType,
            incidentId = data["incidentId"].orEmpty()
        )
    }
}
