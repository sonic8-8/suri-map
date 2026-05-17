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

fun interface AuthBootstrapEnvironmentCheck {
    suspend fun verify(config: ManagedPolicePhoneConfig): AuthBootstrapOutcome?
}

object NoAuthBootstrapEnvironmentCheck : AuthBootstrapEnvironmentCheck {
    override suspend fun verify(config: ManagedPolicePhoneConfig): AuthBootstrapOutcome? = null
}

fun interface ManagedConfigurationOverrideProvider {
    fun read(): ManagedPolicePhoneConfig?
}

class DebugManagedConfigurationOverrideProvider(
    private val isDebugBuild: Boolean = BuildConfig.DEBUG,
    private val apiBaseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL,
    private val debugBootstrapPolicePhoneId: String = BuildConfig.SURI_MAP_DEBUG_BOOTSTRAP_POLICE_PHONE_ID
) : ManagedConfigurationOverrideProvider {
    override fun read(): ManagedPolicePhoneConfig? {
        if (!isDebugBuild || apiBaseUrl.isBlank() || debugBootstrapPolicePhoneId.isBlank()) {
            return null
        }
        // Debug bootstrap only replaces MDM managed config. User authentication still happens via OIDC.
        return ManagedPolicePhoneConfig(
            policePhoneId = debugBootstrapPolicePhoneId,
            apiBaseUrl = apiBaseUrl,
            isManagedPhone = true
        )
    }
}

class AuthBootstrapCoordinator(
    private val managedConfigurationReader: ManagedConfigurationReader,
    private val serverCheck: AuthBootstrapServerCheck,
    private val environmentCheck: AuthBootstrapEnvironmentCheck = NoAuthBootstrapEnvironmentCheck
) {
    fun readConfig(): ManagedPolicePhoneConfig = managedConfigurationReader.read()

    suspend fun check(config: ManagedPolicePhoneConfig = readConfig()): AuthBootstrapOutcome {
        if (!config.isManagedPhone) {
            return AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.NotManagedPhone)
        }
        if (config.policePhoneId.isNullOrBlank()) {
            return AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ManagedConfigMissing)
        }
        environmentCheck.verify(config)?.let { return it }
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
    private val clock: Clock = Clock.systemUTC()
) : AuthBootstrapServerCheck {
    override suspend fun verify(config: ManagedPolicePhoneConfig): AuthBootstrapOutcome {
        return try {
            val accessToken = accessTokenProvider.accessToken()
                ?: return AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.AuthenticationRequired)
            val policePhoneId = config.policePhoneId
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
}

class NetworkAuthBootstrapEnvironmentCheck(
    private val apiClient: SuriMapApiClient = SuriMapApiClient()
) : AuthBootstrapEnvironmentCheck {
    override suspend fun verify(config: ManagedPolicePhoneConfig): AuthBootstrapOutcome? {
        return try {
            val response =
                apiClient.execute(
                    SuriMapApiRequest(
                        method = "GET",
                        path = "/api/health"
                    )
                )
            if (response.isSuccessful) {
                null
            } else {
                AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.InternalNetworkUnavailable)
            }
        } catch (_: SuriMapNetworkException) {
            AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.InternalNetworkUnavailable)
        }
    }
}

fun keycloakIssuerUrl(apiBaseUrl: String): String {
    BuildConfig.SURI_MAP_KEYCLOAK_ISSUER_URL
        .takeIf(String::isNotBlank)
        ?.let { return it }
    return "${apiBaseUrl.trimEnd('/').removeSuffix("/api")}/keycloak/realms/suri-map"
}
