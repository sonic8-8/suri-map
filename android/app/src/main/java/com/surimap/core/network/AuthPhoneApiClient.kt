package com.surimap.core.network

import com.surimap.core.sync.jsonNumber
import com.surimap.core.sync.jsonObject
import com.surimap.core.sync.jsonString

data class AuthLoginNetworkRequest(
    val accountCode: String,
    val password: String,
    val channel: String,
    val policePhoneCode: String? = null
)

data class AuthLogoutNetworkRequest(
    val sessionId: String? = null
)

data class RegisterFcmTokenNetworkRequest(
    val policePhoneId: String,
    val appInstanceId: String,
    val token: String
)

data class PolicePhoneHeartbeatNetworkRequest(
    val policePhoneId: String,
    val clientTs: String,
    val sequence: Long,
    val lastSyncAt: String? = null,
    val batteryPercent: Int? = null
)

class AuthPhoneApiClient(
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun login(request: AuthLoginNetworkRequest): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "POST",
                path = "/api/auth/login",
                body = jsonObject(
                    "accountCode" to jsonString(request.accountCode),
                    "password" to jsonString(request.password),
                    "channel" to jsonString(request.channel),
                    "policePhoneCode" to request.policePhoneCode?.let(::jsonString)
                )
            )
        )
    }

    suspend fun logout(
        request: AuthLogoutNetworkRequest = AuthLogoutNetworkRequest()
    ): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "POST",
                path = "/api/auth/logout",
                body = jsonObject(
                    "sessionId" to request.sessionId?.let(::jsonString)
                ),
                accessToken = accessTokenProvider.accessToken()
            )
        )
    }

    suspend fun registerFcmToken(request: RegisterFcmTokenNetworkRequest): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "POST",
                path = "/api/fcm/tokens",
                body = jsonObject(
                    "appInstanceId" to jsonString(request.appInstanceId),
                    "token" to jsonString(request.token)
                ),
                accessToken = accessTokenProvider.accessToken(),
                policePhoneId = request.policePhoneId
            )
        )
    }

    suspend fun heartbeat(request: PolicePhoneHeartbeatNetworkRequest): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "POST",
                path = "/api/police-phones/${request.policePhoneId}/heartbeat",
                body = jsonObject(
                    "clientTs" to jsonString(request.clientTs),
                    "sequence" to jsonNumber(request.sequence),
                    "lastSyncAt" to request.lastSyncAt?.let(::jsonString),
                    "batteryPercent" to request.batteryPercent?.let(::jsonNumber)
                ),
                accessToken = accessTokenProvider.accessToken(),
                policePhoneId = request.policePhoneId
            )
        )
    }
}
