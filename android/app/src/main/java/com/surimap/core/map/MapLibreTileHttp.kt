package com.surimap.core.map

import com.surimap.BuildConfig
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.module.http.HttpRequestUtil

class MapLibreTileCallFactory(
    private val delegate: Call.Factory = OkHttpClient(),
    tileBaseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL,
    private val accessTokenProvider: () -> String? = { null },
    private val policePhoneIdProvider: () -> String? = { null }
) : Call.Factory {
    private val normalizedTileBaseUrl = tileBaseUrl.trimEnd('/').removeSuffix("/api")

    override fun newCall(request: Request): Call {
        return delegate.newCall(request.withTileHeadersIfNeeded())
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
        HttpRequestUtil.setOkHttpClient(callFactory)
    }
}
