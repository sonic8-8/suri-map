package com.surimap.core.map

import com.surimap.BuildConfig
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

private val ERROR_FIELD = Regex(""""error"\s*:\s*"([^"]+)"""")
private val ATTRIBUTION_FIELD = Regex(""""attribution"\s*:\s*"([^"]+)"""")

data class MapLibreTileSourceConfig(
    val styleId: String,
    val styleUrl: String,
    val requestHeaders: Map<String, String>
)

data class MapTileResponse(
    val statusCode: Int,
    val body: ByteArray,
    val contentType: String?,
    val errorCode: String?
) {
    val isSuccessful: Boolean = statusCode in 200..299
    val bodyText: String? = body.takeIf(ByteArray::isNotEmpty)?.decodeToString()
    val requiredAttribution: String? = bodyText?.let(::parseAttribution)
}

class MapLibreTileSourceFactory(
    private val apiBaseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL
) {
    fun styleSource(
        styleId: String,
        accessToken: String? = null,
        policePhoneId: String? = null
    ): MapLibreTileSourceConfig {
        return MapLibreTileSourceConfig(
            styleId = styleId,
            styleUrl = buildTileUrl(apiBaseUrl, stylePath(styleId)),
            requestHeaders = tileRequestHeaders(accessToken, policePhoneId)
        )
    }
}

class MapTileClient(
    private val apiBaseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL,
    private val callFactory: Call.Factory = OkHttpClient()
) {
    suspend fun fetchStyle(
        styleId: String,
        accessToken: String? = null,
        policePhoneId: String? = null
    ): MapTileResponse {
        return execute(
            path = stylePath(styleId),
            accept = "application/json",
            accessToken = accessToken,
            policePhoneId = policePhoneId
        )
    }

    suspend fun fetchVectorTile(
        style: String,
        z: Int,
        x: Int,
        y: Int,
        accessToken: String? = null,
        policePhoneId: String? = null
    ): MapTileResponse {
        return execute(
            path = vectorTilePath(style, z, x, y),
            accept = "application/x-protobuf",
            accessToken = accessToken,
            policePhoneId = policePhoneId
        )
    }

    private suspend fun execute(
        path: String,
        accept: String,
        accessToken: String?,
        policePhoneId: String?
    ): MapTileResponse = withContext(Dispatchers.IO) {
        try {
            val response = callFactory.newCall(
                Request.Builder()
                    .url(buildTileUrl(apiBaseUrl, path))
                    .headers(okhttp3.Headers.headersOf(*tileHeaderPairs(accessToken, policePhoneId, accept)))
                    .get()
                    .build()
            ).execute()
            response.use {
                val responseBody = it.body
                val contentType = responseBody.contentType()?.toString()
                val body = responseBody.bytes()
                MapTileResponse(
                    statusCode = it.code,
                    body = body,
                    contentType = contentType,
                    errorCode = parseErrorCode(body)
                )
            }
        } catch (exception: IOException) {
            throw MapTileNetworkException(exception)
        }
    }
}

class MapTileNetworkException(
    cause: Throwable
) : IOException("network_error", cause)

private fun stylePath(styleId: String): String {
    return "/tiles/styles/${encodePathSegment(styleId)}.json"
}

private fun vectorTilePath(style: String, z: Int, x: Int, y: Int): String {
    return "/tiles/${encodePathSegment(style)}/$z/$x/$y.pbf"
}

private fun buildTileUrl(apiBaseUrl: String, path: String): String {
    val tileBaseUrl = apiBaseUrl.trimEnd('/').removeSuffix("/api")
    val normalizedPath = if (path.startsWith("/")) path else "/$path"
    return tileBaseUrl + normalizedPath
}

private fun tileRequestHeaders(
    accessToken: String?,
    policePhoneId: String?
): Map<String, String> {
    return buildMap {
        put("X-Client-Channel", "APP")
        accessToken
            ?.takeIf(String::isNotBlank)
            ?.let { token ->
                put("Authorization", if (token.startsWith("Bearer ")) token else "Bearer $token")
            }
        policePhoneId
            ?.takeIf(String::isNotBlank)
            ?.let { put("X-PolicePhone-Id", it) }
    }
}

private fun tileHeaderPairs(
    accessToken: String?,
    policePhoneId: String?,
    accept: String
): Array<String> {
    val headers = tileRequestHeaders(accessToken, policePhoneId) + ("Accept" to accept)
    return headers.flatMap { (key, value) -> listOf(key, value) }.toTypedArray()
}

private fun encodePathSegment(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}

private fun parseErrorCode(body: ByteArray): String? {
    if (body.isEmpty()) {
        return null
    }
    return ERROR_FIELD.find(body.decodeToString())?.groupValues?.get(1)
}

private fun parseAttribution(body: String): String? {
    return ATTRIBUTION_FIELD.find(body)?.groupValues?.get(1)
}
