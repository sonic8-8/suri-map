package com.surimap.core.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncidentClosedFcmRouterTest {

    @Test
    fun incidentClosedDataMessageCreatesClosedPayload() {
        val payload =
            IncidentClosedFcmRouter.payload(
                mapOf(
                    "eventId" to EVENT_ID,
                    "type" to "INCIDENT_CLOSED",
                    "incidentId" to INCIDENT_ID,
                    "closedAt" to CLOSED_AT,
                    "purgeRunId" to PURGE_RUN_ID
                )
            )

        assertEquals(
            IncidentClosedFcmPayload(
                eventId = EVENT_ID,
                incidentId = INCIDENT_ID,
                closedAt = CLOSED_AT,
                purgeRunId = PURGE_RUN_ID
            ),
            payload
        )
    }

    @Test
    fun incidentClosedRequiresClosedAt() {
        val payload =
            IncidentClosedFcmRouter.payload(
                mapOf(
                    "eventType" to "INCIDENT_CLOSED",
                    "incidentId" to INCIDENT_ID
                )
            )

        assertNull(payload)
    }

    @Test
    fun assignmentRefreshDoesNotCreateClosedPayload() {
        val payload =
            IncidentClosedFcmRouter.payload(
                mapOf(
                    "type" to "INCIDENT_ASSIGNMENT_CHANGED",
                    "incidentId" to INCIDENT_ID,
                    "closedAt" to CLOSED_AT
                )
            )

        assertNull(payload)
    }

    private companion object {
        const val EVENT_ID = "evt-incident-closed-001"
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
        const val CLOSED_AT = "2026-05-20T06:16:39.613400Z"
        const val PURGE_RUN_ID = "purge-run-incident-closed-001"
    }
}
