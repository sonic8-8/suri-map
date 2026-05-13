package com.surimap.feature.bootstrap

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.feature.bootstrap.data.AuthBootstrapCoordinator
import com.surimap.feature.bootstrap.data.AuthBootstrapServerCheck
import com.surimap.feature.bootstrap.data.AndroidManagedConfigurationReader
import com.surimap.feature.bootstrap.data.AuthBootstrapCredentials
import com.surimap.feature.bootstrap.data.ManagedPolicePhoneConfig
import com.surimap.feature.bootstrap.data.NetworkPolicePhoneBootstrapServerCheck
import com.surimap.feature.bootstrap.ui.AuthBootstrapFailureReason
import com.surimap.feature.bootstrap.ui.AuthBootstrapOutcome
import com.surimap.feature.bootstrap.ui.AuthBootstrapUiState
import com.surimap.testing.policePhoneIdFixture
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.xml.parsers.DocumentBuilderFactory
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class AuthBootstrapContractTest {

    @Test
    fun managedConfigurationSchemaIsDeclaredForStandardMdmInjection() {
        val schemaFile = File("src/main/res/xml/app_restrictions.xml")
        val document = schemaFile.requireXml()
        val restrictions = document.getElementsByTagName("restriction")
        val keys = (0 until restrictions.length)
            .map { index -> restrictions.item(index).attributes.getNamedItem("android:key").nodeValue }
            .toSet()

        assertEquals(
            setOf(
                AndroidManagedConfigurationReader.KEY_POLICE_PHONE_ID,
                AndroidManagedConfigurationReader.KEY_API_BASE_URL,
                AndroidManagedConfigurationReader.KEY_TILE_BASE_URL,
                AndroidManagedConfigurationReader.KEY_OBJECT_STORAGE_BASE_URL,
                AndroidManagedConfigurationReader.KEY_ALLOWED_HOSTS
            ),
            keys
        )
        assertEquals(
            listOf("string", "string", "string", "string", "string"),
            (0 until restrictions.length).map { index ->
                restrictions.item(index).attributes.getNamedItem("android:restrictionType").nodeValue
            }
        )
    }

    @Test
    fun manifestLinksManagedConfigurationSchemaWithoutKnoxSpecificMetadata() {
        val document = File("src/main/AndroidManifest.xml").requireXml()
        val metadata = document.getElementsByTagName("meta-data")
        val managedConfigNodes = (0 until metadata.length)
            .map { index -> metadata.item(index).attributes }
            .filter { attributes ->
                attributes.getNamedItem("android:name")?.nodeValue == "android.content.APP_RESTRICTIONS"
            }

        assertEquals(1, managedConfigNodes.size)
        assertEquals("@xml/app_restrictions", managedConfigNodes.single().getNamedItem("android:resource").nodeValue)
        assertFalse((0 until metadata.length).any { index ->
            metadata.item(index).attributes
                .getNamedItem("android:name")
                ?.nodeValue
                ?.contains("knox", ignoreCase = true) == true
        })
    }

    @Test
    fun managedPolicePhoneConfigCarriesFutureTileAndStorageSettingsWithoutVendorCoupling() {
        val config =
            ManagedPolicePhoneConfig(
                policePhoneId = POLICE_PHONE_ID,
                apiBaseUrl = "https://suri-map.internal/api",
                tileBaseUrl = "https://suri-map.internal/tiles",
                objectStorageBaseUrl = "https://suri-map.internal/objects",
                allowedHosts = setOf("suri-map.internal", "objects.suri-map.internal")
            )

        assertEquals("https://suri-map.internal/tiles", config.tileBaseUrl)
        assertEquals("https://suri-map.internal/objects", config.objectStorageBaseUrl)
        assertEquals(setOf("suri-map.internal", "objects.suri-map.internal"), config.allowedHosts)
    }

    @Test
    fun failureVariantsBlockIncidentNavigationAndUseFieldFacingCopy() {
        val expectedMessages =
            mapOf(
                AuthBootstrapFailureReason.NotManagedPhone to "관리 단말이 아닙니다. IT 부서 문의",
                AuthBootstrapFailureReason.InternalNetworkUnavailable to "내부망 연결을 확인하세요",
                AuthBootstrapFailureReason.ServerRejectedPhone to "이 폴리폰으로 접속할 수 없습니다. IT 부서 문의",
                AuthBootstrapFailureReason.ManagedConfigMissing to "관리 설정이 없습니다. IT 부서 문의"
            )

        expectedMessages.forEach { (reason, message) ->
            val state = AuthBootstrapUiState.fromOutcome(
                outcome = AuthBootstrapOutcome.Blocked(reason),
                apiBaseUrl = "https://suri-map.internal"
            )

            assertFalse(state.shouldEnterIncidentList)
            assertEquals(message, state.failureMessage)
            assertFalse(state.visibleText().any { it.contains("로그인") })
            assertFalse(state.visibleText().any { it.contains("비밀번호") })
            assertFalse(state.visibleText().any { it.contains("바인딩") })
            assertFalse(state.visibleText().any { it.contains("Knox") })
            assertFalse(state.visibleText().any { it.contains("MDM") })
        }
    }

    @Test
    fun readyOutcomeAllowsAutomaticIncidentListNavigationWithoutIncidentDetails() {
        val state = AuthBootstrapUiState.fromOutcome(
            outcome = AuthBootstrapOutcome.Ready(
                policePhoneId = POLICE_PHONE_ID
            ),
            apiBaseUrl = "https://suri-map.internal"
        )

        assertTrue(state.shouldEnterIncidentList)
        assertFalse(state.visibleText().any { it.contains("실종") })
        assertFalse(state.visibleText().any { it.contains("OP") })
        assertFalse(state.visibleText().any { it.contains("배정 사건") })
    }

    @Test
    fun coordinatorStopsBeforeServerCheckWhenManagedConfigIsMissing() = runBlocking {
        var serverCalled = false
        val coordinator =
            AuthBootstrapCoordinator(
                managedConfigurationReader = {
                    ManagedPolicePhoneConfig(
                        policePhoneId = null,
                        apiBaseUrl = "https://suri-map.internal"
                    )
                },
                serverCheck =
                AuthBootstrapServerCheck {
                    serverCalled = true
                    AuthBootstrapOutcome.Ready(policePhoneId = it.policePhoneId.orEmpty())
                }
            )

        val outcome = coordinator.check()

        assertEquals(AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ManagedConfigMissing), outcome)
        assertFalse(serverCalled)
    }

    @Test
    fun coordinatorStopsBeforeServerCheckWhenPhoneIsNotManaged() = runBlocking {
        var serverCalled = false
        val coordinator =
            AuthBootstrapCoordinator(
                managedConfigurationReader = {
                    ManagedPolicePhoneConfig(
                        policePhoneId = POLICE_PHONE_ID,
                        apiBaseUrl = "https://suri-map.internal",
                        isManagedPhone = false
                    )
                },
                serverCheck =
                AuthBootstrapServerCheck {
                    serverCalled = true
                    AuthBootstrapOutcome.Ready(policePhoneId = it.policePhoneId.orEmpty())
                }
            )

        val outcome = coordinator.check()

        assertEquals(AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.NotManagedPhone), outcome)
        assertFalse(serverCalled)
    }

    @Test
    fun coordinatorMapsServerFailuresToBootstrapOutcomes() = runBlocking {
        val coordinator =
            AuthBootstrapCoordinator(
                managedConfigurationReader = {
                    ManagedPolicePhoneConfig(
                        policePhoneId = POLICE_PHONE_ID,
                        apiBaseUrl = "https://suri-map.internal"
                    )
                },
                serverCheck =
                AuthBootstrapServerCheck {
                    AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ServerRejectedPhone)
                }
            )

        assertEquals(
            AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ServerRejectedPhone),
            coordinator.check()
        )
    }

    @Test
    fun networkServerCheckUsesHeartbeatWithPolicePhoneHeader() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(200, """{"status":"ACTIVE"}"""))
        val serverCheck =
            NetworkPolicePhoneBootstrapServerCheck(
                apiClient =
                SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = callFactory
                ),
                accessTokenProvider = AccessTokenProvider { "token-1" },
                clock = Clock.fixed(Instant.parse("2026-05-11T07:00:00Z"), ZoneOffset.UTC)
            )

        val outcome =
            serverCheck.verify(
                ManagedPolicePhoneConfig(
                    policePhoneId = POLICE_PHONE_ID,
                    apiBaseUrl = "https://suri-map.internal"
                )
            )

        val request = callFactory.lastRequest!!
        assertEquals(
            AuthBootstrapOutcome.Ready(
                policePhoneId = POLICE_PHONE_ID,
                accessToken = "token-1"
            ),
            outcome
        )
        assertEquals("POST", request.method)
        assertEquals(
            "https://suri-map.internal/api/police-phones/$POLICE_PHONE_ID/heartbeat",
            request.url.toString()
        )
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertEquals(POLICE_PHONE_ID, request.header("X-PolicePhone-Id"))
        assertNull(request.header("X-Device-Id"))
        assertEquals("""{"clientTs":"2026-05-11T07:00:00Z","sequence":1}""", readRequestBody(request))
    }

    @Test
    fun networkServerCheckLogsInWithFixtureCredentialsBeforeHeartbeat() = runBlocking {
        val callFactory =
            CapturingCallFactory(
                responses =
                listOf(
                    response(
                        200,
                        """
                        {
                          "sessionId": "session-1",
                          "accessToken": "bootstrap-token-1",
                          "securityContext": {
                            "policePhoneId": "$POLICE_PHONE_ID"
                          }
                        }
                        """.trimIndent()
                    ),
                    response(200, """{"status":"ACTIVE"}""")
                )
            )
        val serverCheck =
            NetworkPolicePhoneBootstrapServerCheck(
                apiClient =
                SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = callFactory
                ),
                credentialsProvider = {
                    AuthBootstrapCredentials(
                        accountCode = "acct-precinct-team",
                        password = "fixture",
                        policePhoneCode = "dev-precinct-phone-01"
                    )
                },
                clock = Clock.fixed(Instant.parse("2026-05-11T07:00:00Z"), ZoneOffset.UTC)
            )

        val outcome =
            serverCheck.verify(
                ManagedPolicePhoneConfig(
                    policePhoneId = POLICE_PHONE_ID,
                    apiBaseUrl = "https://suri-map.internal"
                )
            )

        val requests = callFactory.requests
        assertEquals(
            AuthBootstrapOutcome.Ready(
                policePhoneId = POLICE_PHONE_ID,
                accessToken = "bootstrap-token-1"
            ),
            outcome
        )
        assertEquals("POST", requests[0].method)
        assertEquals("https://suri-map.internal/api/auth/login", requests[0].url.toString())
        assertEquals("APP", requests[0].header("X-Client-Channel"))
        assertEquals(
            """{"accountCode":"acct-precinct-team","password":"fixture","channel":"APP","policePhoneCode":"dev-precinct-phone-01"}""",
            readRequestBody(requests[0])
        )
        assertEquals(
            "https://suri-map.internal/api/police-phones/$POLICE_PHONE_ID/heartbeat",
            requests[1].url.toString()
        )
        assertEquals("Bearer bootstrap-token-1", requests[1].header("Authorization"))
        assertEquals(POLICE_PHONE_ID, requests[1].header("X-PolicePhone-Id"))
    }

    @Test
    fun networkServerCheckMapsGuardErrorAndIoFailure() = runBlocking {
        val rejectedCheck =
            NetworkPolicePhoneBootstrapServerCheck(
                apiClient =
                SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = CapturingCallFactory(
                        response = response(403, """{"error":"police_phone_not_assigned"}""")
                    )
                )
            )
        val offlineCheck =
            NetworkPolicePhoneBootstrapServerCheck(
                apiClient =
                SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = FailingCallFactory()
                )
            )
        val config =
            ManagedPolicePhoneConfig(
                policePhoneId = POLICE_PHONE_ID,
                apiBaseUrl = "https://suri-map.internal"
            )

        assertEquals(
            AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ServerRejectedPhone),
            rejectedCheck.verify(config)
        )
        assertEquals(
            AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.InternalNetworkUnavailable),
            offlineCheck.verify(config)
        )
    }

    private companion object {
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-car-01")
    }

    private class CapturingCallFactory(
        private val responses: List<Response>
    ) : Call.Factory {
        constructor(response: Response) : this(listOf(response))

        var lastRequest: Request? = null
        val requests = mutableListOf<Request>()
        private var index = 0

        override fun newCall(request: Request): Call {
            lastRequest = request
            requests += request
            val response = responses[minOf(index, responses.lastIndex)]
            index += 1
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

private fun readRequestBody(request: Request): String {
    val buffer = okio.Buffer()
    request.body!!.writeTo(buffer)
    return buffer.readUtf8()
}

private fun File.requireXml() =
    DocumentBuilderFactory
        .newInstance()
        .apply { isNamespaceAware = false }
        .newDocumentBuilder()
        .parse(this)
