package com.surimap.core.offline

import android.content.Context
import com.surimap.core.database.SuriMapDatabase
import com.surimap.core.database.SuriMapDatabaseProvider
import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.NoAccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.core.sync.RoomSyncClient
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

class RoomOfflinePackageWorkerInstaller(
    private val database: SuriMapDatabase,
    private val repository: OfflinePackageRepository,
    private val fetchBytes: suspend (OfflinePackageDownloadItem) -> ByteArray,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : OfflinePackageWorkerInstaller {

    override suspend fun install(request: OfflinePackageWorkerInstallRequest) {
        val manifest =
            repository.manifest(
                OfflinePackageManifestQuery(
                    incidentId = request.incidentId,
                    policePhoneId = request.policePhoneId
                )
            )
        if (!manifest.isSuccessful) {
            return
        }
        val plan = OfflinePackageDownloadPlan.fromManifestJson(manifest.body) ?: return
        if (plan.manifestId != request.manifestId) {
            return
        }

        val installer =
            OfflinePackageItemInstaller(
                fetchBytes = fetchBytes,
                persistItemStatuses = {},
                reportInstallationProgress = {}
            )
        val statuses =
            installer.install(plan.installCommand(request.incidentId, request.policePhoneId))
        val issuedAt = nowMillis()
        val progressCommand = progressCommand(request, plan, statuses, issuedAt)
        persistProgress(progressCommand, statuses, issuedAt)
        reportProgress(progressCommand)
    }

    private suspend fun persistProgress(
        command: OfflinePackageInstallationProgressCommand,
        statuses: List<OfflinePackageItemStatus>,
        updatedAt: Long
    ) {
        database.offlinePackageItemStatusDao().upsertAll(
            statuses.map { status -> status.toOfflinePackageItemStatusEntity(updatedAt) }
        )
        database.offlinePackageInstallationDao().upsert(
            command.toOfflinePackageInstallationEntity(
                aggregate = OfflinePackageInstallationProgressReporter.aggregate(statuses),
                updatedAt = updatedAt
            )
        )
    }

    private suspend fun reportProgress(command: OfflinePackageInstallationProgressCommand) {
        OfflinePackageInstallationProgressReporter(repository).report(command)
    }

    private fun progressCommand(
        request: OfflinePackageWorkerInstallRequest,
        plan: OfflinePackageDownloadPlan,
        statuses: List<OfflinePackageItemStatus>,
        issuedAt: Long
    ): OfflinePackageInstallationProgressCommand {
        val clientTs = Instant.ofEpochMilli(issuedAt)
        val operationId =
            "offline-package-installation:${request.incidentId}:${request.policePhoneId}:${plan.manifestId}:$issuedAt"
        return OfflinePackageInstallationProgressCommand(
            operationId = operationId,
            incidentId = request.incidentId,
            policePhoneId = request.policePhoneId,
            idempotencyKey = "$operationId:idempotency",
            sequence = issuedAt,
            manifestId = plan.manifestId,
            manifestVersion = plan.manifestVersion,
            version = issuedAt,
            clientTs = clientTs,
            clockOffsetMs = 0L,
            clockSyncedAt = clientTs,
            items = statuses
        )
    }

    companion object {
        fun fromContext(
            context: Context,
            apiBaseUrl: String,
            accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider
        ): RoomOfflinePackageWorkerInstaller {
            val database = SuriMapDatabaseProvider.database(context)
            val fetcher =
                OfflinePackageHttpByteFetcher(
                    apiBaseUrl = apiBaseUrl,
                    accessTokenProvider = accessTokenProvider
                )
            return RoomOfflinePackageWorkerInstaller(
                database = database,
                repository =
                OfflinePackageRepository(
                    syncClient = RoomSyncClient(database.outboxDao(), database.localWriteDraftDao()),
                    apiClient = SuriMapApiClient(baseUrl = apiBaseUrl),
                    accessTokenProvider = accessTokenProvider
                ),
                fetchBytes = fetcher::fetch
            )
        }
    }
}

class OfflinePackageHttpByteFetcher(
    private val apiBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider = NoAccessTokenProvider,
    private val callFactory: Call.Factory = OkHttpClient()
) {
    suspend fun fetch(item: OfflinePackageDownloadItem): ByteArray = withContext(Dispatchers.IO) {
        val downloadUrl = item.downloadUrl?.takeIf(String::isNotBlank)
            ?: throw IOException("download_url_required")
        val request =
            Request.Builder()
                .url(resolveDownloadUrl(downloadUrl))
                .header("X-Client-Channel", "APP")
        accessTokenProvider.accessToken()
            ?.takeIf(String::isNotBlank)
            ?.let { token ->
                request.header(
                    "Authorization",
                    if (token.startsWith("Bearer ")) token else "Bearer $token"
                )
            }
        val response = callFactory.newCall(request.build()).execute()
        response.use {
            if (!it.isSuccessful) {
                throw IOException("tile_unavailable")
            }
            it.body.bytes()
        }
    }

    private fun resolveDownloadUrl(downloadUrl: String): String {
        if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
            return downloadUrl
        }
        val tileBaseUrl = apiBaseUrl.trimEnd('/').removeSuffix("/api")
        val path = if (downloadUrl.startsWith("/")) downloadUrl else "/$downloadUrl"
        return tileBaseUrl + path
    }
}
