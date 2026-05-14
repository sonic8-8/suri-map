package com.surimap.core.map

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.surimap.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.net.ConnectivityReceiver
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
import org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
import org.maplibre.android.style.layers.Property.SYMBOL_PLACEMENT_LINE
import org.maplibre.android.style.layers.Property.SYMBOL_PLACEMENT_POINT
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolPlacement
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloBlur
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.sources.GeoJsonSource

data class MapLibreViewportBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    fun signature(): String = "$south,$west,$north,$east"

    fun toLatLngBounds(): LatLngBounds = LatLngBounds.from(north, east, south, west)
}

enum class MapLibreGeometryOverlayKind {
    Overall,
    Unit,
    Team,
    Path,
    Marker
}

data class MapLibreGeometryOverlay(
    val id: String,
    val kind: MapLibreGeometryOverlayKind,
    val geoJson: String,
    val highlighted: Boolean = false,
    val label: String? = null
) {
    fun signature(): String = "${kind.name}:$id:$highlighted:${label.orEmpty()}:$geoJson"
}

internal data class MapLibreOverlayPaint(
    val fillColor: String,
    val fillOpacity: Float,
    val lineColor: String,
    val lineWidth: Float,
    val lineOpacity: Float,
    val circleColor: String,
    val circleRadius: Float,
    val circleOpacity: Float,
    val circleStrokeColor: String,
    val circleStrokeWidth: Float,
    val textColor: String,
    val textHaloColor: String,
    val textHaloWidth: Float,
    val textHaloBlur: Float,
    val textSize: Float,
    val textOffset: List<Float>
)

internal fun mapLibreOverlayPaint(
    kind: MapLibreGeometryOverlayKind,
    highlighted: Boolean
): MapLibreOverlayPaint {
    val lineWidthBoost = if (highlighted) 1.25f else 0.0f
    val markerBoost = if (highlighted) 1.5f else 0.0f
    return when (kind) {
        MapLibreGeometryOverlayKind.Overall ->
            MapLibreOverlayPaint(
                fillColor = "#2563EB",
                fillOpacity = 0.12f,
                lineColor = "#1D4ED8",
                lineWidth = 2.5f + lineWidthBoost,
                lineOpacity = 0.92f,
                circleColor = "#1D4ED8",
                circleRadius = 0.0f,
                circleOpacity = 0.0f,
                circleStrokeColor = "#FFFFFF",
                circleStrokeWidth = 0.0f,
                textColor = "#172554",
                textHaloColor = "#FFFFFF",
                textHaloWidth = 2.0f,
                textHaloBlur = 0.35f,
                textSize = 12.0f,
                textOffset = listOf(0.0f, 0.0f)
            )

        MapLibreGeometryOverlayKind.Unit ->
            MapLibreOverlayPaint(
                fillColor = "#059669",
                fillOpacity = 0.14f,
                lineColor = "#047857",
                lineWidth = 2.35f + lineWidthBoost,
                lineOpacity = 0.9f,
                circleColor = "#047857",
                circleRadius = 0.0f,
                circleOpacity = 0.0f,
                circleStrokeColor = "#FFFFFF",
                circleStrokeWidth = 0.0f,
                textColor = "#064E3B",
                textHaloColor = "#FFFFFF",
                textHaloWidth = 2.0f,
                textHaloBlur = 0.35f,
                textSize = 11.5f,
                textOffset = listOf(0.0f, 0.0f)
            )

        MapLibreGeometryOverlayKind.Team ->
            MapLibreOverlayPaint(
                fillColor = "#F97316",
                fillOpacity = 0.18f,
                lineColor = "#C2410C",
                lineWidth = 2.4f + lineWidthBoost,
                lineOpacity = 0.92f,
                circleColor = "#C2410C",
                circleRadius = 0.0f,
                circleOpacity = 0.0f,
                circleStrokeColor = "#FFFFFF",
                circleStrokeWidth = 0.0f,
                textColor = "#7C2D12",
                textHaloColor = "#FFFFFF",
                textHaloWidth = 2.0f,
                textHaloBlur = 0.35f,
                textSize = 11.5f,
                textOffset = listOf(0.0f, 0.0f)
            )

        MapLibreGeometryOverlayKind.Path ->
            MapLibreOverlayPaint(
                fillColor = "#2563EB",
                fillOpacity = 0.0f,
                lineColor = "#2563EB",
                lineWidth = 3.0f + lineWidthBoost,
                lineOpacity = 0.94f,
                circleColor = "#2563EB",
                circleRadius = 0.0f,
                circleOpacity = 0.0f,
                circleStrokeColor = "#FFFFFF",
                circleStrokeWidth = 0.0f,
                textColor = "#1E3A8A",
                textHaloColor = "#FFFFFF",
                textHaloWidth = 2.25f,
                textHaloBlur = 0.35f,
                textSize = 11.5f,
                textOffset = listOf(0.0f, 0.0f)
            )

        MapLibreGeometryOverlayKind.Marker ->
            MapLibreOverlayPaint(
                fillColor = "#DC2626",
                fillOpacity = 0.0f,
                lineColor = "#DC2626",
                lineWidth = 0.0f,
                lineOpacity = 0.0f,
                circleColor = "#DC2626",
                circleRadius = 6.0f + markerBoost,
                circleOpacity = 0.96f,
                circleStrokeColor = "#FFFFFF",
                circleStrokeWidth = 2.25f,
                textColor = "#991B1B",
                textHaloColor = "#FFFFFF",
                textHaloWidth = 2.25f,
                textHaloBlur = 0.35f,
                textSize = 12.0f,
                textOffset = listOf(0.0f, 1.15f)
            )
    }
}

