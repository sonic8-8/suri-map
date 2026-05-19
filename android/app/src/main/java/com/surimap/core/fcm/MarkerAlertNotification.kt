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

data class MarkerAlertFcmPayload(
    val eventId: String,
    val eventType: String,
    val incidentId: String,
    val markerId: String,
    val markerType: String,
    val locationLabel: String?
)

object MarkerAlertFcmRouter {
    fun payload(data: Map<String, String>): MarkerAlertFcmPayload? {
        val eventType = data["type"] ?: data["eventType"] ?: return null
        if (eventType != "PERSON_FOUND" && eventType != "SUPPORT_REQUEST_CREATED") {
            return null
        }
        val markerId = data["markerId"]?.takeIf(String::isNotBlank) ?: return null
        return MarkerAlertFcmPayload(
            eventId = data["eventId"].orEmpty(),
            eventType = eventType,
            incidentId = data["incidentId"].orEmpty(),
            markerId = markerId,
            markerType = data["markerType"].orEmpty(),
            locationLabel = data["locationLabel"]?.takeIf(String::isNotBlank)
        )
    }
}

object MarkerAlertSignal {
    const val Action: String = "com.surimap.MARKER_ALERT"
    const val ExtraEventId: String = "eventId"
    const val ExtraEventType: String = "eventType"
    const val ExtraIncidentId: String = "incidentId"
    const val ExtraMarkerId: String = "markerId"
    const val ExtraMarkerType: String = "markerType"
    const val ExtraLocationLabel: String = "locationLabel"

    fun intent(packageName: String, payload: MarkerAlertFcmPayload): Intent =
        Intent(Action)
            .setPackage(packageName)
            .putExtra(ExtraEventId, payload.eventId)
            .putExtra(ExtraEventType, payload.eventType)
            .putExtra(ExtraIncidentId, payload.incidentId)
            .putExtra(ExtraMarkerId, payload.markerId)
            .putExtra(ExtraMarkerType, payload.markerType)
            .putExtra(ExtraLocationLabel, payload.locationLabel)
}

object MarkerAlertNotification {
    private const val ChannelId = "marker_alert"
    private const val BaseNotificationId = 11300
    private const val PreferenceName = "marker_alert_notifications"

    fun show(context: Context, payload: MarkerAlertFcmPayload): Boolean {
        val key = payload.notificationKey()
        if (wasSeen(context, key)) {
            return false
        }
        markSeen(context, key)
        if (!context.canPostNotifications()) {
            return true
        }
        ensureChannel(context)
        showNotification(context, payload)
        return true
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
                context.getString(R.string.notification_marker_alert_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(context: Context, payload: MarkerAlertFcmPayload) {
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId(payload),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MarkerAlertSignal.ExtraEventId, payload.eventId)
                    putExtra(MarkerAlertSignal.ExtraEventType, payload.eventType)
                    putExtra(MarkerAlertSignal.ExtraIncidentId, payload.incidentId)
                    putExtra(MarkerAlertSignal.ExtraMarkerId, payload.markerId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        val notification =
            NotificationCompat.Builder(context, ChannelId)
                .setSmallIcon(R.drawable.ic_stat_suri_assignment)
                .setContentTitle(context.getString(payload.titleRes()))
                .setContentText(context.getString(payload.textRes()))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

        NotificationManagerCompat.from(context).notify(notificationId(payload), notification)
    }

    private fun wasSeen(context: Context, key: String): Boolean =
        preferences(context).getBoolean(key, false)

    private fun markSeen(context: Context, key: String) {
        preferences(context).edit().putBoolean(key, true).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)

    private fun MarkerAlertFcmPayload.notificationKey(): String =
        eventId.takeIf(String::isNotBlank) ?: "$eventType:$markerId"

    private fun notificationId(payload: MarkerAlertFcmPayload): Int =
        BaseNotificationId + (payload.notificationKey().hashCode() and 0x3ff)

    private fun MarkerAlertFcmPayload.titleRes(): Int =
        if (eventType == "PERSON_FOUND") {
            R.string.notification_marker_alert_person_found_title
        } else {
            R.string.notification_marker_alert_support_request_title
        }

    private fun MarkerAlertFcmPayload.textRes(): Int =
        if (eventType == "PERSON_FOUND") {
            R.string.notification_marker_alert_person_found_text
        } else {
            R.string.notification_marker_alert_support_request_text
        }

    private fun Context.canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
