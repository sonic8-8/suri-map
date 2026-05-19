package com.surimap.feature.incidents

import com.surimap.core.incident.IncidentReadRepository
import com.surimap.core.network.SuriMapApiClient
import com.surimap.feature.incidents.data.IncidentListStateLoader
import com.surimap.feature.incidents.ui.IncidentListStatus
import com.surimap.testing.incidentIdFixture
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.reflect.KClass

@RunWith(RobolectricTestRunner::class)
class IncidentListStateLoaderTest {

    @Test
    fun emptyIncidentListIsAValidStateWithoutImportAction() = runBlocking {
        val loader = loaderFor(response(200, """{"items":[]}"""))

        val state = loader.load()

        assertEquals(IncidentListStatus.Empty, state.status)
        assertTrue(state.incidents.isEmpty())
        assertTrue(state.canRefresh)
        assertFalse(state.shouldClearIncidentContext)
        assertFalse(state.visibleText().any { it.contains("사건 가져오기") })
    }

    @Test
    fun incidentSelectionVisibleTextDoesNotExposePolicePhoneId() = runBlocking {
        val loader =
            IncidentListStateLoader(
                repository =
                IncidentReadRepository(
                    apiClient =
                    SuriMapApiClient(
                        baseUrl = "https://suri-map.internal",
                        callFactory = CapturingCallFactory(response(200, """{"items":[]}"""))
                    )
                ),
                policePhoneLabel = "00000000-0000-0000-0000-000000000101"
            )

        val state = loader.load()

        assertFalse(state.visibleText().any { it.contains("00000000-0000-0000-0000-000000000101") })
    }

    @Test
    fun openIncidentsMapToCardsAndIncidentOnlyContext() = runBlocking {
        val loader =
            loaderFor(
                response(
                    200,
                    """
                    {
                      "items": [
                        {"id":"$INCIDENT_ID","title":"광주 북구 산악 실종","status":"OPEN","version":7}
                      ]
                    }
                    """.trimIndent()
                )
            )

        val state = loader.load()
        val incident = state.incidents.single()
        val context = incident.toIncidentContext()

        assertEquals(IncidentListStatus.Ready, state.status)
        assertEquals(INCIDENT_ID, incident.incidentId)
        assertEquals("광주 북구 산악 실종", incident.title)
        assertEquals("현장 수색 진행 중", incident.summary)
        assertEquals("현장 기록 열기", state.primaryOpenLabel)
        assertTrue(state.visibleText().contains("현장 기록 열기"))
        assertEquals(INCIDENT_ID, context.incidentId)
        assertNull(context.currentOpId)
        assertNull(context.currentDutyShiftId)
    }

    @Test
    fun incidentCardsHideTechnicalIdsAndPreferMissingPersonSummary() = runBlocking {
        val loader =
            loaderFor(
                response(
                    200,
                    """
                    {
                      "items": [
                        {
                          "id":"$INCIDENT_ID",
                          "title":"무등산 증심사 계곡 실종자 수색 00000000-0000-0000-0000-000000000101",
                          "status":"OPEN",
                          "version":7,
                          "missingPerson":{"displayName":"실종자 T2-무등-01","appearanceText":"남색 등산복 · 회색 모자"}
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )

        val incident = loader.load().incidents.single()

        assertEquals("무등산 증심사 계곡 실종자 수색", incident.title)
        assertEquals("실종자 · 남색 등산복 · 회색 모자", incident.summary)
        assertFalse(incident.title.contains("00000000"))
        assertFalse(incident.summary.contains("OPEN"))
        assertFalse(incident.summary.contains("v7"))
    }

    @Test
    fun notAssignedClearsIncidentContextAndShowsEmpty() = runBlocking {
        val loader = loaderFor(response(403, """{"error":"police_phone_not_assigned"}"""))

        val state = loader.load()

        assertEquals(IncidentListStatus.Empty, state.status)
        assertTrue(state.shouldClearIncidentContext)
        assertTrue(state.incidents.isEmpty())
    }

    @Test
    fun networkFailureUsesOfflineStateAndKeepsRefreshDisabled() = runBlocking {
        val loader =
            IncidentListStateLoader(
                repository =
                IncidentReadRepository(
                    apiClient =
                    SuriMapApiClient(
                        baseUrl = "https://suri-map.internal",
                        callFactory = FailingCallFactory()
                    )
                ),
                policePhoneLabel = "지구대 순찰차 폴리폰"
            )

        val state = loader.load()

        assertEquals(IncidentListStatus.Offline, state.status)
        assertFalse(state.canRefresh)
    }

    @Test
    fun appIncidentRouteStartsDutyShiftWhenNoActiveShift() {
        val source = java.io.File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertTrue(source.contains("DutyShiftLocalRecorder"))
        assertTrue(source.contains("resolvedContext.currentDutyShiftId.isNullOrBlank()"))
        assertTrue(source.contains("dutyShiftRecorder.start"))
        assertTrue(source.contains("resolvedContext.toDutyShiftWriteContext(policePhoneContext)"))
        assertTrue(source.contains("route = PolicePhoneRoute.SearchMap"))
        assertTrue(source.contains("onOpenOfflinePackage"))
    }

    private fun loaderFor(response: Response): IncidentListStateLoader {
        return IncidentListStateLoader(
            repository =
            IncidentReadRepository(
                apiClient =
                SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = CapturingCallFactory(response)
                )
            ),
            policePhoneLabel = "지구대 순찰차 폴리폰"
        )
    }

    private class CapturingCallFactory(
        private val response: Response
    ) : Call.Factory {
        override fun newCall(request: Request): Call = CapturingCall(request, response)
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

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
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
