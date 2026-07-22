package com.surimap.core.sync

import android.util.Log
import androidx.work.Data
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.surimap.core.network.AccessTokenProvider
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.policePhoneIdFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxWorkerTest {
    @After
    fun tearDown() {
        LocalSyncRuntime.outboxReplay = null
        LocalSyncRuntime.outboxReplayProvider = null
        LocalSyncRuntime.outboxReplayScheduler = null
        LocalSyncRuntime.accessTokenProvider = null
    }

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
    fun outboxWorkerFailsWhenRequiredInputIsMissing() = runBlocking {
        val worker = TestListenableWorkerBuilder<OutboxWorker>(
            RuntimeEnvironment.getApplication()
        ).build()

        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    @Test
    fun outboxWorkerBuildsReplayRuntimeFromInputData() = runBlocking {
        val calls = mutableListOf<ReplayCall>()
        val providers = mutableListOf<ProviderCall>()
        LocalSyncRuntime.outboxReplayProvider =
            OutboxReplayProvider { _, apiBaseUrl, accessToken ->
                providers += ProviderCall(apiBaseUrl = apiBaseUrl, accessToken = accessToken)
                object : OutboxReplay {
                    override suspend fun flushPending(
                        policePhoneId: String,
                        incidentId: String
                    ): OutboxReplayResult {
                        calls += ReplayCall(
                            incidentId = incidentId,
                            policePhoneId = policePhoneId
                        )
                        return OutboxReplayResult()
                    }
                }
            }
        LocalSyncRuntime.accessTokenProvider = AccessTokenProvider { "bootstrap-token-1" }
        val worker = TestListenableWorkerBuilder<OutboxWorker>(
            RuntimeEnvironment.getApplication()
        )
            .setInputData(
                Data.Builder()
                    .putString(OutboxWorker.KEY_INCIDENT_ID, INCIDENT_ID)
                    .putString(OutboxWorker.KEY_POLICE_PHONE_ID, POLICE_PHONE_ID)
                    .putString(OutboxWorker.KEY_API_BASE_URL, "https://suri-map.internal")
                    .build()
            )
            .build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(
            ProviderCall(
                apiBaseUrl = "https://suri-map.internal",
                accessToken = "bootstrap-token-1"
            ),
            providers.single()
        )
        assertEquals(
            ReplayCall(
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID
            ),
            calls.single()
        )
    }

    @Test
    fun outboxWorkerWaitsForAuthenticationBeforeCreatingReplay() = runBlocking {
        var replayCreated = false
        LocalSyncRuntime.accessTokenProvider = AccessTokenProvider { null }
        LocalSyncRuntime.outboxReplayProvider =
            OutboxReplayProvider { _, _, _ ->
                replayCreated = true
                object : OutboxReplay {
                    override suspend fun flushPending(
                        policePhoneId: String,
                        incidentId: String
                    ): OutboxReplayResult = OutboxReplayResult()
                }
            }
        val worker = TestListenableWorkerBuilder<OutboxWorker>(
            RuntimeEnvironment.getApplication()
        )
            .setInputData(
                Data.Builder()
                    .putString(OutboxWorker.KEY_INCIDENT_ID, INCIDENT_ID)
                    .putString(OutboxWorker.KEY_POLICE_PHONE_ID, POLICE_PHONE_ID)
                    .build()
            )
            .build()

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        assertEquals(false, replayCreated)
    }

    @Test
    fun outboxWorkerRetriesWhenReplayLeavesRetryableFailures() = runBlocking {
        LocalSyncRuntime.outboxReplay =
            object : OutboxReplay {
                override suspend fun flushPending(
                    policePhoneId: String,
                    incidentId: String
                ): OutboxReplayResult =
                    OutboxReplayResult(attemptedCount = 1, retryableFailureCount = 1)
            }
        val worker = TestListenableWorkerBuilder<OutboxWorker>(
            RuntimeEnvironment.getApplication()
        )
            .setInputData(
                Data.Builder()
                    .putString(OutboxWorker.KEY_INCIDENT_ID, INCIDENT_ID)
                    .putString(OutboxWorker.KEY_POLICE_PHONE_ID, POLICE_PHONE_ID)
                    .putString(OutboxWorker.KEY_API_BASE_URL, "https://suri-map.internal")
                    .build()
            )
            .build()

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }

    @Test
    fun outboxWorkerDoesNotAutoRetryWhenReplayRequiresAccessRepair() = runBlocking {
        LocalSyncRuntime.outboxReplay =
            object : OutboxReplay {
                override suspend fun flushPending(
                    policePhoneId: String,
                    incidentId: String
                ): OutboxReplayResult =
                    OutboxReplayResult(attemptedCount = 1, accessRepairRequiredCount = 1)
            }
        val worker = TestListenableWorkerBuilder<OutboxWorker>(
            RuntimeEnvironment.getApplication()
        )
            .setInputData(
                Data.Builder()
                    .putString(OutboxWorker.KEY_INCIDENT_ID, INCIDENT_ID)
                    .putString(OutboxWorker.KEY_POLICE_PHONE_ID, POLICE_PHONE_ID)
                    .putString(OutboxWorker.KEY_API_BASE_URL, "https://suri-map.internal")
                    .build()
            )
            .build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    private data class ProviderCall(
        val apiBaseUrl: String,
        val accessToken: String?
    )

    private data class ReplayCall(
        val incidentId: String,
        val policePhoneId: String
    )

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-car-01")
    }
}
