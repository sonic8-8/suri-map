package com.surimap.core.sync

import com.surimap.core.network.SyncApiClient
import com.surimap.core.network.SyncClockNetworkRequest
import java.time.Instant
import java.time.OffsetDateTime
import org.json.JSONObject

data class ClockSyncSnapshot(
    val clockOffsetMs: Long?,
    val clockSyncedAt: Instant?
)

class ClockSyncState(
    private val now: () -> Instant = { Instant.now() }
) {
    @Volatile
    private var snapshot: ClockSyncSnapshot? = null

    fun clockOffsetMs(): Long? = snapshot?.clockOffsetMs ?: 0L

    fun clockSyncedAt(): Instant? {
        val current = now()
        val syncedAt = snapshot?.clockSyncedAt ?: return current
        return if (syncedAt.plusMillis(STALE_CLOCK_SYNC_AFTER_MS).isBefore(current)) {
            current
        } else {
            syncedAt
        }
    }

    fun snapshot(): ClockSyncSnapshot =
        ClockSyncSnapshot(
            clockOffsetMs = clockOffsetMs(),
            clockSyncedAt = clockSyncedAt()
        )

    suspend fun sync(
        client: SyncApiClient,
        incidentId: String?,
        policePhoneId: String?
    ): Boolean {
        val incident = incidentId?.takeIf(String::isNotBlank) ?: return false
        val phone = policePhoneId?.takeIf(String::isNotBlank) ?: return false
        val response =
            runCatching {
                client.syncClock(
                    SyncClockNetworkRequest(
                        incidentId = incident,
                        policePhoneId = phone,
                        clientTs = now().toString()
                    )
                )
            }.getOrNull()
                ?: return false
        if (!response.isSuccessful) {
            return false
        }
        return updateFromResponse(response.body)
    }

    fun updateFromResponse(body: String?): Boolean {
        val root = runCatching { JSONObject(body.orEmpty()) }.getOrNull() ?: return false
        val clockOffsetMs =
            if (root.has("clockOffsetMs")) {
                root.optLong("clockOffsetMs")
            } else {
                return false
            }
        val syncedAt =
            root.optString("clockSyncedAt")
                .ifBlank { root.optString("serverTs") }
                .toInstantOrNull()
                ?: return false
        snapshot = ClockSyncSnapshot(clockOffsetMs = clockOffsetMs, clockSyncedAt = syncedAt)
        return true
    }
}

private const val STALE_CLOCK_SYNC_AFTER_MS = 300_000L

fun String.toClockInstantOrNull(): Instant? = toInstantOrNull()

private fun String.toInstantOrNull(): Instant? {
    val value = takeIf(String::isNotBlank) ?: return null
    return runCatching { Instant.parse(value) }
        .getOrElse {
            runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        }
}
