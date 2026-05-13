package com.surimap.core.network

import com.surimap.BuildConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private val ERROR_FIELD = Regex(""""error"\s*:\s*"([^"]+)"""")

data class SuriMapApiRequest(
    val method: String,
    val path: String,
    val body: String? = null,
    val accessToken: String? = null,
    val policePhoneId: String? = null,
    val idempotencyKey: String? = null
)

data class SuriMapApiResponse(
    val statusCode: Int,
    val body: String?,
    val errorCode: String?,
    val retryAfterDelayMs: Long? = null
) {
    val isSuccessful: Boolean = statusCode in 200..299
}

class SuriMapNetworkException(
    cause: Throwable
) : IOException("network_error", cause)

fun interface AccessTokenProvider {
    fun accessToken(): String?
}

object NoAccessTokenProvider : AccessTokenProvider {
    override fun accessToken(): String? = null
}

class SuriMapApiClient(
    private val baseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL,
    private val callFactory: Call.Factory = OkHttpClient()
) {
    suspend fun execute(request: SuriMapApiRequest): SuriMapApiResponse = withContext(Dispatchers.IO) {
        try {
            val response = callFactory.newCall(toOkHttpRequest(request)).execute()
            response.use {
                val body = it.body.string()
                SuriMapApiResponse(
                    statusCode = it.code,
                    body = body.ifBlank { null },
                    errorCode = parseErrorCode(body),
                    retryAfterDelayMs = parseRetryAfterDelayMs(it.header("Retry-After"))
                )
            }
        } catch (exception: IOException) {
            throw SuriMapNetworkException(exception)
        }
    }

    private fun toOkHttpRequest(request: SuriMapApiRequest): Request {
        val builder = Request.Builder()
            .url(buildUrl(request.path))
            .header("X-Client-Channel", "APP")

        request.accessToken
            ?.takeIf(String::isNotBlank)
            ?.let { token ->
                builder.header(
                    "Authorization",
                    if (token.startsWith("Bearer ")) token else "Bearer $token"
                )
            }
        request.policePhoneId
            ?.takeIf(String::isNotBlank)
            ?.let { builder.header("X-PolicePhone-Id", it) }
        request.idempotencyKey
            ?.takeIf(String::isNotBlank)
            ?.let { builder.header("Idempotency-Key", it) }

        val body = request.body?.toRequestBody(JSON_MEDIA_TYPE)
        return builder.method(request.method.uppercase(), body).build()
    }

    private fun buildUrl(path: String): String {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        if (normalizedBaseUrl.endsWith("/api") && normalizedPath.startsWith("/api/")) {
            return normalizedBaseUrl + normalizedPath.removePrefix("/api")
        }
        return normalizedBaseUrl + normalizedPath
    }
}

private fun parseErrorCode(body: String): String? {
    if (body.isBlank()) {
        return null
    }
    return ERROR_FIELD.find(body)?.groupValues?.get(1)
}

private fun parseRetryAfterDelayMs(value: String?): Long? {
    val seconds = value?.trim()?.toLongOrNull() ?: return null
    return (seconds * 1_000L).takeIf { it >= 0L }
}
