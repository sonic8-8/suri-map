package com.surimap.core.fcm

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentAssignmentNotificationContractTest {

    @Test
    fun firebaseMessagingServiceShowsSystemNotificationBeforeRefreshBroadcast() {
        val source = File("src/main/java/com/surimap/core/fcm/SuriMapFirebaseMessagingService.kt").readText()

        assertTrue(source.contains("IncidentAssignmentNotification.show(applicationContext, refresh)"))
        assertTrue(source.indexOf("IncidentAssignmentNotification.show") < source.indexOf("sendBroadcast"))
    }

    @Test
    fun rootAppReceivesAssignmentRefreshForBootstrapAndIncidentListReload() {
        val source = File("src/main/java/com/surimap/ui/SuriMapApp.kt").readText()

        assertTrue(source.contains("IncidentAssignmentRefreshEffect(onRefresh = { assignmentRefreshNonce += 1 })"))
        assertTrue(source.contains("LaunchedEffect(retryNonce, assignmentRefreshNonce)"))
        assertTrue(source.contains("LaunchedEffect(assignmentRefreshNonce, manualRefreshNonce, incidentClosed, loader, policePhoneLabel)"))
    }
}
