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
    fun runtimeMapStateCarriesInitialBoundsAndGeoJsonOverlays() {
        val state =
            MapLibreRuntimeMapState(
                initialBounds =
                MapLibreViewportBounds(
                    south = 37.5,
                    west = 126.9,
                    north = 37.62,
                    east = 127.08
                ),
                geometryOverlays =
                listOf(
                    MapLibreGeometryOverlay(
                        id = "area-overall-001",
                        kind = MapLibreGeometryOverlayKind.Overall,
                        geoJson = """{"type":"Polygon","coordinates":[[[126.9,37.5],[127.08,37.5],[127.08,37.62],[126.9,37.62],[126.9,37.5]]]}"""
                    )
                )
            )

        assertEquals("37.5,126.9,37.62,127.08", state.initialBounds!!.signature())
        assertEquals(37.62, state.initialBounds.toLatLngBounds().latitudeNorth, 0.0)
        assertEquals(126.9, state.initialBounds.toLatLngBounds().longitudeWest, 0.0)
        assertEquals("area-overall-001", state.geometryOverlays.single().id)
        assertEquals(MapLibreGeometryOverlayKind.Overall, state.geometryOverlays.single().kind)
        assertTrue(state.geometryOverlays.single().signature().contains("Overall:area-overall-001:false"))
    }

    @Test
    fun searchMapScreenUsesRuntimeMapInsteadOfMockCanvas() {
        val source = File("src/main/java/com/surimap/feature/search/ui/SearchMapScreen.kt").readText()

        assertTrue(source.contains("SuriMapLibreMap("))
        assertFalse(source.contains("MockMapCanvas("))
        assertFalse(source.contains("private fun MockMapCanvas"))
    }

    @Test
    fun runtimeMapAppliesGeoJsonSourcesLayersAndInitialCameraBounds() {
        val source = File("src/main/java/com/surimap/core/map/MapLibreRuntimeMap.kt").readText()

        assertTrue(source.contains("GeoJsonSource("))
        assertTrue(source.contains("FillLayer("))
        assertTrue(source.contains("LineLayer("))
        assertTrue(source.contains("CameraUpdateFactory.newLatLngBounds"))
    }
}
