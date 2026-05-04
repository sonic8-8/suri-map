package com.surimap.core.sync

import android.util.Log
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxWorkerTest {
    @Test
    fun deterministicWorkManagerModeCanBeInitialized() {
        val context = RuntimeEnvironment.getApplication()
        val configuration = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)

        assertNotNull(WorkManagerTestInitHelper.getTestDriver(context))
    }

    @Test
    fun outboxWorkerReturnsSuccessInHarnessBaseline() = runBlocking {
        val worker = TestListenableWorkerBuilder<OutboxWorker>(
            RuntimeEnvironment.getApplication(),
        ).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
