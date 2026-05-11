package com.surimap.feature.bootstrap

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.feature.bootstrap.data.AuthBootstrapCoordinator
import com.surimap.feature.bootstrap.data.AuthBootstrapServerCheck
import com.surimap.feature.bootstrap.data.ManagedPolicePhoneConfig
import com.surimap.feature.bootstrap.data.NetworkPolicePhoneBootstrapServerCheck
import com.surimap.feature.bootstrap.ui.AuthBootstrapFailureReason
import com.surimap.feature.bootstrap.ui.AuthBootstrapOutcome
import com.surimap.feature.bootstrap.ui.AuthBootstrapUiState
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
                policePhoneId = "police-phone-precinct-car-01"
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
                        policePhoneId = "police-phone-precinct-car-01",
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
                        policePhoneId = "police-phone-precinct-car-01",
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
                    policePhoneId = "police-phone-precinct-car-01",
                    apiBaseUrl = "https://suri-map.internal"
                )
            )

        val request = callFactory.lastRequest!!
        assertEquals(AuthBootstrapOutcome.Ready("police-phone-precinct-car-01"), outcome)
        assertEquals("POST", request.method)
        assertEquals(
            "https://suri-map.internal/api/police-phones/police-phone-precinct-car-01/heartbeat",
            request.url.toString()
        )
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertEquals("police-phone-precinct-car-01", request.header("X-PolicePhone-Id"))
        assertNull(request.header("X-Device-Id"))
        assertEquals("""{"clientTs":"2026-05-11T07:00:00Z","sequence":1}""", readRequestBody(request))
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
                policePhoneId = "police-phone-precinct-car-01",
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
