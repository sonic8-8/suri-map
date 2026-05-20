package com.surimap.ui.session

import android.content.Context
import com.surimap.feature.search.data.SearchMapSessionContext
import com.surimap.ui.navigation.IncidentContext
import com.surimap.ui.navigation.PolicePhoneContext
import com.surimap.ui.navigation.accountIdClaim
import org.json.JSONObject

private const val PREFS_NAME = "suri_map_session_snapshot"
private const val PREFS_KEY = "snapshot"

data class SuriMapSessionSnapshot(
    val incidentId: String? = null,
    val currentOpId: String? = null,
    val currentDutyShiftId: String? = null,
    val policePhoneId: String? = null,
    val apiBaseUrl: String? = null,
    val tileBaseUrl: String? = null,
    val objectStorageBaseUrl: String? = null
) {
    fun toSearchMapSessionContext(): SearchMapSessionContext? {
        val normalizedIncidentId = incidentId?.takeIf(String::isNotBlank) ?: return null
        return SearchMapSessionContext(
            incidentId = normalizedIncidentId,
            currentOpId = currentOpId?.takeIf(String::isNotBlank),
            currentDutyShiftId = currentDutyShiftId?.takeIf(String::isNotBlank),
            policePhoneId = policePhoneId?.takeIf(String::isNotBlank)
        )
    }

    fun toPolicePhoneContext(
        accessToken: String? = null,
        accessTokenExpiresAtEpochMs: Long? = null
    ): PolicePhoneContext? {
        val normalizedPolicePhoneId = policePhoneId?.takeIf(String::isNotBlank) ?: return null
        val normalizedApiBaseUrl = apiBaseUrl?.takeIf(String::isNotBlank) ?: return null
        val normalizedTileBaseUrl = tileBaseUrl?.takeIf(String::isNotBlank) ?: normalizedApiBaseUrl
        val normalizedObjectStorageBaseUrl =
            objectStorageBaseUrl?.takeIf(String::isNotBlank) ?: normalizedApiBaseUrl
        return PolicePhoneContext(
            policePhoneId = normalizedPolicePhoneId,
            apiBaseUrl = normalizedApiBaseUrl,
            tileBaseUrl = normalizedTileBaseUrl,
            objectStorageBaseUrl = normalizedObjectStorageBaseUrl,
            accessToken = accessToken?.takeIf(String::isNotBlank),
            accessTokenExpiresAtEpochMs = accessTokenExpiresAtEpochMs,
            accountId = accessToken.accountIdClaim()
        )
    }

    fun toIncidentContext(): IncidentContext? {
        val normalizedIncidentId = incidentId?.takeIf(String::isNotBlank) ?: return null
        return IncidentContext(
            incidentId = normalizedIncidentId,
            currentOpId = currentOpId?.takeIf(String::isNotBlank),
            currentDutyShiftId = currentDutyShiftId?.takeIf(String::isNotBlank)
        )
    }

    fun isEmpty(): Boolean {
        return incidentId.isNullOrBlank() &&
            currentOpId.isNullOrBlank() &&
            currentDutyShiftId.isNullOrBlank() &&
            policePhoneId.isNullOrBlank() &&
            apiBaseUrl.isNullOrBlank() &&
            tileBaseUrl.isNullOrBlank() &&
            objectStorageBaseUrl.isNullOrBlank()
    }

    fun toJson(): String =
        JSONObject()
            .put("incidentId", incidentId)
            .put("currentOpId", currentOpId)
            .put("currentDutyShiftId", currentDutyShiftId)
            .put("policePhoneId", policePhoneId)
            .put("apiBaseUrl", apiBaseUrl)
            .put("tileBaseUrl", tileBaseUrl)
            .put("objectStorageBaseUrl", objectStorageBaseUrl)
            .toString()

    companion object {
        fun from(incidentContext: IncidentContext?, policePhoneContext: PolicePhoneContext?): SuriMapSessionSnapshot? {
            val snapshot =
                SuriMapSessionSnapshot(
                    incidentId = incidentContext?.incidentId?.takeIf(String::isNotBlank),
                    currentOpId = incidentContext?.currentOpId?.takeIf(String::isNotBlank),
                    currentDutyShiftId = incidentContext?.currentDutyShiftId?.takeIf(String::isNotBlank),
                    policePhoneId = policePhoneContext?.policePhoneId?.takeIf(String::isNotBlank),
                    apiBaseUrl = policePhoneContext?.apiBaseUrl?.takeIf(String::isNotBlank),
                    tileBaseUrl = policePhoneContext?.tileBaseUrl?.takeIf(String::isNotBlank),
                    objectStorageBaseUrl = policePhoneContext?.objectStorageBaseUrl?.takeIf(String::isNotBlank)
                )
            return snapshot.takeUnless(SuriMapSessionSnapshot::isEmpty)
        }

        fun fromJson(body: String): SuriMapSessionSnapshot? {
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            return SuriMapSessionSnapshot(
                incidentId = json.optString("incidentId").takeIf(String::isNotBlank),
                currentOpId = json.optString("currentOpId").takeIf(String::isNotBlank),
                currentDutyShiftId = json.optString("currentDutyShiftId").takeIf(String::isNotBlank),
                policePhoneId = json.optString("policePhoneId").takeIf(String::isNotBlank),
                apiBaseUrl = json.optString("apiBaseUrl").takeIf(String::isNotBlank),
                tileBaseUrl = json.optString("tileBaseUrl").takeIf(String::isNotBlank),
                objectStorageBaseUrl = json.optString("objectStorageBaseUrl").takeIf(String::isNotBlank)
            ).takeUnless(SuriMapSessionSnapshot::isEmpty)
        }
    }
}

class SuriMapSessionSnapshotStore(context: Context) {
    private val sharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SuriMapSessionSnapshot? {
        val body = sharedPreferences.getString(PREFS_KEY, null) ?: return null
        return SuriMapSessionSnapshot.fromJson(body)
    }

    fun save(incidentContext: IncidentContext?, policePhoneContext: PolicePhoneContext?) {
        val snapshot = SuriMapSessionSnapshot.from(incidentContext, policePhoneContext)
        if (snapshot == null) {
            clear()
            return
        }
        sharedPreferences.edit().putString(PREFS_KEY, snapshot.toJson()).apply()
    }

    fun clear() {
        sharedPreferences.edit().remove(PREFS_KEY).apply()
    }
}
