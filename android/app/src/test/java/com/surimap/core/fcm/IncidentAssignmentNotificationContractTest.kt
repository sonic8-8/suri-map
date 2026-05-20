package com.surimap.core.fcm

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentAssignmentNotificationContractTest {

    @Test
    fun firebaseMessagingServiceShowsSystemNotificationBeforeRefreshBroadcast() {
        val source = File("src/main/java/com/surimap/core/fcm/SuriMapFirebaseMessagingService.kt").readText()
        val notificationIndex = source.indexOf("IncidentAssignmentNotification.show")
        val refreshBroadcastIndex = source.indexOf("Intent(IncidentAssignmentRefreshSignal.Action)")

        assertTrue(source.contains("IncidentAssignmentNotification.show(applicationContext, refresh)"))
        assertTrue(notificationIndex < refreshBroadcastIndex)
    }

    @Test
    fun rootAppReceivesAssignmentRefreshForBootstrapAndIncidentListReload() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertTrue(source.contains("IncidentAssignmentRefreshEffect(onRefresh = { assignmentRefreshNonce += 1 })"))
        assertTrue(source.contains("LaunchedEffect(retryNonce, assignmentRefreshNonce)"))
        assertTrue(source.contains("LaunchedEffect(assignmentRefreshNonce, manualRefreshNonce, incidentClosed, loader, policePhoneLabel)"))
    }

    @Test
    fun firebaseMessagingServiceRoutesMarkerAlertsBeforeAssignmentRefreshFallback() {
        val source = File("src/main/java/com/surimap/core/fcm/SuriMapFirebaseMessagingService.kt").readText()

        assertTrue(source.contains("MarkerAlertFcmRouter.payload(message.data)"))
        assertTrue(source.contains("MarkerAlertNotification.show(applicationContext, payload)"))
        assertTrue(source.contains("sendBroadcast(MarkerAlertSignal.intent(packageName, payload))"))
        assertTrue(source.indexOf("MarkerAlertFcmRouter.payload") < source.indexOf("IncidentAssignmentFcmRouter.refreshPayload"))
    }

    @Test
    fun firebaseMessagingServiceRoutesIncidentClosedBeforeAssignmentRefreshFallback() {
        val source = File("src/main/java/com/surimap/core/fcm/SuriMapFirebaseMessagingService.kt").readText()

        assertTrue(source.contains("IncidentClosedFcmRouter.payload(message.data)"))
        assertTrue(source.contains("purgeIncidentLocalSync(payload)"))
        assertTrue(source.contains("IncidentClosedNotification.show(applicationContext, payload)"))
        assertTrue(source.contains("sendBroadcast(IncidentClosedSignal.intent(packageName, payload))"))
        assertTrue(source.indexOf("IncidentClosedFcmRouter.payload") < source.indexOf("IncidentAssignmentFcmRouter.refreshPayload"))
    }

    @Test
    fun rootAppReceivesMarkerAlertBroadcastAsInAppBanner() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()
        val overlaySource = File("src/main/java/com/surimap/ui/AppOverlayHost.kt").readText()

        assertTrue(source.contains("MarkerAlertEffect(onAlert = { markerAlert = it })"))
        assertTrue(source.contains("IntentFilter(MarkerAlertSignal.Action)"))
        assertTrue(source.contains("markerAlert = markerAlert"))
        assertTrue(overlaySource.contains("IncidentAlertBanner("))
    }

    @Test
    fun rootAppReceivesIncidentClosedBroadcastAndRunsLocalPurge() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertTrue(source.contains("IncidentClosedEffect("))
        assertTrue(source.contains("IntentFilter(IncidentClosedSignal.Action)"))
        assertTrue(source.contains("createIncidentClosedPurgeHook(context, phoneContext)"))
        assertTrue(source.contains(".handleIncidentClosed("))
        assertTrue(source.contains("IncidentClosedOverlayState("))
    }
}
