package com.surimap.testing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStateFixturesTest {
    @Test
    fun offlineFixtureRepresentsDisconnectedState() {
        assertFalse(NetworkStateFixtures.offline.isConnected)
        assertFalse(NetworkStateFixtures.offline.isValidated)
    }

    @Test
    fun restoredFixtureRepresentsReplayEligibleState() {
        assertTrue(NetworkStateFixtures.restoredCellular.isConnected)
        assertTrue(NetworkStateFixtures.restoredCellular.isValidated)
    }
}
