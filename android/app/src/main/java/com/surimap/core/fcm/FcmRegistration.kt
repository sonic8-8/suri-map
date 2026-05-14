package com.surimap.core.fcm

import android.content.Context
import com.surimap.core.network.RegisterFcmTokenNetworkRequest
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.network.SuriMapNetworkException
import java.util.UUID

fun interface FcmTokenProvider {
    suspend fun currentToken(): String?
}

object NoFcmTokenProvider : FcmTokenProvider {
    override suspend fun currentToken(): String? = null
}

interface FcmRegistrationStateStore {
    fun appInstanceId(): String
    fun pendingToken(): String?
    fun savePendingToken(token: String)
    fun shouldRegister(policePhoneId: String, token: String): Boolean
    fun markRegistered(policePhoneId: String, token: String)
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

    override fun shouldRegister(policePhoneId: String, token: String): Boolean {
        if (policePhoneId.isBlank() || token.isBlank()) {
            return false
        }
        return preferences.getString(KEY_REGISTERED_POLICE_PHONE_ID, null) != policePhoneId ||
            preferences.getString(KEY_REGISTERED_TOKEN, null) != token
    }

    override fun markRegistered(policePhoneId: String, token: String) {
        if (policePhoneId.isBlank() || token.isBlank()) {
            return
        }
        val editor =
            preferences.edit()
                .putString(KEY_REGISTERED_POLICE_PHONE_ID, policePhoneId)
                .putString(KEY_REGISTERED_TOKEN, token)
        if (pendingToken() == token) {
            editor.remove(KEY_PENDING_TOKEN)
        }
        editor.apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "suri_map_fcm_registration"
        const val KEY_APP_INSTANCE_ID = "app_instance_id"
        const val KEY_PENDING_TOKEN = "pending_token"
        const val KEY_REGISTERED_POLICE_PHONE_ID = "registered_police_phone_id"
        const val KEY_REGISTERED_TOKEN = "registered_token"
    }
}

class FcmRegistrationCoordinator(
    private val firebaseMessagingEnabled: Boolean,
    private val tokenProvider: FcmTokenProvider,
    private val stateStore: FcmRegistrationStateStore,
    private val registerToken: suspend (RegisterFcmTokenNetworkRequest) -> SuriMapApiResponse
) {
    suspend fun registerCurrentToken(policePhoneId: String): FcmRegistrationResult {
        if (!firebaseMessagingEnabled) {
            return FcmRegistrationResult.Disabled
        }
        val normalizedPolicePhoneId = policePhoneId.takeIf(String::isNotBlank)
            ?: return FcmRegistrationResult.Skipped
        val token = stateStore.pendingToken()
            ?: tokenProvider.currentToken()?.takeIf(String::isNotBlank)
            ?: return FcmRegistrationResult.Skipped

        stateStore.savePendingToken(token)
        if (!stateStore.shouldRegister(normalizedPolicePhoneId, token)) {
            stateStore.markRegistered(normalizedPolicePhoneId, token)
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
                stateStore.markRegistered(normalizedPolicePhoneId, token)
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
