package com.surimap.core.fcm

import com.google.firebase.messaging.FirebaseMessagingService

class SuriMapFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        SharedPreferencesFcmRegistrationStateStore(applicationContext).savePendingToken(token)
    }
}
