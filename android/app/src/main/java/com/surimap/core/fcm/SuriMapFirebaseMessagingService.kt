package com.surimap.core.fcm

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SuriMapFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        SharedPreferencesFcmRegistrationStateStore(applicationContext).savePendingToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val refresh = IncidentAssignmentFcmRouter.refreshPayload(message.data) ?: return
        sendBroadcast(
            Intent(IncidentAssignmentRefreshSignal.Action)
                .setPackage(packageName)
                .putExtra(IncidentAssignmentRefreshSignal.ExtraEventType, refresh.eventType)
                .putExtra(IncidentAssignmentRefreshSignal.ExtraIncidentId, refresh.incidentId)
        )
    }
}
