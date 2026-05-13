package com.surimap.feature.incidents

import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.operationalperiod.DutyShiftQuery
import com.surimap.feature.incidents.data.IncidentSessionContextResolver
import com.surimap.feature.incidents.ui.AssignedIncidentUiModel
import com.surimap.testing.dutyShiftIdFixture
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.policePhoneIdFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IncidentSessionContextResolverTest {
    @Test
    fun missingCurrentOpUsesOperationalPeriodsCurrentOpForIncidentContext() = runBlocking {
        val resolver =
            IncidentSessionContextResolver(
                operationalPeriods = { incidentId ->
                    assertEquals(INCIDENT_ID, incidentId)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body = """{"currentOpId":"$OP_ID","items":[]}""",
                        errorCode = null
                    )
                }
            )

        val context = resolver.resolve(incident(currentOpId = null))

        assertEquals(INCIDENT_ID, context.incidentId)
        assertEquals(OP_ID, context.currentOpId)
        assertNull(context.currentDutyShiftId)
    }

    @Test
    fun missingCurrentDutyShiftUsesActiveDutyShiftForIncidentContext() = runBlocking {
        val resolver =
            IncidentSessionContextResolver(
                operationalPeriods = {
                    SuriMapApiResponse(
                        statusCode = 200,
                        body = """{"currentOpId":"$OP_ID","items":[]}""",
                        errorCode = null
                    )
                },
                dutyShifts = { query: DutyShiftQuery ->
                    assertEquals(INCIDENT_ID, query.incidentId)
                    assertEquals(OP_ID, query.opId)
                    assertEquals(POLICE_PHONE_ID, query.policePhoneId)
                    assertEquals("ACTIVE", query.status)
                    SuriMapApiResponse(
                        statusCode = 200,
                        body = """{"items":[{"id":"$DUTY_SHIFT_ID","status":"ACTIVE"}]}""",
                        errorCode = null
                    )
                }
            )

        val context = resolver.resolve(incident(currentOpId = null), policePhoneId = POLICE_PHONE_ID)

        assertEquals(OP_ID, context.currentOpId)
        assertEquals(DUTY_SHIFT_ID, context.currentDutyShiftId)
    }

    private fun incident(currentOpId: String?): AssignedIncidentUiModel =
        AssignedIncidentUiModel(
            incidentId = INCIDENT_ID,
            currentOpId = currentOpId,
            currentDutyShiftId = null,
            title = "종로구 인왕산 실종 신고",
            summary = "상태 OPEN",
            packageStatus = "오프라인 패키지 확인 전",
            assignmentStatus = "이 폴리폰에서 선택 가능"
        )

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-001-op1")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-car-01")
        val DUTY_SHIFT_ID = dutyShiftIdFixture("precinct-op1-001")
    }
}
