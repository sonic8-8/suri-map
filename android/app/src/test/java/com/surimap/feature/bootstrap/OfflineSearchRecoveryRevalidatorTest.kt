package com.surimap.feature.bootstrap

import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.network.SuriMapNetworkException
import com.surimap.feature.bootstrap.data.OfflineSearchInvalidReason
import com.surimap.feature.bootstrap.data.OfflineSearchRecoveryRevalidator
import com.surimap.feature.bootstrap.data.OfflineSearchRevalidationResult
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineSearchRecoveryRevalidatorTest {
    @Test
    fun assignedIncidentWithSameOpKeepsRecoveredSearch() = runBlocking {
        val revalidator =
            revalidator(
                incidentsBody =
                    """{"items":[{"incidentId":"$INCIDENT_ID","currentOpId":"$OP_ID"}]}"""
            )

        assertEquals(
            OfflineSearchRevalidationResult.Valid,
            revalidator.validate(INCIDENT_ID, OP_ID)
        )
    }

    @Test
    fun missingAssignedIncidentInvalidatesRecoveredSearch() = runBlocking {
        val revalidator = revalidator(incidentsBody = """{"items":[]}""")

        assertEquals(
            OfflineSearchRevalidationResult.Invalid(
                OfflineSearchInvalidReason.IncidentUnavailable
            ),
            revalidator.validate(INCIDENT_ID, OP_ID)
        )
    }

    @Test
    fun changedCurrentOpInvalidatesRecoveredSearch() = runBlocking {
        val revalidator =
            revalidator(
                incidentsBody =
                    """{"items":[{"incidentId":"$INCIDENT_ID","currentOpId":"$OTHER_OP_ID"}]}"""
            )

        assertEquals(
            OfflineSearchRevalidationResult.Invalid(
                OfflineSearchInvalidReason.OperationalPeriodChanged
            ),
            revalidator.validate(INCIDENT_ID, OP_ID)
        )
    }

    @Test
    fun missingOpInIncidentListUsesOperationalPeriodResponse() = runBlocking {
        val revalidator =
            revalidator(
                incidentsBody = """{"items":[{"incidentId":"$INCIDENT_ID"}]}""",
                operationalPeriodsBody = """{"currentOpId":"$OP_ID","items":[]}"""
            )

        assertEquals(
            OfflineSearchRevalidationResult.Valid,
            revalidator.validate(INCIDENT_ID, OP_ID)
        )
    }

    @Test
    fun networkFailureRetriesWithoutInvalidatingRecoveredSearch() = runBlocking {
        val revalidator =
            OfflineSearchRecoveryRevalidator(
                assignedIncidents = {
                    throw SuriMapNetworkException(IOException("offline"))
                },
                operationalPeriods = { error("must not be called") }
            )

        assertEquals(
            OfflineSearchRevalidationResult.Retry,
            revalidator.validate(INCIDENT_ID, OP_ID)
        )
    }

    @Test
    fun confirmedIncidentAccessErrorsInvalidateRecoveredSearch() = runBlocking {
        val errors =
            listOf(
                "incident_closed",
                "police_phone_not_assigned",
                "incident_access_denied",
                "team_not_assigned"
            )

        errors.forEach { errorCode ->
            val revalidator =
                OfflineSearchRecoveryRevalidator(
                    assignedIncidents = { SuriMapApiResponse(403, null, errorCode) },
                    operationalPeriods = { error("must not be called") }
                )

            assertEquals(
                OfflineSearchRevalidationResult.Invalid(
                    OfflineSearchInvalidReason.IncidentUnavailable
                ),
                revalidator.validate(INCIDENT_ID, OP_ID)
            )
        }
    }

    private fun revalidator(
        incidentsBody: String,
        operationalPeriodsBody: String = """{"currentOpId":"$OP_ID","items":[]}"""
    ): OfflineSearchRecoveryRevalidator =
        OfflineSearchRecoveryRevalidator(
            assignedIncidents = { SuriMapApiResponse(200, incidentsBody, null) },
            operationalPeriods = { SuriMapApiResponse(200, operationalPeriodsBody, null) }
        )

    private companion object {
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
        const val OP_ID = "88888888-8888-8888-8888-888888880001"
        const val OTHER_OP_ID = "88888888-8888-8888-8888-888888880002"
    }
}
