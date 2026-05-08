package com.surimap.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

object LocalSyncRuntime {
    var outboxReplay: OutboxReplay? = null
}

class OutboxWorker(appContext: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val incidentId = inputData.getString("incidentId")
        val policePhoneId = inputData.getString("policePhoneId")
        if (incidentId != null && policePhoneId != null) {
            LocalSyncRuntime.outboxReplay?.flushPending(
                policePhoneId = policePhoneId,
                incidentId = incidentId
            )
        }
        return Result.success()
    }
}
