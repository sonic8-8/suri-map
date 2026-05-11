package com.surimap.core.sync

import androidx.room.Room
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.surimap.core.database.SuriMapDatabase
import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.HttpOutboxSender
import com.surimap.core.network.SuriMapApiClient
import com.surimap.testing.AndroidHarnessFixtureCatalog
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxHarnessIntegrationTest {
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
        LocalSyncRuntime.outboxReplay = null
        database.close()
    }

    @Test
    fun roomWorkManagerAndHttpMockReplaySc05PathFixtureToAcked() = runBlocking {
        val catalog = AndroidHarnessFixtureCatalog.load()
        assertEquals(
            "outboxReplay.sc05PathReplay.outboxId",
            catalog.requireFixture("outbox-path-001").ownerPath
        )

        val syncClient = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao())
        val callFactory = StaticCallFactory(response = response(201))
        val replay = RoomOutboxReplay(
            outboxDao = database.outboxDao(),
            sender =
            HttpOutboxSender(
                apiClient = SuriMapApiClient(
                    baseUrl = "https://suri-map.example.com",
                    callFactory = callFactory
                ),
                accessTokenProvider = AccessTokenProvider { "token-1" }
            )
        )
        val now = Instant.ofEpochMilli(System.currentTimeMillis())
        val operation =
            LocalWriteOperation(
                operationId = "op-outbox-path-001",
                incidentId = "inc-precinct-first-001",
                policePhoneId = "dev-precinct-car-01",
                dependencyGroup = DependencyGroup.PATH,
                sequence = 502L,
                method = "POST",
                endpoint = "/api/search-paths/batch",
                payload = """{"points":[{"lat":37.57,"lon":126.956}]}""",
                bodyHash = "sha256:path-normal-001",
                idempotencyKey = "idem-path-001",
                clientTs = now.minusSeconds(5),
                clockOffsetMs = 0L,
                clockSyncedAt = now,
                opId = "op-precinct-001-op1",
                entityType = "search_path"
            )

        val enqueue = syncClient.enqueue(operation)
        LocalSyncRuntime.outboxReplay = replay
        val worker = TestListenableWorkerBuilder<OutboxWorker>(
            RuntimeEnvironment.getApplication()
        )
            .setInputData(
                Data.Builder()
                    .putString("incidentId", operation.incidentId)
                    .putString("policePhoneId", operation.policePhoneId)
                    .build()
            )
            .build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        val row = database.outboxDao().findById(enqueue.outboxId)
        assertNotNull(row)
        assertEquals(OutboxStatus.ACKED.name, row!!.idempotencyStatus)
        assertEquals(HarnessSyncStatus.SYNCED.name, row.localMirrorStatus)

        val request = callFactory.lastRequest!!
        assertEquals("POST", request.method)
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertEquals("dev-precinct-car-01", request.header("X-PolicePhone-Id"))
        assertEquals("idem-path-001", request.header("Idempotency-Key"))
        assertEquals("https://suri-map.example.com/api/search-paths/batch", request.url.toString())
    }
}

private class StaticCallFactory(
    private val response: Response
) : Call.Factory {
    var lastRequest: Request? = null

    override fun newCall(request: Request): Call {
        lastRequest = request
        return StaticCall(request, response)
    }
}

private class StaticCall(
    private val request: Request,
    private val response: Response
) : Call {
    override fun request(): Request = request

    override fun execute(): Response = response.newBuilder().request(request).build()
    override fun enqueue(responseCallback: Callback) = error("async calls are not used")
    override fun cancel() = Unit
    override fun isExecuted(): Boolean = false
    override fun isCanceled(): Boolean = false
    override fun timeout(): Timeout = Timeout.NONE
    override fun <T : Any> tag(type: KClass<T>): T? = null
    override fun <T> tag(type: Class<out T>): T? = null
    override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    override fun clone(): Call = StaticCall(request, response)
}

private fun response(statusCode: Int): Response =
    Response.Builder()
        .request(Request.Builder().url("https://suri-map.example.com/placeholder").build())
        .protocol(Protocol.HTTP_1_1)
        .code(statusCode)
        .message("test")
        .body("""{"status":"ok"}""".toResponseBody())
        .build()
