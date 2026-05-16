package com.surimap.core.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncidentAssignmentFcmRouterTest {

    @Test
    fun incidentCreatedDataMessageRequestsIncidentListRefresh() {
        val refresh =
            IncidentAssignmentFcmRouter.refreshPayload(
                mapOf(
                    "type" to "INCIDENT_CREATED",
                    "incidentId" to INCIDENT_ID,
                    "version" to "1"
                )
            )

        assertEquals(IncidentAssignmentFcmRefresh("INCIDENT_CREATED", INCIDENT_ID), refresh)
    }

    @Test
    fun assignmentChangedDataMessageRequestsIncidentListRefresh() {
        val refresh =
            IncidentAssignmentFcmRouter.refreshPayload(
                mapOf(
                    "eventType" to "INCIDENT_ASSIGNMENT_CHANGED",
                    "incidentId" to INCIDENT_ID,
                    "version" to "2"
                )
            )

        assertEquals(IncidentAssignmentFcmRefresh("INCIDENT_ASSIGNMENT_CHANGED", INCIDENT_ID), refresh)
    }

    @Test
    fun markerNotificationDoesNotRefreshIncidentList() {
        val refresh =
            IncidentAssignmentFcmRouter.refreshPayload(
                mapOf(
                    "type" to "SUPPORT_REQUEST_CREATED",
                    "incidentId" to INCIDENT_ID
                )
            )

        assertNull(refresh)
    }

    private companion object {
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
    }
}
