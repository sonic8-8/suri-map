package com.surimap.core.network

import com.surimap.core.database.OutboxEntity
import com.surimap.core.sync.OutboxSender
import com.surimap.core.sync.SendResult

class HttpOutboxSender(
    private val apiClient: SuriMapApiClient,
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) : OutboxSender {

    override suspend fun send(row: OutboxEntity): SendResult {
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
            return SendResult.RETRYABLE_FAILURE
        }

        return when {
            response.isSuccessful -> SendResult.ACKED
            response.statusCode == 408 -> SendResult.RETRYABLE_FAILURE
            response.statusCode == 429 -> SendResult.RETRYABLE_FAILURE
            response.statusCode >= 500 -> SendResult.RETRYABLE_FAILURE
            else -> SendResult.FINAL_FAILURE
        }
    }
}

object AndroidNetworkFactory {
    fun createOutboxSender(
        baseUrl: String = com.surimap.BuildConfig.SURI_MAP_API_BASE_URL,
        accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
    ): OutboxSender {
        return HttpOutboxSender(
            apiClient = SuriMapApiClient(baseUrl = baseUrl),
            accessTokenProvider = accessTokenProvider
        )
    }
}
