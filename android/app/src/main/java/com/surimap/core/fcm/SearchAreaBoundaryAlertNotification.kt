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
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.surimap.MainActivity
import com.surimap.R

data class SearchAreaBoundaryFcmPayload(
    val eventId: String,
    val incidentId: String,
    val searchAreaId: String,
    val policePhoneId: String
)

object SearchAreaBoundaryFcmRouter {
    fun payload(data: Map<String, String>): SearchAreaBoundaryFcmPayload? {
        val eventType = data["type"] ?: data["eventType"] ?: return null
        if (eventType != "SEARCH_AREA_BOUNDARY_EXITED") {
            return null
        }
        return SearchAreaBoundaryFcmPayload(
            eventId = data["eventId"].orEmpty(),
            incidentId = data["incidentId"].orEmpty(),
            searchAreaId = data["searchAreaId"].orEmpty(),
            policePhoneId = data["policePhoneId"].orEmpty()
        )
    }
}

object SearchAreaBoundaryAlertNotification {
    private const val ChannelId = "search_area_boundary"
    private const val NotificationId = 11202
    private const val PreferenceName = "search_area_boundary_alerts"
    private const val RecentLocalWindowMs = 120_000L

    fun showLocalExit(context: Context, searchAreaLabel: String, searchAreaId: String? = null) {
        markLocalExit(context, searchAreaId)
        vibrate(context)
        if (!context.canPostNotifications()) {
            return
        }
        ensureChannel(context)
        showNotification(
            context = context,
            title = context.getString(R.string.notification_search_area_boundary_title),
            text = context.getString(R.string.notification_search_area_boundary_text, searchAreaLabel)
        )
    }

    fun showRemoteExit(context: Context, payload: SearchAreaBoundaryFcmPayload) {
        if (payload.eventId.isNotBlank() && wasRemoteEventSeen(context, payload.eventId)) {
            return
        }
        if (recentLocalExitExists(context, payload.searchAreaId)) {
            markRemoteEventSeen(context, payload.eventId)
            return
        }
        markRemoteEventSeen(context, payload.eventId)
        vibrate(context)
        if (!context.canPostNotifications()) {
            return
        }
        ensureChannel(context)
        showNotification(
            context = context,
            title = context.getString(R.string.notification_search_area_boundary_title),
            text = context.getString(R.string.notification_search_area_boundary_remote_text)
        )
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
                context.getString(R.string.notification_search_area_boundary_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(context: Context, title: String, text: String) {
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                NotificationId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        val notification =
            NotificationCompat.Builder(context, ChannelId)
                .setSmallIcon(R.drawable.ic_stat_suri_assignment)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

        NotificationManagerCompat.from(context).notify(NotificationId, notification)
    }

    private fun markLocalExit(context: Context, searchAreaId: String?) {
        val id = searchAreaId?.takeIf(String::isNotBlank) ?: return
        preferences(context)
            .edit()
            .putLong(localExitKey(id), System.currentTimeMillis())
            .apply()
    }

    private fun recentLocalExitExists(context: Context, searchAreaId: String): Boolean {
        val id = searchAreaId.takeIf(String::isNotBlank) ?: return false
        val markedAt = preferences(context).getLong(localExitKey(id), 0L)
        if (markedAt <= 0L) {
            return false
        }
        return System.currentTimeMillis() - markedAt <= RecentLocalWindowMs
    }

    private fun wasRemoteEventSeen(context: Context, eventId: String): Boolean =
        preferences(context).getBoolean(remoteEventKey(eventId), false)

    private fun markRemoteEventSeen(context: Context, eventId: String) {
        if (eventId.isBlank()) {
            return
        }
        preferences(context).edit().putBoolean(remoteEventKey(eventId), true).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)

    private fun localExitKey(searchAreaId: String): String = "local_exit_at_$searchAreaId"

    private fun remoteEventKey(eventId: String): String = "remote_event_$eventId"

    private fun vibrate(context: Context) {
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(android.os.Vibrator::class.java)
            } ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(250L, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun Context.canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
