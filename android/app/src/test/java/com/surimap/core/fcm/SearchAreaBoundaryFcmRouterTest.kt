package com.surimap.core.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchAreaBoundaryFcmRouterTest {
    @Test
    fun boundaryExitedDataMessageRoutesToBoundaryAlert() {
        val payload =
            SearchAreaBoundaryFcmRouter.payload(
                mapOf(
                    "type" to "SEARCH_AREA_BOUNDARY_EXITED",
                    "eventId" to "evt-boundary-001",
                    "incidentId" to INCIDENT_ID,
                    "searchAreaId" to SEARCH_AREA_ID,
                    "policePhoneId" to POLICE_PHONE_ID
                )
            )

        assertEquals(
            SearchAreaBoundaryFcmPayload(
                eventId = "evt-boundary-001",
                incidentId = INCIDENT_ID,
                searchAreaId = SEARCH_AREA_ID,
                policePhoneId = POLICE_PHONE_ID
            ),
            payload
        )
    }

    @Test
    fun otherDataMessageDoesNotRouteToBoundaryAlert() {
        val payload =
            SearchAreaBoundaryFcmRouter.payload(
                mapOf(
                    "type" to "INCIDENT_ASSIGNMENT_CHANGED",
                    "incidentId" to INCIDENT_ID
                )
            )

        assertNull(payload)
    }

    private companion object {
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
        const val SEARCH_AREA_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001"
        const val POLICE_PHONE_ID = "cccccccc-cccc-cccc-cccc-cccccccc0001"
    }
}
