package com.surimap.feature.bootstrap.data

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import com.surimap.BuildConfig
import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.NoAccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.network.SuriMapApiRequest
import com.surimap.core.network.SuriMapNetworkException
import com.surimap.feature.bootstrap.ui.AuthBootstrapFailureReason
import com.surimap.feature.bootstrap.ui.AuthBootstrapOutcome
import java.time.Clock

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
    private val context: Context
) : ManagedConfigurationReader {
    override fun read(): ManagedPolicePhoneConfig {
        val restrictions =
            context
                .getSystemService(RestrictionsManager::class.java)
                ?.applicationRestrictions
        val apiBaseUrl = restrictions.managedString(KEY_API_BASE_URL) ?: BuildConfig.SURI_MAP_API_BASE_URL
        val defaultResourceBaseUrl = apiBaseUrl.trimEnd('/').removeSuffix("/api")

        return ManagedPolicePhoneConfig(
            policePhoneId = restrictions.managedString(KEY_POLICE_PHONE_ID),
            apiBaseUrl = apiBaseUrl,
            tileBaseUrl = restrictions.managedString(KEY_TILE_BASE_URL) ?: defaultResourceBaseUrl,
            objectStorageBaseUrl = restrictions.managedString(KEY_OBJECT_STORAGE_BASE_URL) ?: defaultResourceBaseUrl,
            allowedHosts = restrictions.managedString(KEY_ALLOWED_HOSTS)?.toAllowedHostSet() ?: emptySet(),
            isManagedPhone = restrictions != null && !restrictions.isEmpty
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
    private val clock: Clock = Clock.systemUTC()
) : AuthBootstrapServerCheck {
    override suspend fun verify(config: ManagedPolicePhoneConfig): AuthBootstrapOutcome {
        val policePhoneId = config.policePhoneId
            ?: return AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ManagedConfigMissing)

        return try {
            val response =
                apiClient.execute(
                    SuriMapApiRequest(
                        method = "POST",
                        path = "/api/police-phones/$policePhoneId/heartbeat",
                        body = """{"clientTs":"${clock.instant()}","sequence":1}""",
                        accessToken = accessTokenProvider.accessToken(),
                        policePhoneId = policePhoneId
                    )
                )

            if (response.isSuccessful) {
                AuthBootstrapOutcome.Ready(policePhoneId = policePhoneId)
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
}
