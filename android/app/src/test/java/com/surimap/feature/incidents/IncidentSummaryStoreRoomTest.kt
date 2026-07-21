package com.surimap.feature.incidents

import androidx.room.Room
import com.surimap.core.database.SuriMapDatabase
import com.surimap.feature.incidents.data.RoomIncidentSummaryStore
import com.surimap.feature.incidents.ui.AssignedIncidentUiModel
import com.surimap.feature.incidents.ui.IncidentPackageStatus
import com.surimap.testing.incidentIdFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncidentSummaryStoreRoomTest {
    private lateinit var database: SuriMapDatabase

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                SuriMapDatabase::class.java
            ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replacingOneAccountsIncidentListKeepsOtherAccountsList() = runBlocking {
        val store = RoomIncidentSummaryStore(database.incidentSummaryDao(), nowMillis = { 1_000L })
        val first = incident(INCIDENT_A_ID, "첫 번째 사건")
        val second = incident(INCIDENT_B_ID, "두 번째 사건")
        val otherAccount = incident(INCIDENT_C_ID, "다른 계정 사건")

        store.replaceForAccount(ACCOUNT_A_ID, listOf(first, second))
        store.replaceForAccount(ACCOUNT_B_ID, listOf(otherAccount))
        store.replaceForAccount(ACCOUNT_A_ID, listOf(second))

        assertEquals(listOf(second), store.findByAccountId(ACCOUNT_A_ID))
        assertEquals(listOf(otherAccount), store.findByAccountId(ACCOUNT_B_ID))
    }

    private fun incident(incidentId: String, title: String): AssignedIncidentUiModel =
        AssignedIncidentUiModel(
            incidentId = incidentId,
            currentOpId = "88888888-8888-8888-8888-888888880001",
            currentOpLabel = "OP 1차",
            currentDutyShiftId = "99999999-9999-9999-9999-999999990001",
            title = title,
            summary = "현장 수색 진행 중",
            packageStatus = IncidentPackageStatus.Ready
        )

    private companion object {
        const val ACCOUNT_A_ID = "account-a"
        const val ACCOUNT_B_ID = "account-b"
        val INCIDENT_A_ID = incidentIdFixture("precinct-first-001")
        val INCIDENT_B_ID = incidentIdFixture("precinct-first-002")
        val INCIDENT_C_ID = incidentIdFixture("precinct-first-003")
    }
}
