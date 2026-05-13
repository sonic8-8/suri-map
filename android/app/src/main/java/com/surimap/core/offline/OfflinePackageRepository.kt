package com.surimap.core.offline

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.NoAccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.network.SuriMapApiRequest
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.sync.DependencyGroup
import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.SyncClient
import com.surimap.core.sync.canonicalBodyHash
import com.surimap.core.sync.jsonArray
import com.surimap.core.sync.jsonBoolean
import com.surimap.core.sync.jsonInstant
import com.surimap.core.sync.jsonNumber
import com.surimap.core.sync.jsonObject
import com.surimap.core.sync.jsonString
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

data class OfflinePackageManifestQuery(
    val incidentId: String,
    val policePhoneId: String? = null,
    val knownManifestRevision: Long? = null
)

data class OfflinePackageInstallationCommand(
    val operationId: String,
    val incidentId: String,
    val policePhoneId: String,
    val idempotencyKey: String,
    val sequence: Long,
    val installationId: String? = null,
    val manifestId: String,
    val manifestVersion: Int,
    val status: String,
    val totalItems: Int,
    val completedItems: Int,
    val failedItems: Int,
    val version: Long,
    val readyForOfflineUse: Boolean? = null,
    val failedItemKeys: List<String>? = null,
    val lastError: String? = null,
    val clientTs: Instant,
    val clockOffsetMs: Long? = null,
    val clockSyncedAt: Instant? = null
)

class OfflinePackageRepository(
    private val syncClient: SyncClient? = null,
    private val apiClient: SuriMapApiClient = SuriMapApiClient(),
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
) {
    suspend fun manifest(query: OfflinePackageManifestQuery): SuriMapApiResponse {
        return apiClient.execute(
            SuriMapApiRequest(
                method = "GET",
                path = manifestPath(query),
                accessToken = accessTokenProvider.accessToken(),
                policePhoneId = query.policePhoneId
            )
        )
    }

    suspend fun reportInstallation(command: OfflinePackageInstallationCommand): EnqueueResult {
        val installationEntityId = command.installationId
            ?: offlinePackageInstallationId(
                incidentId = command.incidentId,
                policePhoneId = command.policePhoneId,
                manifestId = command.manifestId
            )
        val payload = jsonObject(
            "policePhoneId" to jsonString(command.policePhoneId),
            "manifestId" to jsonString(command.manifestId),
            "manifestVersion" to jsonNumber(command.manifestVersion),
            "status" to jsonString(command.status),
            "totalItems" to jsonNumber(command.totalItems),
            "completedItems" to jsonNumber(command.completedItems),
            "failedItems" to jsonNumber(command.failedItems),
            "version" to jsonNumber(command.version),
            "clientTs" to jsonInstant(command.clientTs),
            "readyForOfflineUse" to command.readyForOfflineUse?.let(::jsonBoolean),
            "failedItemKeys" to command.failedItemKeys?.let { keys ->
                jsonArray(keys.map(::jsonString))
            },
            "lastError" to command.lastError?.let(::jsonString),
            "sequence" to jsonNumber(command.sequence),
            "clockOffsetMs" to command.clockOffsetMs?.let(::jsonNumber)
        )
        val operation = LocalWriteOperation(
            operationId = command.operationId,
            incidentId = command.incidentId,
            policePhoneId = command.policePhoneId,
            dependencyGroup = DependencyGroup.PACKAGE_INSTALLATION,
            sequence = command.sequence,
            method = "POST",
            endpoint = "/api/incidents/${encodePathSegment(command.incidentId)}/offline-package/installations",
            payload = payload,
            bodyHash = canonicalBodyHash(payload),
            idempotencyKey = command.idempotencyKey,
            clientTs = command.clientTs,
            clockOffsetMs = command.clockOffsetMs,
            clockSyncedAt = command.clockSyncedAt,
            entityId = installationEntityId,
            entityType = "offline_package_installation"
        )
        return requireNotNull(syncClient) { "SyncClient is required for package installation writes" }
            .enqueue(operation)
    }

    private fun manifestPath(query: OfflinePackageManifestQuery): String {
        val queryPairs = listOfNotNull(
            query.policePhoneId?.let { "policePhoneId" to it },
            query.knownManifestRevision?.let { "knownManifestRevision" to it.toString() }
        )
        val queryString = queryPairs
            .takeIf(List<Pair<String, String>>::isNotEmpty)
            ?.joinToString("&") { (key, value) -> "${encodeQueryValue(key)}=${encodeQueryValue(value)}" }
            ?.let { "?$it" }
            .orEmpty()
        return "/api/incidents/${encodePathSegment(query.incidentId)}/offline-package/manifest$queryString"
    }
}

private fun encodePathSegment(value: String): String = encodeQueryValue(value).replace("+", "%20")

private fun encodeQueryValue(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

private fun offlinePackageInstallationId(
    incidentId: String,
    policePhoneId: String,
    manifestId: String
): String =
    UUID.nameUUIDFromBytes(
        "offline-package-installation:$incidentId:$policePhoneId:$manifestId"
            .toByteArray(StandardCharsets.UTF_8)
    ).toString()
