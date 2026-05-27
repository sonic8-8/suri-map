package com.surimap.feature.search.data

import com.surimap.core.database.SearchMapResponseCacheDao
import com.surimap.core.database.SearchMapResponseCacheEntity
import java.security.MessageDigest

interface SearchMapResponseCache {
    suspend fun read(
        context: SearchMapSessionContext,
        source: String
    ): String?

    suspend fun revisions(context: SearchMapSessionContext): Map<String, String>

    suspend fun upsertIfChanged(
        context: SearchMapSessionContext,
        source: String,
        body: String,
        sourceRevision: String? = null
    )
}

class RoomSearchMapResponseCache(
    private val dao: SearchMapResponseCacheDao,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : SearchMapResponseCache {
    override suspend fun read(
        context: SearchMapSessionContext,
        source: String
    ): String? {
        val key = context.cacheKeyOrNull() ?: return null
        return dao.find(
            incidentId = key.incidentId,
            opId = key.opId,
            policePhoneId = key.policePhoneId,
            source = source
        )?.bodyJson
    }

    override suspend fun revisions(context: SearchMapSessionContext): Map<String, String> {
        val key = context.cacheKeyOrNull() ?: return emptyMap()
        return dao.findByContext(
            incidentId = key.incidentId,
            opId = key.opId,
            policePhoneId = key.policePhoneId
        ).mapNotNull { entity ->
            entity.sourceRevision
                .takeIf(String::isNotBlank)
                ?.let { revision -> entity.source to revision }
        }.toMap()
    }

    override suspend fun upsertIfChanged(
        context: SearchMapSessionContext,
        source: String,
        body: String,
        sourceRevision: String?
    ) {
        val key = context.cacheKeyOrNull() ?: return
        val normalizedBody = body.takeIf(String::isNotBlank) ?: return
        val bodyHash = normalizedBody.sha256()
        val normalizedRevision = sourceRevision.orEmpty()
        val current =
            dao.find(
                incidentId = key.incidentId,
                opId = key.opId,
                policePhoneId = key.policePhoneId,
                source = source
            )
        if (current?.bodyHash == bodyHash && current.sourceRevision == normalizedRevision) {
            return
        }
        dao.upsert(
            SearchMapResponseCacheEntity(
                incidentId = key.incidentId,
                opId = key.opId,
                policePhoneId = key.policePhoneId,
                source = source,
                bodyHash = bodyHash,
                sourceRevision = normalizedRevision,
                bodyJson = normalizedBody,
                updatedAt = nowMillis()
            )
        )
    }
}

private data class SearchMapCacheKey(
    val incidentId: String,
    val opId: String,
    val policePhoneId: String
)

private fun SearchMapSessionContext.cacheKeyOrNull(): SearchMapCacheKey? {
    val incidentId = incidentId?.takeIf(String::isNotBlank) ?: return null
    return SearchMapCacheKey(
        incidentId = incidentId,
        opId = currentOpId.orEmpty(),
        policePhoneId = policePhoneId.orEmpty()
    )
}

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return "sha256:" + digest.joinToString("") { byte -> "%02x".format(byte) }
}
