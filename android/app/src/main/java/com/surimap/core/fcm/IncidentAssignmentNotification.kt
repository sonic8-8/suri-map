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

object IncidentAssignmentNotification {
    private const val ChannelId = "incident_assignment"
    private const val NotificationId = 11201

    fun show(context: Context, refresh: IncidentAssignmentFcmRefresh) {
        if (!context.canPostNotifications()) {
            return
        }
        ensureChannel(context)
        showNotification(context, refresh)
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
                context.getString(R.string.notification_incident_assignment_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(context: Context, refresh: IncidentAssignmentFcmRefresh) {
        val openAppIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(IncidentAssignmentRefreshSignal.ExtraEventType, refresh.eventType)
                putExtra(IncidentAssignmentRefreshSignal.ExtraIncidentId, refresh.incidentId)
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
                .setContentTitle(context.getString(R.string.notification_incident_assignment_title))
                .setContentText(context.getString(R.string.notification_incident_assignment_text))
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
