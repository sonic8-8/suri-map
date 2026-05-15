package com.surimap.feature.marker

import com.surimap.core.marker.MarkerReadQuery
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.feature.marker.data.MarkerDetailSessionContext
import com.surimap.feature.marker.data.MarkerDetailStateLoader
import com.surimap.feature.marker.ui.MarkerDetailPhotoStatus
import com.surimap.feature.marker.ui.MarkerType
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.markerIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.photoIdFixture
import com.surimap.testing.policePhoneIdFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MarkerDetailStateLoaderTest {

    @Test
    fun markerQueryResultMapsVersionLocationPermissionAndPhotoSummary() = runBlocking {
        var capturedQuery: MarkerReadQuery? = null
        val loader =
            MarkerDetailStateLoader { query ->
                capturedQuery = query
                SuriMapApiResponse(
                    statusCode = 200,
                    body =
                    """
                    {
                      "incidentId": "$INCIDENT_ID",
                      "markers": [
                        {
                          "id": "$MARKER_ID",
                          "incidentId": "$INCIDENT_ID",
                          "opId": "$OP_ID",
                          "accountId": "acct-field-alpha",
                          "policePhoneId": "$POLICE_PHONE_ID",
                          "type": "CLUE",
                          "status": "ACTIVE",
                          "version": 7,
                          "location": {"type": "Point", "coordinates": [126.970123, 37.580123]},
                          "memo": "검정 패딩 발견",
                          "occurredAt": "2026-05-14T02:30:00Z",
                          "photoSummary": [
                            {"photoId": "$PHOTO_ID", "status": "ATTACHED", "version": 2}
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                    errorCode = null
                )
            }

        val state = loader.load(CONTEXT)

        assertEquals(MarkerReadQuery(incidentId = INCIDENT_ID, opId = OP_ID), capturedQuery)
        assertEquals(MARKER_ID, state.markerId)
        assertEquals(MarkerType.CLUE, state.markerType)
        assertEquals(7L, state.version)
        assertEquals(126.970123, state.lon!!, 0.000001)
        assertEquals(37.580123, state.lat!!, 0.000001)
        assertTrue(state.canEdit)
        assertTrue(state.canSave)
        assertTrue(state.canDelete)
        assertEquals("v7", state.versionLabel)
        assertEquals("ACTIVE", state.syncLabel)
        assertEquals("37.580123, 126.970123", state.locationLabel)
        assertEquals(1, state.photos.size)
        assertEquals(MarkerDetailPhotoStatus.Attached, state.photos.single().status)
    }

    @Test
    fun markerCreatedByDifferentPolicePhoneIsReadonly() = runBlocking {
        val loader =
            MarkerDetailStateLoader {
                SuriMapApiResponse(
                    statusCode = 200,
                    body =
                    """
                    {
                      "markers": [
                        {
                          "id": "$MARKER_ID",
                          "accountId": "acct-other",
                          "policePhoneId": "${policePhoneIdFixture("other-001")}",
                          "type": "PERSON_FOUND",
                          "status": "ACTIVE",
                          "version": 3,
                          "geometry": {"type": "Point", "coordinates": [126.95, 35.162]},
                          "memo": "타 단말 작성"
                        }
                      ]
                    }
                    """.trimIndent(),
                    errorCode = null
                )
            }

        val state = loader.load(CONTEXT)

        assertFalse(state.canEdit)
        assertFalse(state.canSave)
        assertFalse(state.canDelete)
        assertTrue(state.visibleText().any { it.contains("읽기 전용") })
    }

    @Test
    fun missingMarkerOrReadFailureReturnsUnavailableState() = runBlocking {
        val missingLoader =
            MarkerDetailStateLoader {
                SuriMapApiResponse(statusCode = 200, body = """{"markers":[]}""", errorCode = null)
            }
        val failureLoader =
            MarkerDetailStateLoader {
                SuriMapApiResponse(statusCode = 500, body = """{"error":"server_error"}""", errorCode = "server_error")
            }

        val missing = missingLoader.load(CONTEXT)
        val failure = failureLoader.load(CONTEXT)
        val invalid = missingLoader.load(CONTEXT.copy(incidentId = null))

        assertEquals("조회 실패", missing.syncLabel)
        assertEquals("조회 실패", failure.syncLabel)
        assertEquals("조회 실패", invalid.syncLabel)
        assertFalse(missing.canEdit)
        assertFalse(failure.canSave)
        assertFalse(invalid.canDelete)
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("003")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val MARKER_ID = markerIdFixture("detail-001")
        val PHOTO_ID = photoIdFixture("photo-001")
        val CONTEXT =
            MarkerDetailSessionContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                policePhoneId = POLICE_PHONE_ID,
                markerId = MARKER_ID
            )
    }
}
