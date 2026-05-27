package com.surimap.core.map

import android.content.Context
import android.util.Log
import com.surimap.BuildConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.Timeout
import org.maplibre.android.module.http.HttpRequestUtil
import kotlin.reflect.KClass

private const val MAP_LIBRE_TILE_LOG_TAG = "SuriMapTiles"

class MapLibreTileCallFactory(
    private val delegate: Call.Factory = OkHttpClient(),
    tileBaseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL,
    private val accessTokenProvider: () -> String? = { null },
    private val policePhoneIdProvider: () -> String? = { null },
    private val offlineTileCache: OfflineTileCache? = null
) : Call.Factory {
    private val normalizedTileBaseUrl = tileBaseUrl.trimEnd('/').removeSuffix("/api")

    override fun newCall(request: Request): Call {
        val tileRequest = request.withTileHeadersIfNeeded()
        offlineTileCache
            ?.readTile(tileRequest.url)
            ?.let { bytes ->
                return CachedMapTileCall(tileRequest, bytes)
            }
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

class OfflineTileCache(
    private val rootDir: File
) {
    suspend fun writeTile(itemKey: String, bytes: ByteArray) {
        writeTile(itemKey = itemKey, downloadUrl = null, bytes = bytes)
    }

    suspend fun writeTile(
        itemKey: String,
        downloadUrl: String?,
        bytes: ByteArray
    ) {
        val tileFile = tileFileForItemKey(itemKey)
            ?: downloadUrl?.let(::tileFileForDownloadUrl)
            ?: return
        withContext(Dispatchers.IO) {
            tileFile.parentFile?.mkdirs()
            tileFile.writeBytes(bytes)
        }
    }

    fun readTile(url: HttpUrl): ByteArray? {
        val itemKey = url.toTileItemKey() ?: return null
        val tileFile = tileFileForItemKey(itemKey) ?: return null
        return runCatching {
            if (tileFile.isFile) {
                tileFile.readBytes()
            } else {
                null
            }
        }.getOrNull()
    }

    private fun tileFileForItemKey(itemKey: String): File? {
        val parts = itemKey.split(":")
        if (parts.size != 5 || parts[0] != "tile") {
            return null
        }
        val (_, style, z, x, y) = parts
        if (listOf(style, z, x, y).any { it.isBlank() || it.contains("..") || it.contains('/') }) {
            return null
        }
        return File(rootDir, "$style/$z/$x/$y.pbf")
    }

    private fun tileFileForDownloadUrl(downloadUrl: String): File? {
        val path =
            when {
                downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://") ->
                    downloadUrl.toHttpUrlOrNull()?.encodedPath
                downloadUrl.startsWith("/") -> downloadUrl.substringBefore("?")
                else -> "/${downloadUrl.substringBefore("?")}"
            } ?: return null
        val segments = path.trim('/').split("/")
        if (segments.size != 5 || segments[0] != "tiles") {
            return null
        }
        val style = segments[1]
        val z = segments[2]
        val x = segments[3]
        val y = segments[4].removeSuffix(".pbf")
        return tileFileForItemKey("tile:$style:$z:$x:$y")
    }

    private fun HttpUrl.toTileItemKey(): String? {
        val segments = encodedPathSegments
        if (segments.size != 5 || segments[0] != "tiles") {
            return null
        }
        val style = segments[1]
        val z = segments[2]
        val x = segments[3]
        val y = segments[4].removeSuffix(".pbf")
        if (listOf(style, z, x, y).any(String::isBlank)) {
            return null
        }
        return "tile:$style:$z:$x:$y"
    }

    companion object {
        fun fromContext(context: Context): OfflineTileCache =
            OfflineTileCache(File(context.filesDir, "offline-map-tiles"))
    }
}

private class CachedMapTileCall(
    private val request: Request,
    private val bytes: ByteArray
) : Call {
    private val executed = AtomicBoolean(false)
    private val canceled = AtomicBoolean(false)

    override fun request(): Request = request

    override fun execute(): Response {
        check(executed.compareAndSet(false, true)) { "Already Executed" }
        if (canceled.get()) {
            throw IOException("Canceled")
        }
        return response()
    }

    override fun enqueue(responseCallback: Callback) {
        try {
            responseCallback.onResponse(this, execute())
        } catch (exception: IOException) {
            responseCallback.onFailure(this, exception)
        }
    }

    override fun cancel() {
        canceled.set(true)
    }

    override fun isExecuted(): Boolean = executed.get()

    override fun isCanceled(): Boolean = canceled.get()

    override fun timeout(): Timeout = Timeout.NONE

    override fun <T : Any> tag(type: KClass<T>): T? = null

    override fun <T> tag(type: Class<out T>): T? = null

    override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()

    override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()

    override fun clone(): Call = CachedMapTileCall(request, bytes)

    private fun response(): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("X-SuriMap-Offline-Cache", "HIT")
            .body(bytes.toResponseBody("application/x-protobuf".toMediaType()))
            .build()
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
