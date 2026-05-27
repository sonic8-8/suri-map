package com.surimap.feature.offline

import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.offline.OfflinePackageDownloadPlan
import com.surimap.core.offline.OfflinePackageInstallationStatus
import com.surimap.core.offline.OfflinePackageItemStatus
import com.surimap.core.offline.OfflinePackageRepository
import com.surimap.feature.offline.data.OfflinePackageStateLoader
import com.surimap.feature.offline.ui.OfflinePackageDownloadStatus
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.manifestIdFixture
import com.surimap.testing.policePhoneIdFixture
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.reflect.KClass

@RunWith(RobolectricTestRunner::class)
class OfflinePackageStateLoaderTest {

    @Test
    fun manifestResponseMapsToP3UiStateForCurrentIncidentAndPolicePhone() = runBlocking {
        val callFactory =
            CapturingCallFactory(
                response =
                response(
                    200,
                    """
                    {
                      "manifestId": "$MANIFEST_ID",
                      "incidentId": "$INCIDENT_ID",
                      "manifestVersion": 18,
                      "expiresAt": "2026-05-11T09:00:00Z",
                      "incident": {
                        "title": "광주 북구 산악 실종"
                      },
                      "packageItems": [
                        {
                          "itemKey": "incident-meta",
                          "itemType": "INCIDENT_META",
                          "status": "DOWNLOADED",
                          "sourceVersion": 7,
                          "sourceHash": "sha256:incident"
                        },
                        {
                          "itemKey": "missing-person",
                          "itemType": "MISSING_PERSON_CACHE",
                          "status": "PENDING",
                          "sourceVersion": 3,
                          "sourceHash": "sha256:missing"
                        },
                        {
                          "itemKey": "tile-1",
                          "itemType": "TILE",
                          "status": "FAILED",
                          "sourceVersion": 18,
                          "sourceHash": "sha256:tile"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
        val loader =
            OfflinePackageStateLoader(
                repository =
                OfflinePackageRepository(
                    apiClient =
                    SuriMapApiClient(
                        baseUrl = "https://suri-map.internal",
                        callFactory = callFactory
                    )
                ),
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID
            )

        val state = loader.load()
        val request = callFactory.lastRequest!!

        assertEquals(
            "https://suri-map.internal/api/incidents/$INCIDENT_ID/offline-package/manifest?policePhoneId=$POLICE_PHONE_ID",
            request.url.toString()
        )
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals(POLICE_PHONE_ID, request.header("X-PolicePhone-Id"))
        assertEquals("광주 북구 산악 실종", state.incidentTitle)
        assertEquals(18, state.manifestRevision)
        assertEquals(OfflinePackageDownloadStatus.Partial, state.status)
        assertFalse(state.readyForOfflineUse)
        assertFalse(state.autoOpenSearchMap)
        assertTrue(state.shouldDownloadPackage)
        assertEquals("완료", state.packageItems.single { it.label == "사건 정보" }.statusLabel)
        assertEquals("대기", state.packageItems.single { it.label == "실종자 정보" }.statusLabel)
        assertEquals("실패", state.packageItems.single { it.label == "타일" }.statusLabel)
    }

    @Test
    fun permissionErrorsMapToPermissionDeniedState() = runBlocking {
        val loader =
            loaderFor(
                response(
                    403,
                    """{"error":"police_phone_not_assigned"}"""
                )
            )

        val state = loader.load()

        assertEquals(OfflinePackageDownloadStatus.PermissionDenied, state.status)
        assertFalse(state.shouldDownloadPackage)
        assertTrue(state.visibleText().any { it.contains("배정") })
    }

    @Test
    fun packageManifestNotReadyMapsToSearchAreaPendingState() = runBlocking {
        val loader =
            loaderFor(
                response(
                    409,
                    """{"error":"package_manifest_not_ready"}"""
                )
            )

        val state = loader.load()

        assertEquals(OfflinePackageDownloadStatus.SearchAreaPending, state.status)
        assertFalse(state.shouldDownloadPackage)
        assertFalse(state.autoOpenSearchMap)
        assertFalse(state.canManualRetry)
        assertTrue(state.visibleText().any { it.contains("수색구역 지정 전") })
    }

    @Test
    fun networkFailureMapsToOfflineStateWithoutAutoOpeningMap() = runBlocking {
        val loader =
            OfflinePackageStateLoader(
                repository =
                OfflinePackageRepository(
                    apiClient =
                    SuriMapApiClient(
                        baseUrl = "https://suri-map.internal",
                        callFactory = FailingCallFactory()
                    )
                ),
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID
            )

        val state = loader.load()

        assertEquals(OfflinePackageDownloadStatus.Offline, state.status)
        assertFalse(state.readyForOfflineUse)
        assertFalse(state.autoOpenSearchMap)
    }

    @Test
    fun readyLocalInstallationStatusSendsKnownManifestRevisionAndOpensMap() = runBlocking {
        val callFactory =
            CapturingCallFactory(
                response =
                response(
                    200,
                    """
                    {
                      "manifestId": "$MANIFEST_ID",
                      "incidentId": "$INCIDENT_ID",
                      "manifestVersion": 18,
                      "incident": {"title": "광주 북구 산악 실종"},
                      "packageItems": []
                    }
                    """.trimIndent()
                )
            )
        val loader =
            OfflinePackageStateLoader(
                repository =
                OfflinePackageRepository(
                    apiClient =
                    SuriMapApiClient(
                        baseUrl = "https://suri-map.internal",
                        callFactory = callFactory
                    )
                ),
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID,
                localInstallationStatus = {
                    OfflinePackageInstallationStatus(
                        incidentId = INCIDENT_ID,
                        policePhoneId = POLICE_PHONE_ID,
                        manifestId = MANIFEST_ID,
                        manifestVersion = 18,
                        status = "READY",
                        totalItems = 7,
                        completedItems = 7,
                        failedItems = 0,
                        version = 3,
                        readyForOfflineUse = true
                    )
                }
            )

        val state = loader.load()
        val request = callFactory.lastRequest!!

        assertEquals(
            "https://suri-map.internal/api/incidents/$INCIDENT_ID/offline-package/manifest?policePhoneId=$POLICE_PHONE_ID&knownManifestRevision=18",
            request.url.toString()
        )
        assertEquals(OfflinePackageDownloadStatus.Ready, state.status)
        assertTrue(state.readyForOfflineUse)
        assertFalse(state.autoOpenSearchMap)
        assertFalse(state.shouldDownloadPackage)
    }

    @Test
    fun completedLocalPackageItemsOpenMapWhenInstallationAggregateIsNotYetVisible() = runBlocking {
        val callFactory =
            CapturingCallFactory(
                response =
                response(
                    200,
                    """
                    {
                      "manifestId": "$MANIFEST_ID",
                      "incidentId": "$INCIDENT_ID",
                      "manifestVersion": 18,
                      "incident": {"title": "광주 북구 산악 실종"},
                      "packageItems": [
                        {
                          "itemKey": "incident-meta",
                          "itemType": "INCIDENT_META",
                          "status": "PENDING",
                          "sourceVersion": 7,
                          "sourceHash": "sha256:incident"
                        },
                        {
                          "itemKey": "tile-1",
                          "itemType": "TILE",
                          "status": "PENDING",
                          "sourceVersion": 18,
                          "sourceHash": "sha256:tile"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
        val loader =
            OfflinePackageStateLoader(
                repository =
                OfflinePackageRepository(
                    apiClient =
                    SuriMapApiClient(
                        baseUrl = "https://suri-map.internal",
                        callFactory = callFactory
                    )
                ),
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID,
                localPackageItems = { manifestId ->
                    assertEquals(MANIFEST_ID, manifestId)
                    listOf(
                        OfflinePackageItemStatus(
                            incidentId = INCIDENT_ID,
                            policePhoneId = POLICE_PHONE_ID,
                            manifestId = manifestId,
                            manifestVersion = 18,
                            itemKey = "incident-meta",
                            itemType = "INCIDENT_META",
                            status = "SKIPPED",
                            sourceVersion = 7,
                            sourceHash = "sha256:incident",
                            bytesTotal = null,
                            bytesDownloaded = null
                        ),
                        OfflinePackageItemStatus(
                            incidentId = INCIDENT_ID,
                            policePhoneId = POLICE_PHONE_ID,
                            manifestId = manifestId,
                            manifestVersion = 18,
                            itemKey = "tile-1",
                            itemType = "TILE",
                            status = "DOWNLOADED",
                            sourceVersion = 18,
                            sourceHash = "sha256:tile",
                            bytesTotal = 100,
                            bytesDownloaded = 100
                        )
                    )
                }
            )

        val state = loader.load()

        assertEquals(OfflinePackageDownloadStatus.Ready, state.status)
        assertTrue(state.readyForOfflineUse)
        assertFalse(state.autoOpenSearchMap)
        assertFalse(state.shouldDownloadPackage)
    }

    @Test
    fun localPackageItemProgressOverridesManifestItemProgress() = runBlocking {
        val callFactory =
            CapturingCallFactory(
                response =
                response(
                    200,
                    """
                    {
                      "manifestId": "$UPDATED_MANIFEST_ID",
                      "incidentId": "$INCIDENT_ID",
                      "manifestVersion": 19,
                      "incident": {"title": "광주 북구 산악 실종"},
                      "packageItems": [
                        {
                          "itemKey": "incident-meta",
                          "itemType": "INCIDENT_META",
                          "status": "PENDING",
                          "sourceVersion": 8,
                          "sourceHash": "sha256:incident"
                        },
                        {
                          "itemKey": "tile-1",
                          "itemType": "TILE",
                          "status": "DOWNLOADED",
                          "sourceVersion": 19,
                          "sourceHash": "sha256:tile"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
        val loader =
            OfflinePackageStateLoader(
                repository =
                OfflinePackageRepository(
                    apiClient =
                    SuriMapApiClient(
                        baseUrl = "https://suri-map.internal",
                        callFactory = callFactory
                    )
                ),
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID,
                localPackageItems = { manifestId ->
                    assertEquals(UPDATED_MANIFEST_ID, manifestId)
                    listOf(
                        OfflinePackageItemStatus(
                            incidentId = INCIDENT_ID,
                            policePhoneId = POLICE_PHONE_ID,
                            manifestId = manifestId,
                            manifestVersion = 19,
                            itemKey = "incident-meta",
                            itemType = "INCIDENT_META",
                            status = "DOWNLOADED",
                            sourceVersion = 8,
                            sourceHash = "sha256:incident",
                            bytesTotal = null,
                            bytesDownloaded = null
                        ),
                        OfflinePackageItemStatus(
                            incidentId = INCIDENT_ID,
                            policePhoneId = POLICE_PHONE_ID,
                            manifestId = manifestId,
                            manifestVersion = 19,
                            itemKey = "tile-1",
                            itemType = "TILE",
                            status = "PENDING",
                            sourceVersion = 19,
                            sourceHash = "sha256:tile",
                            bytesTotal = 100,
                            bytesDownloaded = 40
                        ),
                        OfflinePackageItemStatus(
                            incidentId = INCIDENT_ID,
                            policePhoneId = POLICE_PHONE_ID,
                            manifestId = manifestId,
                            manifestVersion = 19,
                            itemKey = "marker-1",
                            itemType = "INITIAL_MARKER",
                            status = "FAILED",
                            sourceVersion = 19,
                            sourceHash = "sha256:marker",
                            bytesTotal = null,
                            bytesDownloaded = null
                        )
                    )
                }
            )

        val state = loader.load()

        assertEquals(OfflinePackageDownloadStatus.Partial, state.status)
        assertEquals("완료", state.packageItems.single { it.label == "사건 정보" }.statusLabel)
        assertEquals(1f, state.packageItems.single { it.label == "사건 정보" }.progress)
        assertEquals("다운로드 중", state.packageItems.single { it.label == "타일" }.statusLabel)
        assertEquals(0.4f, state.packageItems.single { it.label == "타일" }.progress)
        assertEquals("실패", state.packageItems.single { it.label == "마커" }.statusLabel)
        assertTrue(state.packageItems.single { it.label == "마커" }.failed)
    }

    @Test
    fun manifestLoadPublishesDownloadPlanForDaoSeedAndScheduling() = runBlocking {
        val capturedPlans = mutableListOf<OfflinePackageDownloadPlan>()
        val loader =
            OfflinePackageStateLoader(
                repository =
                OfflinePackageRepository(
                    apiClient =
                    SuriMapApiClient(
                        baseUrl = "https://suri-map.internal",
                        callFactory =
                        CapturingCallFactory(
                            response(
                                200,
                                """
                                {
                                  "manifestId": "$UPDATED_MANIFEST_ID",
                                  "incidentId": "$INCIDENT_ID",
                                  "manifestVersion": 19,
                                  "incident": {"title": "광주 북구 산악 실종"},
                                  "packageItems": [
                                    {
                                      "itemKey": "tile-1",
                                      "itemType": "TILE",
                                      "status": "PENDING",
                                      "sourceVersion": 19,
                                      "sourceHash": "sha256:tile",
                                      "tile": {
                                        "url": "/tiles/osm-local/15/1/1.pbf",
                                        "checksum": "sha256:tile",
                                        "bytes": 100
                                      }
                                    }
                                  ]
                                }
                                """.trimIndent()
                            )
                        )
                    )
                ),
                incidentId = INCIDENT_ID,
                policePhoneId = POLICE_PHONE_ID,
                onDownloadPlanAvailable = { plan -> capturedPlans += plan }
            )

        val state = loader.load()

        assertTrue(state.shouldDownloadPackage)
        assertEquals(UPDATED_MANIFEST_ID, capturedPlans.single().manifestId)
        assertEquals("/tiles/osm-local/15/1/1.pbf", capturedPlans.single().items.single().downloadUrl)
    }

    @Test
    fun appOfflineRouteDoesNotRenderSamplePackageStateDirectly() {
        val source = java.io.File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertFalse(source.contains("state = sampleOfflinePackageState()"))
        assertFalse(source.contains("import com.surimap.feature.offline.ui.sampleOfflinePackageState"))
        assertTrue(source.contains("OfflinePackageStateLoader"))
        assertTrue(source.contains("offlinePackageInstallationDao"))
        assertTrue(source.contains("offlinePackageItemStatusDao"))
        assertTrue(source.contains("localInstallationStatus"))
        assertTrue(source.contains("localPackageItems"))
        assertTrue(source.contains("onDownloadPlanAvailable"))
        assertTrue(source.contains("OfflinePackageDownloadScheduler"))
        assertTrue(source.contains("clockSyncState.syncClockForIncident"))
        assertTrue(source.contains("val clockSnapshot = clockSyncState.snapshot()"))
        assertTrue(source.contains("clockOffsetMs = clockSnapshot.clockOffsetMs"))
        assertTrue(source.contains("clockSyncedAt = clockSnapshot.clockSyncedAt?.toString()"))
        assertTrue(source.contains("WorkManager.getInstance"))
        assertTrue(source.contains("observe("))
        assertTrue(source.contains("collectAsState"))
        assertTrue(source.contains("installationRefreshSignal"))
        assertTrue(source.contains("upsertAll"))
    }

    private fun loaderFor(response: Response): OfflinePackageStateLoader {
        return OfflinePackageStateLoader(
            repository =
            OfflinePackageRepository(
                apiClient =
                SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = CapturingCallFactory(response)
                )
            ),
            incidentId = INCIDENT_ID,
            policePhoneId = POLICE_PHONE_ID
        )
    }

    private class CapturingCallFactory(
        private val response: Response
    ) : Call.Factory {
        var lastRequest: Request? = null

        override fun newCall(request: Request): Call {
            lastRequest = request
            return CapturingCall(request, response)
        }
    }

    private class FailingCallFactory : Call.Factory {
        override fun newCall(request: Request): Call = FailingCall(request)
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

    private class FailingCall(
        private val request: Request
    ) : Call {
        override fun request(): Request = request
        override fun execute(): Response = throw IOException("network unavailable")
        override fun enqueue(responseCallback: Callback) = error("async calls are not used")
        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun <T : Any> tag(type: KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun clone(): Call = FailingCall(request)
    }

    private fun response(statusCode: Int, body: String): Response {
        return Response.Builder()
            .request(Request.Builder().url("https://suri-map.internal/placeholder").build())
            .protocol(Protocol.HTTP_1_1)
            .code(statusCode)
            .message("test")
            .body(body.toResponseBody())
            .build()
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        val MANIFEST_ID = manifestIdFixture("precinct-first-rev-18")
        val UPDATED_MANIFEST_ID = manifestIdFixture("precinct-first-rev-19")
    }
}
