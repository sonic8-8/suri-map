package com.surimap.feature.search.data

import com.surimap.core.location.GpsLocationFix
import com.surimap.core.location.LocationUpdates
import com.surimap.core.location.LocationUpdatesHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SearchPathLocationRecorder(
    private val locationUpdates: LocationUpdates,
    private val batchRecorder: SearchPathGpsBatchRecorder,
    private val coroutineScope: CoroutineScope,
    private val onFix: (GpsLocationFix) -> Unit = {}
) {
    private var activeRecording: ActiveRecording? = null
    private var locationUpdatesHandle: LocationUpdatesHandle? = null
    private var lastRecordJob: Job? = null
    private val recordingMutex = Mutex()

    suspend fun start(context: SearchPathWriteContext, searchPathId: String?) = recordingMutex.withLock {
        val pathId = searchPathId?.takeIf(String::isNotBlank) ?: return@withLock
        val nextRecording = ActiveRecording(context = context, searchPathId = pathId)
        if (activeRecording == nextRecording) {
            return@withLock
        }

        stopRecording()
        activeRecording = nextRecording
        locationUpdatesHandle =
            locationUpdates.start { fix ->
                if (activeRecording != nextRecording) {
                    return@start
                }
                onFix(fix)
                val previousRecordJob = lastRecordJob
                lastRecordJob = coroutineScope.launch {
                    previousRecordJob?.join()
                    batchRecorder.recordFix(context, pathId, fix)
                }
            }
    }

    suspend fun stop() = recordingMutex.withLock { stopRecording() }

    private suspend fun stopRecording() {
        val previousRecording = detach() ?: return
        val pendingRecordJob = lastRecordJob
        lastRecordJob = null
        withContext(NonCancellable) {
            finish(previousRecording, pendingRecordJob)
        }
    }

    private fun detach(): ActiveRecording? {
        val previousRecording = activeRecording ?: return null
        locationUpdatesHandle?.stop()
        locationUpdatesHandle = null
        activeRecording = null
        return previousRecording
    }

    private suspend fun finish(recording: ActiveRecording, pendingRecordJob: Job?) {
        pendingRecordJob?.join()
        batchRecorder.flushAll(recording.context, recording.searchPathId)
    }

    private data class ActiveRecording(val context: SearchPathWriteContext, val searchPathId: String)
}
