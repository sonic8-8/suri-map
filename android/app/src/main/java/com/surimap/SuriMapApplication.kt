package com.surimap

import android.app.Application
import androidx.work.WorkManager
import com.surimap.core.database.SuriMapDatabaseProvider
import com.surimap.core.location.AndroidLocationUpdates
import com.surimap.core.location.GpsLocationFix
import com.surimap.core.sync.ClockSyncState
import com.surimap.core.sync.OutboxReplayScheduler
import com.surimap.core.sync.RoomSyncClient
import com.surimap.core.sync.SchedulingSyncClient
import com.surimap.feature.search.data.SearchPathGpsBatchRecorder
import com.surimap.feature.search.data.SearchPathLocalRecorder
import com.surimap.feature.search.data.SearchPathLocationRecorder
import com.surimap.feature.search.data.SearchPathWriteContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SuriMapApplication : Application() {
    val clockSyncState = ClockSyncState()

    private val searchPathLocationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _searchPathLocationFixes = MutableSharedFlow<GpsLocationFix>(extraBufferCapacity = 1)
    val searchPathLocationFixes = _searchPathLocationFixes.asSharedFlow()

    @Volatile
    private var searchRecordingApiBaseUrl = BuildConfig.SURI_MAP_API_BASE_URL

    val searchPathLocationUpdates by lazy {
        AndroidLocationUpdates(
            context = this,
            sampleIntervalMs = BuildConfig.SURI_MAP_LOCATION_SAMPLE_INTERVAL_MS
        )
    }

    val searchPathRecorder by lazy {
        val database = SuriMapDatabaseProvider.database(this)
        val replayScheduler = OutboxReplayScheduler(WorkManager.getInstance(this))
        SearchPathLocalRecorder(
            syncClient =
            SchedulingSyncClient(
                delegate = RoomSyncClient(database),
                scheduleReplay = replayScheduler::schedule,
                apiBaseUrl = { searchRecordingApiBaseUrl }
            ),
            clockOffsetMs = clockSyncState::clockOffsetMs,
            clockSyncedAt = clockSyncState::clockSyncedAt
        )
    }

    val searchPathLocationRecorder by lazy {
        SearchPathLocationRecorder(
            locationUpdates = searchPathLocationUpdates,
            batchRecorder = SearchPathGpsBatchRecorder(searchPathRecorder),
            coroutineScope = searchPathLocationScope,
            onFix = _searchPathLocationFixes::tryEmit
        )
    }

    fun configureSearchPathRecording(apiBaseUrl: String) {
        searchRecordingApiBaseUrl =
            apiBaseUrl
                .trim()
                .takeIf(String::isNotBlank)
                ?: BuildConfig.SURI_MAP_API_BASE_URL
    }

    suspend fun startSearchPathLocationRecording(
        context: SearchPathWriteContext,
        searchPathId: String
    ) {
        searchPathLocationRecorder.start(context, searchPathId)
    }

    suspend fun stopSearchPathLocationRecording() {
        searchPathLocationRecorder.stop()
    }

    override fun onTerminate() {
        searchPathLocationScope.cancel()
        super.onTerminate()
    }
}
