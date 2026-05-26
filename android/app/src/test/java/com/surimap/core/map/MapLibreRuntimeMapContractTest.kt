package com.surimap.core.map

import com.surimap.testing.areaIdFixture
import com.surimap.testing.markerIdFixture
import com.surimap.testing.pathIdFixture
import com.surimap.testing.policePhoneIdFixture
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
                policePhoneId = POLICE_PHONE_ID
            )

        val source = state.tileSourceConfig()

        assertEquals("https://suri-map.example.com/tiles/styles/osm-local.json", source.styleUrl)
        assertEquals("APP", source.requestHeaders["X-Client-Channel"])
        assertEquals("Bearer token-1", source.requestHeaders["Authorization"])
        assertEquals(POLICE_PHONE_ID, source.requestHeaders["X-PolicePhone-Id"])
    }

    @Test
    fun runtimeMapStateCarriesInitialBoundsAndGeoJsonOverlays() {
        val state =
            MapLibreRuntimeMapState(
                initialBounds =
                MapLibreViewportBounds(
                    south = 35.052595,
                    west = 126.647507,
                    north = 35.256837,
                    east = 127.017482
                ),
                geometryOverlays =
                listOf(
                    MapLibreGeometryOverlay(
                        id = OVERALL_AREA_ID,
                        kind = MapLibreGeometryOverlayKind.Overall,
                        geoJson = """{"type":"Polygon","coordinates":[[[126.647507,35.052595],[127.017482,35.052595],[127.017482,35.256837],[126.647507,35.256837],[126.647507,35.052595]]]}"""
                    )
                )
            )

        assertEquals("35.052595,126.647507,35.256837,127.017482", state.initialBounds!!.signature())
        assertEquals(35.256837, state.initialBounds.toLatLngBounds().latitudeNorth, 0.0)
        assertEquals(126.647507, state.initialBounds.toLatLngBounds().longitudeWest, 0.0)
        assertEquals(OVERALL_AREA_ID, state.geometryOverlays.single().id)
        assertEquals(MapLibreGeometryOverlayKind.Overall, state.geometryOverlays.single().kind)
        assertTrue(state.geometryOverlays.single().signature().contains("Overall:$OVERALL_AREA_ID:false"))
    }

    @Test
    fun pathOverlayKindIsCarriedAsHighlightedLineGeometry() {
        val overlay =
            MapLibreGeometryOverlay(
                id = PATH_ID,
                kind = MapLibreGeometryOverlayKind.Path,
                highlighted = true,
                geoJson = """{"type":"LineString","coordinates":[[126.91,37.51],[126.92,37.52]]}"""
            )
        val source = File("src/main/java/com/surimap/core/map/MapLibreRuntimeMap.kt").readText()

        assertTrue(overlay.signature().contains("Path:$PATH_ID:true"))
        assertTrue(source.contains("supportsFillLayer"))
        assertTrue(source.contains("MapLibreGeometryOverlayKind.Path -> false"))
    }

    @Test
    fun markerOverlayKindIsCarriedAsHighlightedPointGeometry() {
        val overlay =
            MapLibreGeometryOverlay(
                id = MARKER_ID,
                kind = MapLibreGeometryOverlayKind.Marker,
                highlighted = true,
                geoJson = """{"type":"Point","coordinates":[126.91,37.51]}"""
            )
        val source = File("src/main/java/com/surimap/core/map/MapLibreRuntimeMap.kt").readText()

        assertTrue(overlay.signature().contains("Marker:$MARKER_ID:true"))
        assertTrue(source.contains("SymbolLayer("))
        assertTrue(source.contains("supportsMarkerIconLayer"))
        assertTrue(source.contains("kind == MapLibreGeometryOverlayKind.Marker"))
        assertTrue(source.contains("queryRenderedFeatures(point, *markerLayerIds)"))
        assertTrue(source.contains("latestMarkerClick(markerId)"))
    }

    @Test
    fun currentLocationBearingAddsRotatedHeadingIndicatorLayer() {
        val overlay =
            MapLibreGeometryOverlay(
                id = "current-location",
                kind = MapLibreGeometryOverlayKind.CurrentLocation,
                highlighted = true,
                label = "",
                bearingDegrees = 92.0,
                geoJson = """{"type":"Point","coordinates":[126.91,37.51]}"""
            )
        val source = File("src/main/java/com/surimap/core/map/MapLibreRuntimeMap.kt").readText()

        assertTrue(overlay.signature().contains("CurrentLocation:current-location:true::92.0"))
        assertTrue(source.contains("headingLayerId"))
        assertTrue(source.contains("supportsHeadingLayer"))
        assertTrue(source.contains("CURRENT_LOCATION_IMAGE_ID"))
        assertTrue(source.contains("CURRENT_LOCATION_HEADING_IMAGE_ID"))
        assertTrue(source.contains("currentLocationBitmap("))
        assertTrue(source.contains("withHeading = false"))
        assertTrue(source.contains("withHeading = true"))
        assertTrue(source.contains("iconImage(Expression.get(\"currentLocationIcon\"))"))
        assertTrue(source.contains("iconRotate(Expression.get(\"bearingDegrees\"))"))
        assertTrue(source.contains("iconSize(CURRENT_LOCATION_ICON_SIZE)"))
        assertTrue(source.contains("CURRENT_LOCATION_ICON_SIZE = 1.35f"))
        assertTrue(source.contains("MapLibreGeometryOverlayKind.CurrentLocation -> false"))
        assertTrue(source.contains("CURRENT_LOCATION_HEADING_IMAGE_SDF = false"))
        assertTrue(source.contains("removeLayer(\"\$styleId-heading\")"))
    }

    @Test
    fun overlayPaintContractAddsReadableLabelsAndHighContrastHalo() {
        val labeledOverlay =
            MapLibreGeometryOverlay(
                id = MARKER_ID,
                kind = MapLibreGeometryOverlayKind.Marker,
                highlighted = true,
                label = "단서",
                geoJson = """{"type":"Point","coordinates":[126.91,37.51]}"""
            )
        val markerPaint = mapLibreOverlayPaint(MapLibreGeometryOverlayKind.Marker, highlighted = true)
        val pathPaint = mapLibreOverlayPaint(MapLibreGeometryOverlayKind.Path, highlighted = true)
        val source = File("src/main/java/com/surimap/core/map/MapLibreRuntimeMap.kt").readText()
        val searchMapSource = File("src/main/java/com/surimap/feature/search/ui/SearchMapScreen.kt").readText()

        assertTrue(labeledOverlay.signature().contains("Marker:$MARKER_ID:true:단서"))
        assertEquals("#FFFFFF", markerPaint.textHaloColor)
        assertTrue(markerPaint.textHaloWidth >= 1.75f)
        assertTrue(markerPaint.circleStrokeWidth >= 2.0f)
        assertTrue(pathPaint.lineWidth > mapLibreOverlayPaint(MapLibreGeometryOverlayKind.Path, highlighted = false).lineWidth)
        assertTrue(source.contains("SymbolLayer("))
        assertTrue(source.contains("textField(Expression.get(\"label\"))"))
        assertTrue(source.contains("textFont(SURI_MAP_LABEL_FONT_STACK)"))
        assertTrue(source.contains("Pretendard GOV"))
        assertTrue(source.contains("textHaloColor(paint.textHaloColor)"))
        assertTrue(source.contains("removeLayer(\"\$styleId-label\")"))
        assertTrue(searchMapSource.contains("label = layer.label"))
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

    @Test
    fun runtimeMapReportsCameraIdleViewportBounds() {
        val source = File("src/main/java/com/surimap/core/map/MapLibreRuntimeMap.kt").readText()

        assertTrue(source.contains("onViewportBoundsChanged: (MapLibreViewportBounds) -> Unit"))
        assertTrue(source.contains("addOnCameraIdleListener"))
        assertTrue(source.contains("removeOnCameraIdleListener"))
        assertTrue(source.contains("visibleRegion.latLngBounds"))
        assertTrue(source.contains("toMapLibreViewportBoundsOrNull()"))
    }

    private companion object {
        val POLICE_PHONE_ID = policePhoneIdFixture("1")
        val OVERALL_AREA_ID = areaIdFixture("overall-001")
        val PATH_ID = pathIdFixture("001")
        val MARKER_ID = markerIdFixture("clue-001")
    }
}
