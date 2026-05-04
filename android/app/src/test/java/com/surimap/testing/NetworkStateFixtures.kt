package com.surimap.testing

enum class NetworkTransport {
    NONE,
    CELLULAR,
    WIFI,
}

data class NetworkStateFixture(
    val name: String,
    val isConnected: Boolean,
    val isValidated: Boolean,
    val isMetered: Boolean,
    val transport: NetworkTransport,
)

object NetworkStateFixtures {
    val offline = NetworkStateFixture(
        name = "offline",
        isConnected = false,
        isValidated = false,
        isMetered = false,
        transport = NetworkTransport.NONE,
    )

    val restoredCellular = NetworkStateFixture(
        name = "restored-cellular",
        isConnected = true,
        isValidated = true,
        isMetered = true,
        transport = NetworkTransport.CELLULAR,
    )

    val restoredWifi = NetworkStateFixture(
        name = "restored-wifi",
        isConnected = true,
        isValidated = true,
        isMetered = false,
        transport = NetworkTransport.WIFI,
    )
}
