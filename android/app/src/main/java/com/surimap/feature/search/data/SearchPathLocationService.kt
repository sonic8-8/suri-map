package com.surimap.feature.search.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.surimap.MainActivity
import com.surimap.R
import com.surimap.SuriMapApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SearchPathLocationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent?.toStartCommand()
        if (command == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!hasLocationPermission()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val application = application as SuriMapApplication
        application.configureSearchPathRecording(command.apiBaseUrl)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            recordingNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        serviceScope.launch {
            application.startSearchPathLocationRecording(command.context, command.searchPathId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val application = application as? SuriMapApplication
        if (application != null) {
            serviceScope.launch { application.stopSearchPathLocationRecording() }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) {
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_search_path_recording_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun recordingNotification() =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_suri_assignment)
            .setContentTitle(getString(R.string.notification_search_path_recording_title))
            .setContentText(getString(R.string.notification_search_path_recording_text))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    NOTIFICATION_ID,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun Intent.toStartCommand(): StartCommand? {
        val incidentId = getStringExtra(EXTRA_INCIDENT_ID)?.takeIf(String::isNotBlank) ?: return null
        val opId = getStringExtra(EXTRA_OP_ID)?.takeIf(String::isNotBlank) ?: return null
        val accountId = getStringExtra(EXTRA_ACCOUNT_ID)?.takeIf(String::isNotBlank) ?: return null
        val policePhoneId = getStringExtra(EXTRA_POLICE_PHONE_ID)?.takeIf(String::isNotBlank) ?: return null
        val searchPathId = getStringExtra(EXTRA_SEARCH_PATH_ID)?.takeIf(String::isNotBlank) ?: return null
        val apiBaseUrl = getStringExtra(EXTRA_API_BASE_URL)?.takeIf(String::isNotBlank) ?: return null
        return StartCommand(
            context =
            SearchPathWriteContext(
                incidentId = incidentId,
                opId = opId,
                accountId = accountId,
                policePhoneId = policePhoneId
            ),
            searchPathId = searchPathId,
            apiBaseUrl = apiBaseUrl
        )
    }

    private data class StartCommand(
        val context: SearchPathWriteContext,
        val searchPathId: String,
        val apiBaseUrl: String
    )

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "search_path_recording"
        private const val NOTIFICATION_ID = 11301
        private const val EXTRA_INCIDENT_ID = "incidentId"
        private const val EXTRA_OP_ID = "opId"
        private const val EXTRA_ACCOUNT_ID = "accountId"
        private const val EXTRA_POLICE_PHONE_ID = "policePhoneId"
        private const val EXTRA_SEARCH_PATH_ID = "searchPathId"
        private const val EXTRA_API_BASE_URL = "apiBaseUrl"

        fun start(
            context: Context,
            writeContext: SearchPathWriteContext,
            searchPathId: String,
            apiBaseUrl: String
        ) {
            ContextCompat.startForegroundService(
                context,
                startIntent(context, writeContext, searchPathId, apiBaseUrl)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SearchPathLocationService::class.java))
        }

        fun startIntent(
            context: Context,
            writeContext: SearchPathWriteContext,
            searchPathId: String,
            apiBaseUrl: String
        ): Intent =
            Intent(context, SearchPathLocationService::class.java).apply {
                putExtra(EXTRA_INCIDENT_ID, writeContext.incidentId)
                putExtra(EXTRA_OP_ID, writeContext.opId)
                putExtra(EXTRA_ACCOUNT_ID, writeContext.accountId)
                putExtra(EXTRA_POLICE_PHONE_ID, writeContext.policePhoneId)
                putExtra(EXTRA_SEARCH_PATH_ID, searchPathId)
                putExtra(EXTRA_API_BASE_URL, apiBaseUrl)
            }
    }
}
