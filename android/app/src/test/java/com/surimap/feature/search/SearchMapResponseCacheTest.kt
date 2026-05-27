package com.surimap.feature.search

import androidx.room.Room
import com.surimap.core.database.SuriMapDatabase
import com.surimap.feature.search.data.RoomSearchMapResponseCache
import com.surimap.feature.search.data.SearchMapSessionContext
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.policePhoneIdFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SearchMapResponseCacheTest {
    private lateinit var database: SuriMapDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            SuriMapDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomSearchMapResponseCacheSkipsUnchangedBodyWritesAndUpdatesChangedBodies() = runBlocking {
        var now = 1_000L
        val cache =
            RoomSearchMapResponseCache(
                dao = database.searchMapResponseCacheDao(),
                nowMillis = { now }
            )

        cache.upsertIfChanged(CONTEXT, source = "search_paths", body = """{"paths":[]}""")
        now = 2_000L
        cache.upsertIfChanged(CONTEXT, source = "search_paths", body = """{"paths":[]}""")

        val unchanged =
            database.searchMapResponseCacheDao().find(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                policePhoneId = POLICE_PHONE_ID,
                source = "search_paths"
            )

        assertEquals(1_000L, unchanged!!.updatedAt)
        assertEquals("""{"paths":[]}""", cache.read(CONTEXT, "search_paths"))

        now = 3_000L
        cache.upsertIfChanged(CONTEXT, source = "search_paths", body = """{"paths":[{"id":"p1"}]}""")

        val changed =
            database.searchMapResponseCacheDao().find(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                policePhoneId = POLICE_PHONE_ID,
                source = "search_paths"
            )

        assertEquals(3_000L, changed!!.updatedAt)
        assertEquals("""{"paths":[{"id":"p1"}]}""", cache.read(CONTEXT, "search_paths"))
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-first-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val CONTEXT =
            SearchMapSessionContext(
                incidentId = INCIDENT_ID,
                currentOpId = OP_ID,
                currentDutyShiftId = null,
                policePhoneId = POLICE_PHONE_ID
            )
    }
}
