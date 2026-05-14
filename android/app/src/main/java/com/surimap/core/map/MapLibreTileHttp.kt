package com.surimap.core.map

import android.util.Log
import com.surimap.BuildConfig
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.module.http.HttpRequestUtil

private const val MAP_LIBRE_TILE_LOG_TAG = "SuriMapTiles"

class MapLibreTileCallFactory(
    private val delegate: Call.Factory = OkHttpClient(),
    tileBaseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL,
    private val accessTokenProvider: () -> String? = { null },
    private val policePhoneIdProvider: () -> String? = { null }
) : Call.Factory {
    private val normalizedTileBaseUrl = tileBaseUrl.trimEnd('/').removeSuffix("/api")

    override fun newCall(request: Request): Call {
        val tileRequest = request.withTileHeadersIfNeeded()
        if (BuildConfig.DEBUG && tileRequest.url.toString().startsWith("$normalizedTileBaseUrl/tiles/")) {
            logTileRequest(tileRequest)
        }
        return delegate.newCall(tileRequest)
    }

    private fun Request.withTileHeadersIfNeeded(): Request {
        if (!url.toString().startsWith("$normalizedTileBaseUrl/tiles/")) {
            return this
        }

        val builder = newBuilder()
            .header("X-Client-Channel", "APP")

        accessTokenProvider()
            ?.takeIf(String::isNotBlank)
            ?.let { token ->
                builder.header("Authorization", if (token.startsWith("Bearer ")) token else "Bearer $token")
            }
        policePhoneIdProvider()
            ?.takeIf(String::isNotBlank)
            ?.let { builder.header("X-PolicePhone-Id", it) }

        return builder.build()
    }
}

object MapLibreTileHttpInstaller {
    fun install(callFactory: Call.Factory) {
        if (BuildConfig.DEBUG) {
            HttpRequestUtil.setLogEnabled(true)
            HttpRequestUtil.setPrintRequestUrlOnFailure(true)
        }
        HttpRequestUtil.setOkHttpClient(callFactory)
    }
}

private fun logTileRequest(request: Request) {
    runCatching {
        Log.d(MAP_LIBRE_TILE_LOG_TAG, "MapLibre request ${request.method} ${request.url.encodedPath}")
    }
}