data class MapLibreRuntimeMapState(
    val apiBaseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL,
    val styleId: String = "osm-local",
    val accessToken: String? = null,
    val policePhoneId: String? = null,
    val initialBounds: MapLibreViewportBounds? = null,
    val geometryOverlays: List<MapLibreGeometryOverlay> = emptyList()
) {
    fun tileSourceConfig(): MapLibreTileSourceConfig {
        return MapLibreTileSourceFactory(apiBaseUrl = apiBaseUrl).styleSource(
            styleId = styleId,
            accessToken = accessToken,
            policePhoneId = policePhoneId
        )
    }
}

@Composable
fun SuriMapLibreMap(
    state: MapLibreRuntimeMapState,
    modifier: Modifier = Modifier,
    onLoadFailed: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestLoadFailed by rememberUpdatedState(onLoadFailed)
    var appliedStyleUrl by remember { mutableStateOf<String?>(null) }
    var appliedOverlaySignature by remember { mutableStateOf<String?>(null) }
    var appliedOverlayStyleIds by remember { mutableStateOf(emptySet<String>()) }
    var appliedCameraSignature by remember { mutableStateOf<String?>(null) }
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(Bundle()) }
    }
    val lifecycleBridge = remember(mapView) { MapViewLifecycleBridge(mapView) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> lifecycleBridge.onStart()
                Lifecycle.Event.ON_RESUME -> lifecycleBridge.onResume()
                Lifecycle.Event.ON_PAUSE -> lifecycleBridge.onPause()
                Lifecycle.Event.ON_STOP -> lifecycleBridge.onStop()
                Lifecycle.Event.ON_DESTROY -> lifecycleBridge.onDestroy()
                else -> Unit
            }
        }
        val failListener = MapView.OnDidFailLoadingMapListener { reason ->
            latestLoadFailed(reason)
        }

        lifecycle.addObserver(observer)
        mapView.addOnDidFailLoadingMapListener(failListener)
        lifecycleBridge.sync(lifecycle.currentState)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.removeOnDidFailLoadingMapListener(failListener)
            lifecycleBridge.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            val styleUrl = state.tileSourceConfig().styleUrl
            val overlaySignature = state.geometryOverlaySignature()
            val cameraSignature = state.initialBounds?.signature()
            view.getMapAsync { mapLibreMap ->
                mapLibreMap.uiSettings.setAttributionEnabled(true)
                mapLibreMap.uiSettings.setLogoEnabled(true)
                fun applyRuntimeState(style: Style) {
                    if (appliedOverlaySignature != overlaySignature) {
                        val currentStyleIds = state.geometryOverlays.map { it.styleId }.toSet()
                        style.removeGeometryOverlays(appliedOverlayStyleIds - currentStyleIds)
                        state.geometryOverlays.forEach { overlay ->
                            style.upsertGeometryOverlay(overlay)
                        }
                        appliedOverlayStyleIds = currentStyleIds
                        appliedOverlaySignature = overlaySignature
                    }
                    val bounds = state.initialBounds
                    if (bounds != null && appliedCameraSignature != cameraSignature) {
                        view.post {
                            runCatching {
                                mapLibreMap.moveCamera(
                                    CameraUpdateFactory.newLatLngBounds(
                                        bounds.toLatLngBounds(),
                                        INITIAL_BOUNDS_PADDING_PX
                                    )
                                )
                            }.onSuccess {
                                appliedCameraSignature = cameraSignature
                            }
                        }
                    } else if (bounds == null) {
                        appliedCameraSignature = null
                    }
                }

                if (appliedStyleUrl != styleUrl) {
                    installMapLibreTileHttp(context, state)
                    appliedOverlaySignature = null
                    appliedOverlayStyleIds = emptySet()
                    appliedCameraSignature = null
                    appliedStyleUrl = styleUrl
                    mapLibreMap.setStyle(styleUrl) {
                        applyRuntimeState(it)
                    }
                } else {
                    mapLibreMap.getStyle { style ->
                        applyRuntimeState(style)
                    }
                }
            }
        }
    )
}

