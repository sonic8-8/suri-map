package com.surimap.core.map

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLibreRuntimeMapContractTest {

    @Test
    fun runtimeMapStateUsesCanonicalLocalStyleAndAppHeaders() {
        val state =
            MapLibreRuntimeMapState(
                apiBaseUrl = "https://suri-map.example.com/api",
                styleId = "osm-local",
                accessToken = "token-1",
                policePhoneId = "phone-1"
            )

        val source = state.tileSourceConfig()

        assertEquals("https://suri-map.example.com/tiles/styles/osm-local.json", source.styleUrl)
        assertEquals("APP", source.requestHeaders["X-Client-Channel"])
        assertEquals("Bearer token-1", source.requestHeaders["Authorization"])
        assertEquals("phone-1", source.requestHeaders["X-PolicePhone-Id"])
    }

    @Test
    fun searchMapScreenUsesRuntimeMapInsteadOfMockCanvas() {
        val source = File("src/main/java/com/surimap/feature/search/ui/SearchMapScreen.kt").readText()

        assertTrue(source.contains("SuriMapLibreMap("))
        assertFalse(source.contains("MockMapCanvas("))
        assertFalse(source.contains("private fun MockMapCanvas"))
    }
}
