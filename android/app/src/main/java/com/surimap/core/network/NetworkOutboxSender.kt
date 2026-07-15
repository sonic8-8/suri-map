package com.surimap.core.network

import android.util.Log
import com.surimap.BuildConfig
import com.surimap.core.database.OutboxEntity
import com.surimap.core.sync.OutboxSender
import com.surimap.core.sync.SendResult

class HttpOutboxSender(
    private val apiClient: SuriMapApiClient,
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider,
    private val timingReporter: (OutboxEntity, SuriMapApiResponse) -> Unit = { _, _ -> }
) : OutboxSender {
    private var lastRetryableFailureErrorCode: String? = null
    private var lastRetryAfterDelayMs: Long? = null
    private var lastFinalFailureErrorCode: String? = null

    override suspend fun send(row: OutboxEntity): SendResult {
        lastRetryableFailureErrorCode = null
        lastRetryAfterDelayMs = null
        lastFinalFailureErrorCode = null
        val response = try {
            apiClient.execute(
                SuriMapApiRequest(
                    method = row.requestMethod,
                    path = row.requestPath,
                    body = row.payloadJson,
                    accessToken = accessTokenProvider.accessToken(),
                    policePhoneId = row.policePhoneId,
                    idempotencyKey = row.idempotencyKey
                )
            )
        } catch (exception: SuriMapNetworkException) {
            lastRetryableFailureErrorCode = "network_unavailable"
            return SendResult.RETRYABLE_FAILURE
        }
        timingReporter(row, response)

        val errorCode = response.errorCode ?: "http_${response.statusCode}"
        return when {
            response.isSuccessful -> SendResult.ACKED
            isRetryable(response.statusCode, response.errorCode) -> {
                lastRetryableFailureErrorCode = errorCode
                lastRetryAfterDelayMs = response.retryAfterDelayMs
                SendResult.RETRYABLE_FAILURE
            }
            else -> {
                lastFinalFailureErrorCode = errorCode
                SendResult.FINAL_FAILURE
            }
        }
    }

    override fun retryableFailureErrorCode(): String? = lastRetryableFailureErrorCode

    override fun retryAfterDelayMs(): Long? = lastRetryAfterDelayMs

    override fun finalFailureErrorCode(): String? = lastFinalFailureErrorCode

    private fun isRetryable(statusCode: Int, errorCode: String?): Boolean {
        return when {
            errorCode in finalAccessGuardErrors -> false
            errorCode == "police_phone_required" -> true
            statusCode == 401 -> true
            statusCode == 408 -> true
            statusCode == 429 -> true
            statusCode >= 500 -> true
            else -> false
        }
    }

    private companion object {
        val finalAccessGuardErrors =
            setOf(
                "police_phone_not_registered",
                "police_phone_not_assigned",
                "channel_not_allowed",
                "role_denied"
            )
    }
}

object AndroidNetworkFactory {
    fun createOutboxSender(
        baseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL,
        accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
    ): OutboxSender {
        return HttpOutboxSender(
            apiClient = SuriMapApiClient(baseUrl = baseUrl),
            accessTokenProvider = accessTokenProvider,
            timingReporter = { row, response ->
                if (BuildConfig.DEBUG) {
                    val durationMs = response.requestStartedAtMillis?.let { startedAt ->
                        response.responseReceivedAtMillis?.minus(startedAt)
                    }
                    Log.d(
                        OUTBOX_HTTP_TIMING_TAG,
                        "outboxId=${row.outboxId} " +
                            "path=${row.requestPath} " +
                            "requestStartedAtMillis=${response.requestStartedAtMillis} " +
                            "responseReceivedAtMillis=${response.responseReceivedAtMillis} " +
                            "durationMs=$durationMs statusCode=${response.statusCode}"
                    )
                }
            }
        )
    }

    fun createSyncApiClient(
        baseUrl: String = com.surimap.BuildConfig.SURI_MAP_API_BASE_URL,
        accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
    ): SyncApiClient {
        return SyncApiClient(
            apiClient = SuriMapApiClient(baseUrl = baseUrl),
            accessTokenProvider = accessTokenProvider
        )
    }
}

private const val OUTBOX_HTTP_TIMING_TAG = "SuriMapOutboxHttp"
