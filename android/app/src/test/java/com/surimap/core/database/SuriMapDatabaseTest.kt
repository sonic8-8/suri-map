package com.surimap.core.database

import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuriMapDatabaseTest {
    private lateinit var database: SuriMapDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            SuriMapDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomSchemaIncludesBaselineTables() {
        val tableNames = mutableSetOf<String>()
        val cursor = database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
        )

        cursor.use {
            while (it.moveToNext()) {
                tableNames += it.getString(0)
            }
        }

        assertTrue(tableNames.contains("android_outbox_row"))
        assertTrue(tableNames.contains("local_write_draft"))
        assertTrue(tableNames.contains("sync_status"))
    }
}
