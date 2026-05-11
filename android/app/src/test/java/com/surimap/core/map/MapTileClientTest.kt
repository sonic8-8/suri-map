package com.surimap.core.map

import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.Timeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.reflect.KClass

class MapTileClientTest {

    @Test
    fun styleConfigStripsApiPrefixForCanonicalTilesRouteAndCarriesAppHeaders() {
        val config = MapLibreTileSourceFactory(
            apiBaseUrl = "https://suri-map.example.com/api"
        ).styleSource(
            styleId = "osm-local",
            accessToken = "access-token-1",
            policePhoneId = "phone-1"
        )

        assertEquals("osm-local", config.styleId)
        assertEquals(
            "https://suri-map.example.com/tiles/styles/osm-local.json",
            config.styleUrl
        )
        assertEquals("APP", config.requestHeaders["X-Client-Channel"])
        assertEquals("Bearer access-token-1", config.requestHeaders["Authorization"])
        assertEquals("phone-1", config.requestHeaders["X-PolicePhone-Id"])
    }

    @Test
    fun fetchesStyleJsonFromTilesRouteWithAppChannelHeaders() = runBlocking {
        val callFactory = CapturingCallFactory(
            response = response(
                statusCode = 200,
                body = """{"version":8,"sources":{},"layers":[],"metadata":{"attribution":"OpenStreetMap"}}"""
            )
        )
        val client = MapTileClient(
            apiBaseUrl = "https://suri-map.example.com/api",
            callFactory = callFactory
        )

        val result = client.fetchStyle(
            styleId = "osm-local",
            accessToken = "access-token-1",
            policePhoneId = "phone-1"
        )

        val request = callFactory.lastRequest!!
        assertEquals(200, result.statusCode)
        assertEquals(
            "https://suri-map.example.com/tiles/styles/osm-local.json",
            request.url.toString()
        )
        assertEquals("GET", request.method)
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer access-token-1", request.header("Authorization"))
        assertEquals("phone-1", request.header("X-PolicePhone-Id"))
        assertEquals("application/json", request.header("Accept"))
        assertEquals("OpenStreetMap", result.requiredAttribution)
    }

    @Test
    fun fetchesVectorTileBytesFromTilesRouteWithoutApiPrefix() = runBlocking {
        val tileBytes = byteArrayOf(0x0A, 0x02, 0x01)
        val callFactory = CapturingCallFactory(
            response = response(
                statusCode = 200,
                bodyBytes = tileBytes,
                contentType = "application/x-protobuf"
            )
        )
        val client = MapTileClient(
            apiBaseUrl = "https://suri-map.example.com/api",
            callFactory = callFactory
        )

        val result = client.fetchVectorTile(
            style = "osm-local",
            z = 15,
            x = 27925,
            y = 12680,
            accessToken = "access-token-1",
            policePhoneId = "phone-1"
        )

        val request = callFactory.lastRequest!!
        assertEquals(
            "https://suri-map.example.com/tiles/osm-local/15/27925/12680.pbf",
            request.url.toString()
        )
        assertEquals("application/x-protobuf", request.header("Accept"))
        assertArrayEquals(tileBytes, result.body)
        assertEquals("application/x-protobuf", result.contentType)
    }

    @Test
    fun tileErrorsExposeCanonicalErrorCode() = runBlocking {
        val callFactory = CapturingCallFactory(
            response = response(statusCode = 503, body = """{"error":"tile_unavailable"}""")
        )
        val client = MapTileClient(
            apiBaseUrl = "https://suri-map.example.com/api",
            callFactory = callFactory
        )

        val result = client.fetchStyle(styleId = "osm-local")

        assertEquals(503, result.statusCode)
        assertEquals("tile_unavailable", result.errorCode)
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
}

private fun response(
    statusCode: Int,
    body: String,
    contentType: String = "application/json"
): Response = response(statusCode, body.toByteArray(), contentType)

private fun response(
    statusCode: Int,
    bodyBytes: ByteArray,
    contentType: String
): Response {
    return Response.Builder()
        .request(Request.Builder().url("https://suri-map.example.com/placeholder").build())
        .protocol(Protocol.HTTP_1_1)
        .code(statusCode)
        .message("test")
        .body(bodyBytes.toResponseBody(contentType.toMediaTypeOrNull()))
        .build()
}
