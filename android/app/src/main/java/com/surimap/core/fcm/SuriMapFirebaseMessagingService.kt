package com.surimap.core.fcm

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SuriMapFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        SharedPreferencesFcmRegistrationStateStore(applicationContext).savePendingToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val eventType = message.data["type"] ?: message.data["eventType"] ?: return
        if (eventType != "INCIDENT_CREATED" && eventType != "INCIDENT_ASSIGNMENT_CHANGED") {
            return
        }
        val incidentId = message.data["incidentId"].orEmpty()
        sendBroadcast(
            Intent(IncidentAssignmentRefreshSignal.Action)
                .setPackage(packageName)
                .putExtra(IncidentAssignmentRefreshSignal.ExtraEventType, eventType)
                .putExtra(IncidentAssignmentRefreshSignal.ExtraIncidentId, incidentId)
        )
    }
}
