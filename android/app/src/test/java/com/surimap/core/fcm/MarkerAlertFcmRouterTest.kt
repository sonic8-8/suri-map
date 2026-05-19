package com.surimap.core.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkerAlertFcmRouterTest {

    @Test
    fun personFoundDataMessageCreatesMarkerAlertPayload() {
        val payload =
            MarkerAlertFcmRouter.payload(
                mapOf(
                    "eventId" to "evt-person-found-001",
                    "type" to "PERSON_FOUND",
                    "incidentId" to INCIDENT_ID,
                    "markerId" to MARKER_ID,
                    "markerType" to "PERSON_FOUND",
                    "locationLabel" to "126.913400,35.163100"
                )
            )

        assertEquals(
            MarkerAlertFcmPayload(
                eventId = "evt-person-found-001",
                eventType = "PERSON_FOUND",
                incidentId = INCIDENT_ID,
                markerId = MARKER_ID,
                markerType = "PERSON_FOUND",
                locationLabel = "126.913400,35.163100"
            ),
            payload
        )
    }

    @Test
    fun supportRequestDataMessageCreatesMarkerAlertPayload() {
        val payload =
            MarkerAlertFcmRouter.payload(
                mapOf(
                    "eventId" to "evt-support-001",
                    "eventType" to "SUPPORT_REQUEST_CREATED",
                    "incidentId" to INCIDENT_ID,
                    "markerId" to MARKER_ID,
                    "markerType" to "SUPPORT_REQUEST"
                )
            )

        assertEquals("SUPPORT_REQUEST_CREATED", payload?.eventType)
        assertEquals(MARKER_ID, payload?.markerId)
    }

    @Test
    fun markerAlertRequiresMarkerId() {
        val payload =
            MarkerAlertFcmRouter.payload(
                mapOf(
                    "type" to "PERSON_FOUND",
                    "incidentId" to INCIDENT_ID
                )
            )

        assertNull(payload)
    }

    private companion object {
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
        const val MARKER_ID = "55555555-5555-5555-5555-555555550802"
    }
}
