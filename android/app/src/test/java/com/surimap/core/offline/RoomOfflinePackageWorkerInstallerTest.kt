package com.surimap.core.offline

import androidx.room.Room
import com.surimap.core.database.SuriMapDatabase
import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.sync.RoomSyncClient
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.reflect.KClass

@RunWith(RobolectricTestRunner::class)
class RoomOfflinePackageWorkerInstallerTest {
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
    fun installsManifestItemsIntoRoomAndReportsReadyInstallation() = runBlocking {
        val installer =
            roomInstaller(
                manifestBody =
                manifestBody(
                    tileSourceHash = "sha256:256aa026a53e12257562cb73c6a1e4ce769566957c1823773028e08cf6ddb355"
                ),
                tileBytes = "tile-bytes".encodeToByteArray()
            )

        installer.install(
            OfflinePackageWorkerInstallRequest(
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID,
                manifestId = MANIFEST_ID
            )
        )

        val items = database.offlinePackageItemStatusDao().findByManifest(
            incidentId = INCIDENT_ID,
            policePhoneId = POLICE_PHONE_ID,
            manifestId = MANIFEST_ID
        )
        val installation = database.offlinePackageInstallationDao().find(INCIDENT_ID, POLICE_PHONE_ID)
        val outboxRows = database.outboxDao().findByIncidentId(INCIDENT_ID)

        assertEquals(listOf("incident-meta", "tile-1"), items.map { it.itemKey })
        assertTrue(items.all { it.status == "DOWNLOADED" || it.status == "SKIPPED" })
        assertEquals("READY", installation!!.status)
        assertTrue(installation.readyForOfflineUse)
        assertEquals("PACKAGE_INSTALLATION", outboxRows.single().dependencyGroup)
        assertEquals("e54c8c5a-802f-352d-b366-ee430f0bbea5", outboxRows.single().operationId)
        assertEquals(
            "/api/incidents/$INCIDENT_ID/offline-package/installations",
            outboxRows.single().requestPath
        )
    }

    @Test
    fun checksumMismatchPersistsFailedTileAndPartialInstallation() = runBlocking {
        val installer =
            roomInstaller(
                manifestBody = manifestBody(tileSourceHash = "sha256:not-the-real-hash"),
                tileBytes = "tile-bytes".encodeToByteArray()
            )

        installer.install(
            OfflinePackageWorkerInstallRequest(
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID,
                manifestId = MANIFEST_ID
            )
        )

        val tile = database.offlinePackageItemStatusDao()
            .findByManifest(INCIDENT_ID, POLICE_PHONE_ID, MANIFEST_ID)
            .single { it.itemKey == "tile-1" }
        val installation = database.offlinePackageInstallationDao().find(INCIDENT_ID, POLICE_PHONE_ID)

        assertEquals("FAILED", tile.status)
        assertEquals("PARTIAL", installation!!.status)
        assertEquals(1, installation.failedItems)
        assertEquals(2, installation.totalItems)
    }

    @Test
    fun httpByteFetcherAddsBootstrapBearerTokenToTileRequest() = runBlocking {
        val callFactory = CapturingCallFactory(response(200, "tile-bytes"))
        val fetcher =
            OfflinePackageHttpByteFetcher(
                apiBaseUrl = "https://suri-map.internal/api",
                accessTokenProvider = AccessTokenProvider { "bootstrap-token-1" },
                callFactory = callFactory
            )

        fetcher.fetch(
            OfflinePackageDownloadItem(
                itemKey = "tile-1",
                itemType = "TILE",
                sourceVersion = 18,
                sourceHash = "sha256:irrelevant",
                downloadUrl = "/tiles/osm-local/15/27925/12680.pbf"
            )
        )

        assertEquals(
            "https://suri-map.internal/tiles/osm-local/15/27925/12680.pbf",
            callFactory.lastRequest!!.url.toString()
        )
        assertEquals("APP", callFactory.lastRequest!!.header("X-Client-Channel"))
        assertEquals("Bearer bootstrap-token-1", callFactory.lastRequest!!.header("Authorization"))
    }

    private fun roomInstaller(
        manifestBody: String,
        tileBytes: ByteArray
    ): RoomOfflinePackageWorkerInstaller {
        val repository =
            OfflinePackageRepository(
                syncClient = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                apiClient =
                SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = CapturingCallFactory(response(200, manifestBody))
                )
            )
        return RoomOfflinePackageWorkerInstaller(
            database = database,
            repository = repository,
            fetchBytes = { tileBytes },
            nowMillis = { 1_000L }
        )
    }

    private fun manifestBody(tileSourceHash: String): String =
        """
        {
          "manifestId": "$MANIFEST_ID",
          "incidentId": "$INCIDENT_ID",
          "manifestVersion": 18,
          "packageItems": [
            {
              "itemKey": "incident-meta",
              "itemType": "INCIDENT_META",
              "status": "PENDING",
              "sourceVersion": 7,
              "sourceHash": "sha256:incident",
              "payload": {"title": "광주 북구 산악 실종"}
            },
            {
              "itemKey": "tile-1",
              "itemType": "TILE",
              "status": "PENDING",
              "sourceVersion": 18,
              "sourceHash": "$tileSourceHash",
              "tile": {
                "url": "/tiles/osm-local/15/27925/12680.pbf",
                "checksum": "$tileSourceHash",
                "bytes": 10
              }
            }
          ]
        }
        """.trimIndent()

    private class CapturingCallFactory(private val response: Response) : Call.Factory {
        var lastRequest: Request? = null

        override fun newCall(request: Request): Call {
            lastRequest = request
            return CapturingCall(request, response)
        }
    }

    private class CapturingCall(
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
        override fun clone(): Call = CapturingCall(request, response)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://suri-map.internal/api").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body.toResponseBody())
            .build()

    private companion object {
        const val INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
        const val POLICE_PHONE_ID = "50000000-0000-0000-0000-000000000001"
        const val MANIFEST_ID = "77777777-0000-4000-8000-000000000701"
    }
}
