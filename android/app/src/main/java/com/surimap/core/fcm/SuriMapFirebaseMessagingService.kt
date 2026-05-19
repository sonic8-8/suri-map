package com.surimap.core.fcm

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SuriMapFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        SharedPreferencesFcmRegistrationStateStore(applicationContext).savePendingToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        SearchAreaBoundaryFcmRouter.payload(message.data)?.let { payload ->
            SearchAreaBoundaryAlertNotification.showRemoteExit(applicationContext, payload)
            return
        }
        val refresh = IncidentAssignmentFcmRouter.refreshPayload(message.data) ?: return
        IncidentAssignmentNotification.show(applicationContext, refresh)
        sendBroadcast(
            Intent(IncidentAssignmentRefreshSignal.Action)
                .setPackage(packageName)
                .putExtra(IncidentAssignmentRefreshSignal.ExtraEventType, refresh.eventType)
                .putExtra(IncidentAssignmentRefreshSignal.ExtraIncidentId, refresh.incidentId)
        )
    }
}
