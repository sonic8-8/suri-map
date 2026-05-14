package com.surimap.feature.bootstrap.data

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import com.surimap.BuildConfig
import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.AuthLoginNetworkRequest
import com.surimap.core.network.AuthPhoneApiClient
import com.surimap.core.network.NoAccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.network.SuriMapApiRequest
import com.surimap.core.network.SuriMapNetworkException
import com.surimap.feature.bootstrap.ui.AuthBootstrapFailureReason
import com.surimap.feature.bootstrap.ui.AuthBootstrapOutcome
import java.time.Clock

private val ACCESS_TOKEN_FIELD = Regex(""""accessToken"\s*:\s*"([^"]+)"""")
private val POLICE_PHONE_ID_FIELD = Regex(""""policePhoneId"\s*:\s*"([^"]+)"""")

data class ManagedPolicePhoneConfig(
    val policePhoneId: String?,
    val apiBaseUrl: String,
    val tileBaseUrl: String = apiBaseUrl.trimEnd('/').removeSuffix("/api"),
    val objectStorageBaseUrl: String = apiBaseUrl.trimEnd('/').removeSuffix("/api"),
    val allowedHosts: Set<String> = emptySet(),
    val isManagedPhone: Boolean = true
)

fun interface ManagedConfigurationReader {
    fun read(): ManagedPolicePhoneConfig
}

fun interface AuthBootstrapServerCheck {
    suspend fun verify(config: ManagedPolicePhoneConfig): AuthBootstrapOutcome
}

data class AuthBootstrapCredentials(
    val accountCode: String,
    val password: String,
    val policePhoneCode: String
) {
    fun isUsable(): Boolean =
        accountCode.isNotBlank() && password.isNotBlank() && policePhoneCode.isNotBlank()
}

fun interface AuthBootstrapCredentialsProvider {
    fun credentials(config: ManagedPolicePhoneConfig): AuthBootstrapCredentials?
}

fun interface ManagedConfigurationOverrideProvider {
    fun read(): ManagedPolicePhoneConfig?
}

object NoAuthBootstrapCredentialsProvider : AuthBootstrapCredentialsProvider {
    override fun credentials(config: ManagedPolicePhoneConfig): AuthBootstrapCredentials? = null
}

class DebugManagedConfigurationOverrideProvider(
    private val isDebugBuild: Boolean = BuildConfig.DEBUG,
    private val apiBaseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL,
    private val debugBootstrapAccountCode: String = BuildConfig.SURI_MAP_DEBUG_BOOTSTRAP_ACCOUNT_CODE,
    private val debugBootstrapPassword: String = BuildConfig.SURI_MAP_DEBUG_BOOTSTRAP_PASSWORD,
    private val debugBootstrapPolicePhoneCode: String = BuildConfig.SURI_MAP_DEBUG_BOOTSTRAP_POLICE_PHONE_CODE
) : ManagedConfigurationOverrideProvider {
    override fun read(): ManagedPolicePhoneConfig? {
        if (!isDebugBuild || apiBaseUrl.isBlank()) {
            return null
        }
        // Debug bootstrap only replaces Knox managed config while feature data still comes from server APIs.
        val credentials =
            AuthBootstrapCredentials(
                accountCode = debugBootstrapAccountCode,
                password = debugBootstrapPassword,
                policePhoneCode = debugBootstrapPolicePhoneCode
            )
        if (!credentials.isUsable()) {
            return null
        }
        return ManagedPolicePhoneConfig(
            policePhoneId = credentials.policePhoneCode,
            apiBaseUrl = apiBaseUrl,
            isManagedPhone = true
        )
    }
}

object BuildConfigAuthBootstrapCredentialsProvider : AuthBootstrapCredentialsProvider {
    override fun credentials(config: ManagedPolicePhoneConfig): AuthBootstrapCredentials? {
        val policePhoneCode =
            BuildConfig.SURI_MAP_DEBUG_BOOTSTRAP_POLICE_PHONE_CODE
                .takeIf(String::isNotBlank)
                ?: config.policePhoneId?.takeIf(String::isNotBlank)
        val credentials =
            AuthBootstrapCredentials(
                accountCode = BuildConfig.SURI_MAP_DEBUG_BOOTSTRAP_ACCOUNT_CODE,
                password = BuildConfig.SURI_MAP_DEBUG_BOOTSTRAP_PASSWORD,
                policePhoneCode = policePhoneCode.orEmpty()
            )
        return credentials.takeIf(AuthBootstrapCredentials::isUsable)
    }
}

class AuthBootstrapCoordinator(
    private val managedConfigurationReader: ManagedConfigurationReader,
    private val serverCheck: AuthBootstrapServerCheck
) {
    fun readConfig(): ManagedPolicePhoneConfig = managedConfigurationReader.read()

    suspend fun check(config: ManagedPolicePhoneConfig = readConfig()): AuthBootstrapOutcome {
        if (!config.isManagedPhone) {
            return AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.NotManagedPhone)
        }
        if (config.policePhoneId.isNullOrBlank()) {
            return AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ManagedConfigMissing)
        }
        return serverCheck.verify(config)
    }
}