private fun installMapLibreTileHttp(context: Context, state: MapLibreRuntimeMapState) {
    if (BuildConfig.DEBUG && state.apiBaseUrl.isLoopbackHttpBaseUrl()) {
        ConnectivityReceiver.instance(context.applicationContext).setConnected(true)
    }
    MapLibreTileHttpInstaller.install(
        MapLibreTileCallFactory(
            tileBaseUrl = state.apiBaseUrl,
            accessTokenProvider = { state.accessToken },
            policePhoneIdProvider = { state.policePhoneId }
        )
    )
}

private fun String.isLoopbackHttpBaseUrl(): Boolean {
    val normalized = trim().lowercase()
    return normalized.startsWith("http://127.0.0.1") || normalized.startsWith("http://localhost")
}

private val MapLibreGeometryOverlay.styleId: String
    get() = "suri-${kind.name.lowercase()}-${id.ifBlank { kind.name }.replace(UNSAFE_STYLE_ID_CHARS, "-")}"

private val MapLibreGeometryOverlay.sourceId: String
    get() = "$styleId-source"

private val MapLibreGeometryOverlay.fillLayerId: String
    get() = "$styleId-fill"

private val MapLibreGeometryOverlay.lineLayerId: String
    get() = "$styleId-line"

private val MapLibreGeometryOverlay.circleLayerId: String
    get() = "$styleId-circle"

private val MapLibreGeometryOverlay.labelLayerId: String
    get() = "$styleId-label"

private fun MapLibreRuntimeMapState.geometryOverlaySignature(): String =
    geometryOverlays.joinToString("|") { it.signature() }

private fun Style.upsertGeometryOverlay(overlay: MapLibreGeometryOverlay) {
    val sourceJson = overlay.featureCollectionJson() ?: return
    val source = getSourceAs<GeoJsonSource>(overlay.sourceId)
    if (source == null) {
        addSource(GeoJsonSource(overlay.sourceId, sourceJson))
    } else {
        source.setGeoJson(sourceJson)
    }

    val paint = mapLibreOverlayPaint(overlay.kind, overlay.highlighted)

    if (overlay.supportsFillLayer) {
        upsertFillLayer(overlay, paint)
    } else {
        removeLayer(overlay.fillLayerId)
    }
    if (overlay.supportsLineLayer) {
        upsertLineLayer(overlay, paint)
    } else {
        removeLayer(overlay.lineLayerId)
    }
    if (overlay.supportsCircleLayer) {
        upsertCircleLayer(overlay, paint)
    } else {
        removeLayer(overlay.circleLayerId)
    }
    if (overlay.supportsLabelLayer) {
        upsertLabelLayer(overlay, paint)
    } else {
        removeLayer(overlay.labelLayerId)
    }
}

private fun Style.upsertFillLayer(overlay: MapLibreGeometryOverlay, paint: MapLibreOverlayPaint) {
    val layer = getLayer(overlay.fillLayerId)
    if (layer == null) {
        addLayer(
            FillLayer(overlay.fillLayerId, overlay.sourceId).withProperties(
                fillColor(paint.fillColor),
                fillOpacity(paint.fillOpacity),
                fillOutlineColor(paint.lineColor)
            )
        )
        return
    }
    layer.setProperties(
        fillColor(paint.fillColor),
        fillOpacity(paint.fillOpacity),
        fillOutlineColor(paint.lineColor)
    )
}

