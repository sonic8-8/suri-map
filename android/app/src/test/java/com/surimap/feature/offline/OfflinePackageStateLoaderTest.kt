package com.surimap.feature.offline

import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.offline.OfflinePackageInstallationStatus
import com.surimap.core.offline.OfflinePackageRepository
import com.surimap.feature.offline.data.OfflinePackageStateLoader
import com.surimap.feature.offline.ui.OfflinePackageDownloadStatus
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
                      "manifestId": "pkg-precinct-first-rev-18",
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
        assertEquals("완료", state.packageItems.single { it.label == "사건 메타" }.statusLabel)
        assertEquals("대기", state.packageItems.single { it.label == "실종자" }.statusLabel)
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
                      "manifestId": "pkg-precinct-first-rev-18",
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
                        manifestId = "pkg-precinct-first-rev-18",
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
        assertTrue(state.autoOpenSearchMap)
        assertFalse(state.shouldDownloadPackage)
    }

    @Test
    fun appOfflineRouteDoesNotRenderSamplePackageStateDirectly() {
        val source = java.io.File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertFalse(source.contains("state = sampleOfflinePackageState()"))
        assertFalse(source.contains("import com.surimap.feature.offline.ui.sampleOfflinePackageState"))
        assertTrue(source.contains("OfflinePackageStateLoader"))
        assertTrue(source.contains("offlinePackageInstallationDao"))
        assertTrue(source.contains("localInstallationStatus"))
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
        const val INCIDENT_ID = "inc-precinct-first-001"
        const val POLICE_PHONE_ID = "phone-precinct-001"
    }
}
