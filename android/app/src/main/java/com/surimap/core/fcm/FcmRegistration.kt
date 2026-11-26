package com.surimap.core.fcm

import android.content.Context
import com.surimap.core.network.RegisterFcmTokenNetworkRequest
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.network.SuriMapNetworkException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun interface FcmTokenProvider {
    suspend fun currentToken(): String?

    suspend fun deleteToken() {}
}

object NoFcmTokenProvider : FcmTokenProvider {
    override suspend fun currentToken(): String? = null
}

interface FcmRegistrationStateStore {
    fun appInstanceId(): String
    fun pendingToken(): String?
    fun savePendingToken(token: String)
    fun shouldRegister(accountId: String, policePhoneId: String, token: String): Boolean
    fun markRegistered(accountId: String, policePhoneId: String, token: String)
    fun clearRegistration()
}

class SharedPreferencesFcmRegistrationStateStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME
) : FcmRegistrationStateStore {
    private val preferences =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun appInstanceId(): String {
        val existing = preferences.getString(KEY_APP_INSTANCE_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = UUID.randomUUID().toString()
        preferences.edit()
            .putString(KEY_APP_INSTANCE_ID, generated)
            .apply()
        return generated
    }

    override fun pendingToken(): String? =
        preferences.getString(KEY_PENDING_TOKEN, null)?.takeIf(String::isNotBlank)

    override fun savePendingToken(token: String) {
        val normalizedToken = token.takeIf(String::isNotBlank) ?: return
        preferences.edit()
            .putString(KEY_PENDING_TOKEN, normalizedToken)
            .apply()
    }

    override fun shouldRegister(accountId: String, policePhoneId: String, token: String): Boolean {
        if (accountId.isBlank() || policePhoneId.isBlank() || token.isBlank()) {
            return false
        }
        return preferences.getString(KEY_REGISTERED_ACCOUNT_ID, null) != accountId ||
            preferences.getString(KEY_REGISTERED_POLICE_PHONE_ID, null) != policePhoneId ||
            preferences.getString(KEY_REGISTERED_TOKEN, null) != token
    }

    override fun markRegistered(accountId: String, policePhoneId: String, token: String) {
        if (accountId.isBlank() || policePhoneId.isBlank() || token.isBlank()) {
            return
        }
        val editor =
            preferences.edit()
                .putString(KEY_REGISTERED_ACCOUNT_ID, accountId)
                .putString(KEY_REGISTERED_POLICE_PHONE_ID, policePhoneId)
                .putString(KEY_REGISTERED_TOKEN, token)
        if (pendingToken() == token) {
            editor.remove(KEY_PENDING_TOKEN)
        }
        editor.apply()
    }

    override fun clearRegistration() {
        preferences.edit()
            .remove(KEY_PENDING_TOKEN)
            .remove(KEY_REGISTERED_ACCOUNT_ID)
            .remove(KEY_REGISTERED_POLICE_PHONE_ID)
            .remove(KEY_REGISTERED_TOKEN)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "suri_map_fcm_registration"
        const val KEY_APP_INSTANCE_ID = "app_instance_id"
        const val KEY_PENDING_TOKEN = "pending_token"
        const val KEY_REGISTERED_ACCOUNT_ID = "registered_account_id"
        const val KEY_REGISTERED_POLICE_PHONE_ID = "registered_police_phone_id"
        const val KEY_REGISTERED_TOKEN = "registered_token"
    }
}

class FcmRegistrationCoordinator(
    private val firebaseMessagingEnabled: Boolean,
    private val tokenProvider: FcmTokenProvider,
    private val stateStore: FcmRegistrationStateStore,
    private val registerToken: suspend (RegisterFcmTokenNetworkRequest) -> SuriMapApiResponse,
    private val revokeToken: suspend (policePhoneId: String) -> SuriMapApiResponse
) {
    suspend fun registerCurrentToken(accountId: String, policePhoneId: String): FcmRegistrationResult {
        if (!firebaseMessagingEnabled) {
            return FcmRegistrationResult.Disabled
        }
        return registrationLock.withLock { registerCurrentTokenLocked(accountId, policePhoneId) }
    }

    private suspend fun registerCurrentTokenLocked(accountId: String, policePhoneId: String): FcmRegistrationResult {
        val normalizedAccountId = accountId.takeIf(String::isNotBlank)
            ?: return FcmRegistrationResult.Skipped
        val normalizedPolicePhoneId = policePhoneId.takeIf(String::isNotBlank)
            ?: return FcmRegistrationResult.Skipped
        val token = stateStore.pendingToken()
            ?: tokenProvider.currentToken()?.takeIf(String::isNotBlank)
            ?: return FcmRegistrationResult.Skipped

        stateStore.savePendingToken(token)
        if (!stateStore.shouldRegister(normalizedAccountId, normalizedPolicePhoneId, token)) {
            stateStore.markRegistered(normalizedAccountId, normalizedPolicePhoneId, token)
            return FcmRegistrationResult.Skipped
        }

        return try {
            val response =
                registerToken(
                    RegisterFcmTokenNetworkRequest(
                        policePhoneId = normalizedPolicePhoneId,
                        appInstanceId = stateStore.appInstanceId(),
                        token = token
                    )
                )
            if (response.isSuccessful) {
                stateStore.markRegistered(normalizedAccountId, normalizedPolicePhoneId, token)
                FcmRegistrationResult.Registered
            } else {
                FcmRegistrationResult.Failed(
                    statusCode = response.statusCode,
                    errorCode = response.errorCode
                )
            }
        } catch (_: SuriMapNetworkException) {
            FcmRegistrationResult.Failed(statusCode = null, errorCode = "network_error")
        }
    }

    // 통신이 끊겨 연결 해제 요청이 실패하면 서버의 토큰은 다음 로그인 때 다시 등록될 때까지 이전 계정에 연결된 채로 남는다(#59에서 받아들인 한계).
    suspend fun revokeForLogout(policePhoneId: String) {
        if (!firebaseMessagingEnabled) {
            return
        }
        registrationLock.withLock {
            try {
                policePhoneId.takeIf(String::isNotBlank)?.let { revokeToken(it) }
            } catch (_: SuriMapNetworkException) {
            } finally {
                // 해제 요청이 성공하든 실패하든 취소되든 앱의 토큰과 등록 기록은 지운다.
                withContext(NonCancellable) {
                    tokenProvider.deleteToken()
                    stateStore.clearRegistration()
                }
            }
        }
    }

    // 잠금을 호출한 자리에서 바로 잡으므로, 이어서 로그인한 계정의 등록은 이 정리가 끝난 뒤에 진행된다.
    fun startLogoutCleanup(scope: CoroutineScope, policePhoneId: String): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) { revokeForLogout(policePhoneId) }

    private companion object {
        // 앱 설치의 FCM 토큰과 등록 기록은 하나뿐이므로 coordinator 인스턴스가 달라도 같은 잠금을 쓴다.
        val registrationLock = Mutex()
    }
}

sealed interface FcmRegistrationResult {
    object Disabled : FcmRegistrationResult
    object Skipped : FcmRegistrationResult
    object Registered : FcmRegistrationResult

    data class Failed(
        val statusCode: Int?,
        val errorCode: String?
    ) : FcmRegistrationResult
}