private fun Style.upsertLineLayer(overlay: MapLibreGeometryOverlay, paint: MapLibreOverlayPaint) {
    val layer = getLayer(overlay.lineLayerId)
    if (layer == null) {
        addLayer(
            LineLayer(overlay.lineLayerId, overlay.sourceId).withProperties(
                lineColor(paint.lineColor),
                lineWidth(paint.lineWidth),
                lineOpacity(paint.lineOpacity),
                lineCap(LINE_CAP_ROUND),
                lineJoin(LINE_JOIN_ROUND)
            )
        )
        return
    }
    layer.setProperties(
        lineColor(paint.lineColor),
        lineWidth(paint.lineWidth),
        lineOpacity(paint.lineOpacity),
        lineCap(LINE_CAP_ROUND),
        lineJoin(LINE_JOIN_ROUND)
    )
}

private fun Style.upsertCircleLayer(overlay: MapLibreGeometryOverlay, paint: MapLibreOverlayPaint) {
    val layer = getLayer(overlay.circleLayerId)
    if (layer == null) {
        addLayer(
            CircleLayer(overlay.circleLayerId, overlay.sourceId).withProperties(
                circleColor(paint.circleColor),
                circleRadius(paint.circleRadius),
                circleOpacity(paint.circleOpacity),
                circleStrokeColor(paint.circleStrokeColor),
                circleStrokeWidth(paint.circleStrokeWidth)
            )
        )
        return
    }
    layer.setProperties(
        circleColor(paint.circleColor),
        circleRadius(paint.circleRadius),
        circleOpacity(paint.circleOpacity),
        circleStrokeColor(paint.circleStrokeColor),
        circleStrokeWidth(paint.circleStrokeWidth)
    )
}

private fun Style.upsertLabelLayer(overlay: MapLibreGeometryOverlay, paint: MapLibreOverlayPaint) {
    val layer = getLayer(overlay.labelLayerId)
    if (layer == null) {
        addLayer(
            SymbolLayer(overlay.labelLayerId, overlay.sourceId).withProperties(
                symbolPlacement(overlay.labelPlacement),
                textField(Expression.get("label")),
                textSize(paint.textSize),
                textColor(paint.textColor),
                textHaloColor(paint.textHaloColor),
                textHaloWidth(paint.textHaloWidth),
                textHaloBlur(paint.textHaloBlur),
                textOffset(paint.textOffset.toTypedArray()),
                textAllowOverlap(overlay.highlighted),
                textIgnorePlacement(false),
                textOptional(true)
            )
        )
        return
    }
    layer.setProperties(
        symbolPlacement(overlay.labelPlacement),
        textField(Expression.get("label")),
        textSize(paint.textSize),
        textColor(paint.textColor),
        textHaloColor(paint.textHaloColor),
        textHaloWidth(paint.textHaloWidth),
        textHaloBlur(paint.textHaloBlur),
        textOffset(paint.textOffset.toTypedArray()),
        textAllowOverlap(overlay.highlighted),
        textIgnorePlacement(false),
        textOptional(true)
    )
}

private fun Style.removeGeometryOverlays(styleIds: Set<String>) {
    styleIds.forEach { styleId ->
        removeLayer("$styleId-label")
        removeLayer("$styleId-circle")
        removeLayer("$styleId-line")
        removeLayer("$styleId-fill")
        removeSource("$styleId-source")
    }
}

private fun MapLibreGeometryOverlay.featureCollectionJson(): String? {
    val geometry = runCatching { JSONObject(geoJson) }.getOrNull() ?: return null
    if (!geometry.isRenderableGeometry()) return null
    return JSONObject()
        .put("type", "FeatureCollection")
        .put(
            "features",
            JSONArray().put(
                JSONObject()
                    .put("type", "Feature")
                    .put(
                        "properties",
                        JSONObject()
                            .put("id", id)
                            .put("kind", kind.name)
                            .put("highlighted", highlighted)
                            .put("label", label.orEmpty())
                    )
                    .put("geometry", geometry)
            )
        )
        .toString()
}

private fun JSONObject.isRenderableGeometry(): Boolean {
    return when (optString("type")) {
        "Point" -> optJSONArray("coordinates")?.length() == 2
        "MultiPoint" -> (optJSONArray("coordinates")?.length() ?: 0) > 0
        "LineString" -> (optJSONArray("coordinates")?.length() ?: 0) >= 2
        "MultiLineString" -> hasRenderableNestedLineCoordinates()
        "Polygon" -> hasRenderablePolygonCoordinates()
        "MultiPolygon" -> hasRenderableMultiPolygonCoordinates()
        "GeometryCollection" -> hasRenderableGeometryCollection()
        else -> false
    }
}