class AndroidManagedConfigurationReader(
    private val context: Context,
    private val localOverrideProvider: ManagedConfigurationOverrideProvider =
        DebugManagedConfigurationOverrideProvider()
) : ManagedConfigurationReader {
    override fun read(): ManagedPolicePhoneConfig {
        val restrictions =
            context
                .getSystemService(RestrictionsManager::class.java)
                ?.applicationRestrictions
        val isManagedByRestrictions = restrictions != null && !restrictions.isEmpty
        if (!isManagedByRestrictions) {
            localOverrideProvider.read()?.let { return it }
        }
        val apiBaseUrl = restrictions.managedString(KEY_API_BASE_URL) ?: BuildConfig.SURI_MAP_API_BASE_URL
        val defaultResourceBaseUrl = apiBaseUrl.trimEnd('/').removeSuffix("/api")

        return ManagedPolicePhoneConfig(
            policePhoneId = restrictions.managedString(KEY_POLICE_PHONE_ID),
            apiBaseUrl = apiBaseUrl,
            tileBaseUrl = restrictions.managedString(KEY_TILE_BASE_URL) ?: defaultResourceBaseUrl,
            objectStorageBaseUrl = restrictions.managedString(KEY_OBJECT_STORAGE_BASE_URL) ?: defaultResourceBaseUrl,
            allowedHosts = restrictions.managedString(KEY_ALLOWED_HOSTS)?.toAllowedHostSet() ?: emptySet(),
            isManagedPhone = isManagedByRestrictions
        )
    }

    private fun String.toAllowedHostSet(): Set<String> {
        return split(',', '\n', ';', ' ')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
    }

    companion object {
        const val KEY_POLICE_PHONE_ID = "police_phone_id"
        const val KEY_API_BASE_URL = "api_base_url"
        const val KEY_TILE_BASE_URL = "tile_base_url"
        const val KEY_OBJECT_STORAGE_BASE_URL = "object_storage_base_url"
        const val KEY_ALLOWED_HOSTS = "allowed_hosts"
    }
}

private fun Bundle?.managedString(key: String): String? {
    return this?.getString(key)?.takeIf(String::isNotBlank)
}

class NetworkPolicePhoneBootstrapServerCheck(
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider,
    private val credentialsProvider: AuthBootstrapCredentialsProvider = NoAuthBootstrapCredentialsProvider,
    private val clock: Clock = Clock.systemUTC()
) : AuthBootstrapServerCheck {
    override suspend fun verify(config: ManagedPolicePhoneConfig): AuthBootstrapOutcome {
        return try {
            val loginSession = loginIfNeeded(config)
            val accessToken = loginSession?.accessToken ?: accessTokenProvider.accessToken()
            val policePhoneId = loginSession?.policePhoneId ?: config.policePhoneId
                ?: return AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ManagedConfigMissing)
            val response =
                apiClient.execute(
                    SuriMapApiRequest(
                        method = "POST",
                        path = "/api/police-phones/$policePhoneId/heartbeat",
                        body = """{"clientTs":"${clock.instant()}","sequence":1}""",
                        accessToken = accessToken,
                        policePhoneId = policePhoneId
                    )
                )

            if (response.isSuccessful) {
                AuthBootstrapOutcome.Ready(policePhoneId = policePhoneId, accessToken = accessToken)
            } else {
                AuthBootstrapOutcome.Blocked(mapError(response.errorCode))
            }
        } catch (_: SuriMapNetworkException) {
            AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.InternalNetworkUnavailable)
        }
    }

    private fun mapError(errorCode: String?): AuthBootstrapFailureReason =
        when (errorCode) {
            "police_phone_not_registered",
            "police_phone_not_assigned",
            "police_phone_required",
            "channel_not_allowed" -> AuthBootstrapFailureReason.ServerRejectedPhone
            else -> AuthBootstrapFailureReason.ServerRejectedPhone
        }

    private suspend fun loginIfNeeded(config: ManagedPolicePhoneConfig): BootstrapLoginSession? {
        if (!accessTokenProvider.accessToken().isNullOrBlank()) {
            return null
        }
        val credentials = credentialsProvider.credentials(config)?.takeIf(AuthBootstrapCredentials::isUsable)
            ?: return null
        val response =
            AuthPhoneApiClient(apiClient = apiClient)
                .login(
                    AuthLoginNetworkRequest(
                        accountCode = credentials.accountCode,
                        password = credentials.password,
                        channel = "APP",
                        policePhoneCode = credentials.policePhoneCode
                    )
                )
        if (!response.isSuccessful) {
            return null
        }
        val body = response.body.orEmpty()
        val accessToken = ACCESS_TOKEN_FIELD.find(body)?.groupValues?.get(1)?.takeIf(String::isNotBlank)
            ?: return null
        val policePhoneId = POLICE_PHONE_ID_FIELD.find(body)?.groupValues?.get(1)?.takeIf(String::isNotBlank)
            ?: config.policePhoneId
            ?: credentials.policePhoneCode
        return BootstrapLoginSession(accessToken = accessToken, policePhoneId = policePhoneId)
    }

    private data class BootstrapLoginSession(
        val accessToken: String,
        val policePhoneId: String
    )
}
