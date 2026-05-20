package com.surimap.core.fcm

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.surimap.MainActivity
import com.surimap.R

data class IncidentClosedFcmPayload(
    val eventId: String,
    val incidentId: String,
    val closedAt: String,
    val purgeRunId: String
)

object IncidentClosedFcmRouter {
    fun payload(data: Map<String, String>): IncidentClosedFcmPayload? {
        val eventType = data["type"] ?: data["eventType"] ?: return null
        if (eventType != "INCIDENT_CLOSED") {
            return null
        }
        val incidentId =
            data["incidentId"]?.takeIf(String::isNotBlank)
                ?: data["id"]?.takeIf(String::isNotBlank)
                ?: return null
        val closedAt = data["closedAt"]?.takeIf(String::isNotBlank) ?: return null
        val eventId = data["eventId"].orEmpty()
        return IncidentClosedFcmPayload(
            eventId = eventId,
            incidentId = incidentId,
            closedAt = closedAt,
            purgeRunId = data["purgeRunId"]?.takeIf(String::isNotBlank)
                ?: eventId.ifBlank { "incident-closed:$incidentId:$closedAt" }
        )
    }
}

object IncidentClosedSignal {
    const val Action: String = "com.surimap.INCIDENT_CLOSED"
    const val ExtraEventId: String = "eventId"
    const val ExtraIncidentId: String = "incidentId"
    const val ExtraClosedAt: String = "closedAt"
    const val ExtraPurgeRunId: String = "purgeRunId"

    fun intent(packageName: String, payload: IncidentClosedFcmPayload): Intent =
        Intent(Action)
            .setPackage(packageName)
            .putExtra(ExtraEventId, payload.eventId)
            .putExtra(ExtraIncidentId, payload.incidentId)
            .putExtra(ExtraClosedAt, payload.closedAt)
            .putExtra(ExtraPurgeRunId, payload.purgeRunId)
}

object IncidentClosedNotification {
    private const val ChannelId = "incident_closed"
    private const val NotificationId = 11203

    fun show(context: Context, payload: IncidentClosedFcmPayload) {
        if (!context.canPostNotifications()) {
            return
        }
        ensureChannel(context)
        showNotification(context, payload)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(ChannelId) != null) {
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                context.getString(R.string.notification_incident_closed_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(context: Context, payload: IncidentClosedFcmPayload) {
        val openAppIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(IncidentClosedSignal.ExtraEventId, payload.eventId)
                putExtra(IncidentClosedSignal.ExtraIncidentId, payload.incidentId)
                putExtra(IncidentClosedSignal.ExtraClosedAt, payload.closedAt)
                putExtra(IncidentClosedSignal.ExtraPurgeRunId, payload.purgeRunId)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                NotificationId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        val notification =
            NotificationCompat.Builder(context, ChannelId)
                .setSmallIcon(R.drawable.ic_stat_suri_assignment)
                .setContentTitle(context.getString(R.string.notification_incident_closed_title))
                .setContentText(context.getString(R.string.notification_incident_closed_text))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

        NotificationManagerCompat.from(context).notify(NotificationId, notification)
    }

    private fun Context.canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