private fun JSONObject.hasRenderablePolygonCoordinates(): Boolean {
    val rings = optJSONArray("coordinates") ?: return false
    return rings.hasRenderablePolygonRing()
}

private fun JSONObject.hasRenderableNestedLineCoordinates(): Boolean {
    val lines = optJSONArray("coordinates") ?: return false
    return lines.anyArray { it.length() >= 2 }
}

private fun JSONObject.hasRenderableMultiPolygonCoordinates(): Boolean {
    val polygons = optJSONArray("coordinates") ?: return false
    return polygons.anyArray { it.hasRenderablePolygonRing() }
}

private fun JSONObject.hasRenderableGeometryCollection(): Boolean {
    val geometries = optJSONArray("geometries") ?: return false
    return geometries.anyObject { it.isRenderableGeometry() }
}

private fun JSONArray.hasRenderablePolygonRing(): Boolean {
    if (length() == 0) return false
    val outerRing = optJSONArray(0) ?: return false
    return outerRing.length() >= 4
}

private fun JSONArray.anyArray(predicate: (JSONArray) -> Boolean): Boolean {
    for (index in 0 until length()) {
        val value = optJSONArray(index) ?: continue
        if (predicate(value)) return true
    }
    return false
}

private fun JSONArray.anyObject(predicate: (JSONObject) -> Boolean): Boolean {
    for (index in 0 until length()) {
        val value = optJSONObject(index) ?: continue
        if (predicate(value)) return true
    }
    return false
}

private val MapLibreGeometryOverlay.supportsFillLayer: Boolean
    get() =
        when (kind) {
            MapLibreGeometryOverlayKind.Overall -> true
            MapLibreGeometryOverlayKind.Unit -> true
            MapLibreGeometryOverlayKind.Team -> true
            MapLibreGeometryOverlayKind.Path -> false
            MapLibreGeometryOverlayKind.Marker -> false
        }

private val MapLibreGeometryOverlay.supportsLineLayer: Boolean
    get() =
        when (kind) {
            MapLibreGeometryOverlayKind.Overall -> true
            MapLibreGeometryOverlayKind.Unit -> true
            MapLibreGeometryOverlayKind.Team -> true
            MapLibreGeometryOverlayKind.Path -> true
            MapLibreGeometryOverlayKind.Marker -> false
        }

private val MapLibreGeometryOverlay.supportsCircleLayer: Boolean
    get() =
        when (kind) {
            MapLibreGeometryOverlayKind.Overall -> false
            MapLibreGeometryOverlayKind.Unit -> false
            MapLibreGeometryOverlayKind.Team -> false
            MapLibreGeometryOverlayKind.Path -> false
            MapLibreGeometryOverlayKind.Marker -> true
        }

private val MapLibreGeometryOverlay.supportsLabelLayer: Boolean
    get() =
        !label.isNullOrBlank()

private val MapLibreGeometryOverlay.labelPlacement: String
    get() =
        when (kind) {
            MapLibreGeometryOverlayKind.Path -> SYMBOL_PLACEMENT_LINE
            else -> SYMBOL_PLACEMENT_POINT
        }

private class MapViewLifecycleBridge(
    private val mapView: MapView
) {
    private var started = false
    private var resumed = false

    fun sync(state: Lifecycle.State) {
        if (state.isAtLeast(Lifecycle.State.STARTED)) {
            onStart()
        }
        if (state.isAtLeast(Lifecycle.State.RESUMED)) {
            onResume()
        }
    }

    fun onStart() {
        if (!started && !mapView.isDestroyed) {
            mapView.onStart()
            started = true
        }
    }

    fun onResume() {
        onStart()
        if (!resumed && !mapView.isDestroyed) {
            mapView.onResume()
            resumed = true
        }
    }

    fun onPause() {
        if (resumed && !mapView.isDestroyed) {
            mapView.onPause()
            resumed = false
        }
    }

    fun onStop() {
        onPause()
        if (started && !mapView.isDestroyed) {
            mapView.onStop()
            started = false
        }
    }

    fun onDestroy() {
        onStop()
        if (!mapView.isDestroyed) {
            mapView.onDestroy()
        }
    }
}

private val UNSAFE_STYLE_ID_CHARS = Regex("[^A-Za-z0-9_-]")
private const val INITIAL_BOUNDS_PADDING_PX = 64
