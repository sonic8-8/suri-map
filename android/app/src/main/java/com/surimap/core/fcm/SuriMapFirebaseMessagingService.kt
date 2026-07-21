package com.surimap.core.fcm

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.surimap.BuildConfig
import com.surimap.core.auth.OidcAccessTokenProvider
import com.surimap.core.database.SuriMapDatabaseProvider
import com.surimap.core.network.AndroidNetworkFactory
import com.surimap.core.sync.LocalSyncPurgeHookAdapter
import com.surimap.core.sync.RoomOutboxReplay
import com.surimap.ui.navigation.accessTokenProvider
import com.surimap.ui.session.SuriMapSessionSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SuriMapFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        SharedPreferencesFcmRegistrationStateStore(applicationContext).savePendingToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        SearchAreaBoundaryFcmRouter.payload(message.data)?.let { payload ->
            SearchAreaBoundaryAlertNotification.showRemoteExit(applicationContext, payload)
            return
        }
        MarkerAlertFcmRouter.payload(message.data)?.let { payload ->
            if (MarkerAlertNotification.show(applicationContext, payload)) {
                sendBroadcast(MarkerAlertSignal.intent(packageName, payload))
            }
            return
        }
        IncidentClosedFcmRouter.payload(message.data)?.let { payload ->
            purgeIncidentLocalSync(payload)
            IncidentClosedNotification.show(applicationContext, payload)
            sendBroadcast(IncidentClosedSignal.intent(packageName, payload))
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

    private fun purgeIncidentLocalSync(payload: IncidentClosedFcmPayload) {
        serviceScope.launch {
            val snapshot = SuriMapSessionSnapshotStore(applicationContext).load() ?: return@launch
            val policePhoneContext =
                snapshot.toPolicePhoneContext(accessToken = OidcAccessTokenProvider(applicationContext).accessToken())
                    ?: return@launch
            val database = SuriMapDatabaseProvider.database(applicationContext)
            val outboxReplay =
                RoomOutboxReplay(
                    outboxDao = database.outboxDao(),
                    sender =
                    AndroidNetworkFactory.createOutboxSender(
                        baseUrl = policePhoneContext.apiBaseUrl,
                        accessTokenProvider = policePhoneContext.accessTokenProvider()
                    ),
                    accessRepairAvailable = { !policePhoneContext.accessToken.isNullOrBlank() },
                    enableRetryJitter = !BuildConfig.DEBUG
                )
            LocalSyncPurgeHookAdapter(
                outboxDao = database.outboxDao(),
                localWriteDraftDao = database.localWriteDraftDao(),
                searchRecordingStateDao = database.searchRecordingStateDao(),
                closeDrainReplay = outboxReplay
            ).handleIncidentClosed(
                incidentId = payload.incidentId,
                policePhoneId = policePhoneContext.policePhoneId,
                closedAt = payload.closedAt,
                purgeRunId = payload.purgeRunId
            )
        }
    }
}
