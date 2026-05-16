package com.surimap.core.map

import com.surimap.testing.policePhoneIdFixture
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.reflect.KClass

class MapLibreTileHttpTest {

    @Test
    fun mapLibreTileRequestFactoryAddsAppHeadersOnlyForTilesRoutes() {
        val delegate = CapturingCallFactory()
        val factory = MapLibreTileCallFactory(
            delegate = delegate,
            tileBaseUrl = "https://suri-map.example.com/api",
            accessTokenProvider = { "access-token-1" },
            policePhoneIdProvider = { POLICE_PHONE_ID }
        )

        factory.newCall(
            Request.Builder()
                .url("https://suri-map.example.com/tiles/styles/osm-local.json")
                .build()
        )

        val request = delegate.lastRequest!!
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer access-token-1", request.header("Authorization"))
        assertEquals(POLICE_PHONE_ID, request.header("X-PolicePhone-Id"))
        assertNull(request.header("Idempotency-Key"))
    }

    @Test
    fun mapLibreTileRequestFactoryAddsAppHeadersForGlyphRoutes() {
        val delegate = CapturingCallFactory()
        val factory = MapLibreTileCallFactory(
            delegate = delegate,
            tileBaseUrl = "https://suri-map.example.com/api",
            accessTokenProvider = { "access-token-1" },
            policePhoneIdProvider = { POLICE_PHONE_ID }
        )

        factory.newCall(
            Request.Builder()
                .url("https://suri-map.example.com/tiles/fonts/Pretendard%20GOV/0-255.pbf")
                .build()
        )

        val request = delegate.lastRequest!!
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer access-token-1", request.header("Authorization"))
        assertEquals(POLICE_PHONE_ID, request.header("X-PolicePhone-Id"))
    }

    @Test
    fun mapLibreTileRequestFactoryLeavesExternalResourcesUnchanged() {
        val delegate = CapturingCallFactory()
        val factory = MapLibreTileCallFactory(
            delegate = delegate,
            tileBaseUrl = "https://suri-map.example.com/api",
            accessTokenProvider = { "access-token-1" },
            policePhoneIdProvider = { POLICE_PHONE_ID }
        )

        factory.newCall(
            Request.Builder()
                .url("https://external-tiles.example.com/tiles/osm-local/15/27935/12960.pbf")
                .build()
        )

        val request = delegate.lastRequest!!
        assertNull(request.header("X-Client-Channel"))
        assertNull(request.header("Authorization"))
        assertNull(request.header("X-PolicePhone-Id"))
    }

    private class CapturingCallFactory : Call.Factory {
        var lastRequest: Request? = null

        override fun newCall(request: Request): Call {
            lastRequest = request
            return CapturingCall(request)
        }
    }

    private class CapturingCall(
        private val request: Request
    ) : Call {
        override fun request(): Request = request
        override fun execute(): Response {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("test")
                .body("{}".toResponseBody())
                .build()
        }

        override fun enqueue(responseCallback: Callback) = error("async calls are not used")
        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun <T : Any> tag(type: KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun clone(): Call = CapturingCall(request)
    }

    private companion object {
        val POLICE_PHONE_ID = policePhoneIdFixture("1")
    }
}
