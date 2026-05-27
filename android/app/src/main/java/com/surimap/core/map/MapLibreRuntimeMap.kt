package com.surimap.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
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
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.net.ConnectivityReceiver
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property.ICON_ANCHOR_CENTER
import org.maplibre.android.style.layers.Property.ICON_ROTATION_ALIGNMENT_MAP
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
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOptional
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolPlacement
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloBlur
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.sources.GeoJsonSource

private val SURI_MAP_LABEL_FONT_STACK = arrayOf("Pretendard GOV")
private const val CURRENT_LOCATION_IMAGE_ID = "suri-current-location"
private const val CURRENT_LOCATION_HEADING_IMAGE_ID = "suri-current-location-heading"
private const val CURRENT_LOCATION_IMAGE_SIZE_PX = 76
private const val CURRENT_LOCATION_MARKER_RADIUS_PX = 17f
private const val CURRENT_LOCATION_MARKER_STROKE_WIDTH_PX = 5f
private const val CURRENT_LOCATION_ICON_SIZE = 1.35f
private const val CURRENT_LOCATION_HEADING_IMAGE_SDF = false
private const val MARKER_ICON_PREFIX = "suri-board-marker"
private const val MARKER_ICON_WIDTH = 40
private const val MARKER_ICON_HEIGHT = 46
private const val MARKER_ICON_RASTER_SCALE = 2
private const val MARKER_ICON_SIZE = 0.5f
private const val MARKER_SELECTED_GLOW_COLOR = "#38BDF8"

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
    Marker,
    CurrentLocation
}

data class MapLibreGeometryOverlay(
    val id: String,
    val kind: MapLibreGeometryOverlayKind,
    val geoJson: String,
    val highlighted: Boolean = false,
    val label: String? = null,
    val bearingDegrees: Double? = null,
    val markerType: String? = null,
    val supportRequestType: String? = null,
    val visualStyle: MapLibreGeometryVisualStyle? = null
) {
    fun signature(): String =
        "${kind.name}:$id:$highlighted:${label.orEmpty()}:${bearingDegrees ?: ""}:${markerType.orEmpty()}:${supportRequestType.orEmpty()}:${visualStyle?.signature().orEmpty()}:$geoJson"
}

data class MapLibreGeometryVisualStyle(
    val fillColor: String? = null,
    val fillOpacity: Float? = null,
    val lineColor: String? = null,
    val lineWidth: Float? = null,
    val lineOpacity: Float? = null
) {
    fun signature(): String =
        "${fillColor.orEmpty()}:${fillOpacity ?: ""}:${lineColor.orEmpty()}:${lineWidth ?: ""}:${lineOpacity ?: ""}"
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
    highlighted: Boolean,
    visualStyle: MapLibreGeometryVisualStyle? = null
): MapLibreOverlayPaint {
    val lineWidthBoost = if (highlighted) 1.25f else 0.0f
    val markerBoost = if (highlighted) 1.5f else 0.0f
    val basePaint = when (kind) {
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

        MapLibreGeometryOverlayKind.CurrentLocation ->
            MapLibreOverlayPaint(
                fillColor = "#0284C7",
                fillOpacity = 0.0f,
                lineColor = "#0284C7",
                lineWidth = 0.0f,
                lineOpacity = 0.0f,
                circleColor = "#0EA5E9",
                circleRadius = 7.5f + markerBoost,
                circleOpacity = 0.98f,
                circleStrokeColor = "#FFFFFF",
                circleStrokeWidth = 2.75f,
                textColor = "#075985",
                textHaloColor = "#FFFFFF",
                textHaloWidth = 2.25f,
                textHaloBlur = 0.35f,
                textSize = 12.0f,
                textOffset = listOf(0.0f, 1.2f)
            )
    }
    return visualStyle?.let { style ->
        basePaint.copy(
            fillColor = style.fillColor ?: basePaint.fillColor,
            fillOpacity = style.fillOpacity ?: basePaint.fillOpacity,
            lineColor = style.lineColor ?: basePaint.lineColor,
            lineWidth = style.lineWidth ?: basePaint.lineWidth,
            lineOpacity = style.lineOpacity ?: basePaint.lineOpacity,
            circleColor = style.lineColor ?: basePaint.circleColor,
            textColor = style.lineColor ?: basePaint.textColor
        )
    } ?: basePaint
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

class MapLibreMapViewHandle {
    private var cachedMapView: MapView? = null
    private var cachedLifecycleBridge: MapViewLifecycleBridge? = null
    internal var appliedStyleUrl: String? = null
    internal var appliedOverlaySignature: String? = null
    internal var appliedOverlayStyleIds: Set<String> = emptySet()
    internal var appliedCameraSignature: String? = null

    internal fun mapView(context: Context): MapView {
        val existing = cachedMapView
        if (existing != null && !existing.isDestroyed) {
            return existing
        }
        MapLibre.getInstance(context.applicationContext)
        val options =
            MapLibreMapOptions()
                .textureMode(true)
                .foregroundLoadColor(MAP_FOREGROUND_LOAD_COLOR)
                .setPrefetchesTiles(true)
        return MapView(context, options).apply {
            setBackgroundColor(MAP_FOREGROUND_LOAD_COLOR)
            onCreate(Bundle())
        }.also { mapView ->
            cachedMapView = mapView
            cachedLifecycleBridge = MapViewLifecycleBridge(mapView)
        }
    }

    internal fun lifecycleBridge(mapView: MapView): MapViewLifecycleBridge {
        return cachedLifecycleBridge ?: MapViewLifecycleBridge(mapView).also { bridge ->
            cachedLifecycleBridge = bridge
        }
    }

    internal fun sync(state: Lifecycle.State) {
        cachedLifecycleBridge?.sync(state)
    }

    internal fun onStart() {
        cachedLifecycleBridge?.onStart()
    }

    internal fun onResume() {
        cachedLifecycleBridge?.onResume()
    }

    internal fun onPause() {
        cachedLifecycleBridge?.onPause()
    }

    internal fun onStop() {
        cachedLifecycleBridge?.onStop()
    }

    internal fun destroy() {
        cachedLifecycleBridge?.onDestroy()
        cachedLifecycleBridge = null
        cachedMapView = null
        appliedStyleUrl = null
        appliedOverlaySignature = null
        appliedOverlayStyleIds = emptySet()
        appliedCameraSignature = null
    }
}

@Composable
fun rememberMapLibreMapViewHandle(key: Any? = Unit): MapLibreMapViewHandle {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handle = remember(key) { MapLibreMapViewHandle() }
    DisposableEffect(handle, lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> handle.onStart()
                    Lifecycle.Event.ON_RESUME -> handle.onResume()
                    Lifecycle.Event.ON_PAUSE -> handle.onPause()
                    Lifecycle.Event.ON_STOP -> handle.onStop()
                    Lifecycle.Event.ON_DESTROY -> handle.destroy()
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        handle.sync(lifecycle.currentState)
        onDispose {
            lifecycle.removeObserver(observer)
            handle.destroy()
        }
    }
    return handle
}

@Composable
fun SuriMapLibreMap(
    state: MapLibreRuntimeMapState,
    modifier: Modifier = Modifier,
    mapViewHandle: MapLibreMapViewHandle? = null,
    onLoadFailed: (String) -> Unit = {},
    onMarkerClick: (String) -> Unit = {},
    onViewportBoundsChanged: (MapLibreViewportBounds) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestLoadFailed by rememberUpdatedState(onLoadFailed)
    val latestMarkerClick by rememberUpdatedState(onMarkerClick)
    val latestViewportBoundsChanged by rememberUpdatedState(onViewportBoundsChanged)
    val latestMapState by rememberUpdatedState(state)
    var markerClickListener by remember { mutableStateOf<MapLibreMap.OnMapClickListener?>(null) }
    var cameraIdleListener by remember { mutableStateOf<MapLibreMap.OnCameraIdleListener?>(null) }
    val ownedMapViewHandle = rememberMapLibreMapViewHandle()
    val activeMapViewHandle = mapViewHandle ?: ownedMapViewHandle
    val mapView = remember(activeMapViewHandle, context) { activeMapViewHandle.mapView(context) }
    val lifecycleBridge = remember(activeMapViewHandle, mapView) { activeMapViewHandle.lifecycleBridge(mapView) }

    DisposableEffect(lifecycle, mapView) {
        val failListener = MapView.OnDidFailLoadingMapListener { reason ->
            latestLoadFailed(reason)
        }

        mapView.addOnDidFailLoadingMapListener(failListener)
        lifecycleBridge.sync(lifecycle.currentState)
        onDispose {
            markerClickListener?.let { listener ->
                mapView.getMapAsync { mapLibreMap ->
                    mapLibreMap.removeOnMapClickListener(listener)
                }
            }
            cameraIdleListener?.let { listener ->
                mapView.getMapAsync { mapLibreMap ->
                    mapLibreMap.removeOnCameraIdleListener(listener)
                }
            }
            mapView.removeOnDidFailLoadingMapListener(failListener)
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
                if (markerClickListener == null) {
                    val listener =
                        MapLibreMap.OnMapClickListener { latLng ->
                            val markerLayerIds =
                                latestMapState.geometryOverlays
                                    .filter { it.kind == MapLibreGeometryOverlayKind.Marker }
                                    .map { it.markerIconLayerId }
                                    .toTypedArray()
                            if (markerLayerIds.isEmpty()) {
                                return@OnMapClickListener false
                            }
                            val point: PointF = mapLibreMap.projection.toScreenLocation(latLng)
                            val markerId =
                                runCatching {
                                    mapLibreMap.queryRenderedFeatures(point, *markerLayerIds)
                                        .firstNotNullOfOrNull { feature ->
                                            feature.getStringProperty("id")?.takeIf(String::isNotBlank)
                                        }
                                }.getOrNull()
                            if (markerId != null) {
                                latestMarkerClick(markerId)
                                true
                            } else {
                                false
                            }
                        }
                    mapLibreMap.addOnMapClickListener(listener)
                    markerClickListener = listener
                }
                if (cameraIdleListener == null) {
                    val listener =
                        MapLibreMap.OnCameraIdleListener {
                            mapLibreMap.projection.visibleRegion.latLngBounds
                                .toMapLibreViewportBoundsOrNull()
                                ?.let(latestViewportBoundsChanged)
                        }
                    mapLibreMap.addOnCameraIdleListener(listener)
                    cameraIdleListener = listener
                }
                fun applyRuntimeState(style: Style) {
                    if (activeMapViewHandle.appliedOverlaySignature != overlaySignature) {
                        val currentStyleIds = state.geometryOverlays.map { it.styleId }.toSet()
                        style.removeGeometryOverlays(activeMapViewHandle.appliedOverlayStyleIds - currentStyleIds)
                        state.geometryOverlays.forEach { overlay ->
                            style.upsertGeometryOverlay(overlay)
                        }
                        activeMapViewHandle.appliedOverlayStyleIds = currentStyleIds
                        activeMapViewHandle.appliedOverlaySignature = overlaySignature
                    }
                    val bounds = state.initialBounds
                    if (bounds != null && activeMapViewHandle.appliedCameraSignature != cameraSignature) {
                        view.post {
                            runCatching {
                                mapLibreMap.moveCamera(
                                    CameraUpdateFactory.newLatLngBounds(
                                        bounds.toLatLngBounds(),
                                        INITIAL_BOUNDS_PADDING_PX
                                    )
                                )
                            }.onSuccess {
                                activeMapViewHandle.appliedCameraSignature = cameraSignature
                            }
                        }
                    } else if (bounds == null) {
                        activeMapViewHandle.appliedCameraSignature = null
                    }
                }

                if (activeMapViewHandle.appliedStyleUrl != styleUrl) {
                    installMapLibreTileHttp(context, state)
                    activeMapViewHandle.appliedOverlaySignature = null
                    activeMapViewHandle.appliedOverlayStyleIds = emptySet()
                    activeMapViewHandle.appliedCameraSignature = null
                    activeMapViewHandle.appliedStyleUrl = styleUrl
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

private fun LatLngBounds.toMapLibreViewportBoundsOrNull(): MapLibreViewportBounds? =
    MapLibreViewportBounds(
        south = latitudeSouth,
        west = longitudeWest,
        north = latitudeNorth,
        east = longitudeEast
    ).takeIf { bounds ->
        bounds.south.isFinite() &&
            bounds.west.isFinite() &&
            bounds.north.isFinite() &&
            bounds.east.isFinite()
    }

private fun installMapLibreTileHttp(context: Context, state: MapLibreRuntimeMapState) {
    if (BuildConfig.DEBUG && state.apiBaseUrl.isLoopbackHttpBaseUrl()) {
        ConnectivityReceiver.instance(context.applicationContext).setConnected(true)
    }
    MapLibreTileHttpInstaller.install(
        MapLibreTileCallFactory(
            tileBaseUrl = state.apiBaseUrl,
            accessTokenProvider = { state.accessToken },
            policePhoneIdProvider = { state.policePhoneId },
            offlineTileCache = OfflineTileCache.fromContext(context)
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

private val MapLibreGeometryOverlay.markerIconLayerId: String
    get() = "$styleId-marker-icon"

private val MapLibreGeometryOverlay.labelLayerId: String
    get() = "$styleId-label"

private val MapLibreGeometryOverlay.headingLayerId: String
    get() = "$styleId-heading"

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

    val paint = mapLibreOverlayPaint(overlay.kind, overlay.highlighted, overlay.visualStyle)

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
    if (overlay.supportsMarkerIconLayer) {
        upsertMarkerIconLayer(overlay)
    } else {
        removeLayer(overlay.markerIconLayerId)
    }
    if (overlay.supportsHeadingLayer) {
        upsertHeadingLayer(overlay, paint)
    } else {
        removeLayer(overlay.headingLayerId)
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

private fun Style.upsertMarkerIconLayer(overlay: MapLibreGeometryOverlay) {
    upsertMarkerImage(overlay)
    val layer = getLayer(overlay.markerIconLayerId)
    if (layer == null) {
        addLayer(
            SymbolLayer(overlay.markerIconLayerId, overlay.sourceId).withProperties(
                symbolPlacement(SYMBOL_PLACEMENT_POINT),
                iconImage(Expression.get("markerIcon")),
                iconSize(MARKER_ICON_SIZE),
                iconAnchor("bottom"),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconOptional(false)
            )
        )
        return
    }
    layer.setProperties(
        symbolPlacement(SYMBOL_PLACEMENT_POINT),
        iconImage(Expression.get("markerIcon")),
        iconSize(MARKER_ICON_SIZE),
        iconAnchor("bottom"),
        iconAllowOverlap(true),
        iconIgnorePlacement(true),
        iconOptional(false)
    )
}

private fun Style.upsertMarkerImage(overlay: MapLibreGeometryOverlay) {
    val imageId = overlay.markerIconImageId
    if (getImage(imageId) != null) {
        return
    }
    addImage(
        imageId,
        markerBitmap(
            type = overlay.markerType,
            supportRequestType = overlay.supportRequestType,
            selected = overlay.highlighted
        ),
        false
    )
}

private fun Style.upsertHeadingLayer(overlay: MapLibreGeometryOverlay, paint: MapLibreOverlayPaint) {
    upsertCurrentLocationImages(paint)
    val layer = getLayer(overlay.headingLayerId)
    if (layer == null) {
        addLayer(
            SymbolLayer(overlay.headingLayerId, overlay.sourceId).withProperties(
                symbolPlacement(SYMBOL_PLACEMENT_POINT),
                iconImage(Expression.get("currentLocationIcon")),
                iconSize(CURRENT_LOCATION_ICON_SIZE),
                iconAnchor(ICON_ANCHOR_CENTER),
                iconRotate(Expression.get("bearingDegrees")),
                iconRotationAlignment(ICON_ROTATION_ALIGNMENT_MAP),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconOptional(false)
            )
        )
        return
    }
    layer.setProperties(
        symbolPlacement(SYMBOL_PLACEMENT_POINT),
        iconImage(Expression.get("currentLocationIcon")),
        iconSize(CURRENT_LOCATION_ICON_SIZE),
        iconAnchor(ICON_ANCHOR_CENTER),
        iconRotate(Expression.get("bearingDegrees")),
        iconRotationAlignment(ICON_ROTATION_ALIGNMENT_MAP),
        iconAllowOverlap(true),
        iconIgnorePlacement(true),
        iconOptional(false)
    )
}

private fun Style.upsertCurrentLocationImages(paint: MapLibreOverlayPaint) {
    if (getImage(CURRENT_LOCATION_IMAGE_ID) == null) {
        addImage(
            CURRENT_LOCATION_IMAGE_ID,
            currentLocationBitmap(
                fillColor = paint.circleColor,
                strokeColor = paint.circleStrokeColor,
                withHeading = false
            ),
            CURRENT_LOCATION_HEADING_IMAGE_SDF
        )
    }
    if (getImage(CURRENT_LOCATION_HEADING_IMAGE_ID) == null) {
        addImage(
            CURRENT_LOCATION_HEADING_IMAGE_ID,
            currentLocationBitmap(
                fillColor = paint.circleColor,
                strokeColor = paint.circleStrokeColor,
                withHeading = true
            ),
            CURRENT_LOCATION_HEADING_IMAGE_SDF
        )
    }
}

private fun Style.upsertLabelLayer(overlay: MapLibreGeometryOverlay, paint: MapLibreOverlayPaint) {
    val layer = getLayer(overlay.labelLayerId)
    if (layer == null) {
        addLayer(
            SymbolLayer(overlay.labelLayerId, overlay.sourceId).withProperties(
                symbolPlacement(overlay.labelPlacement),
                textField(Expression.get("label")),
                textFont(SURI_MAP_LABEL_FONT_STACK),
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
        textFont(SURI_MAP_LABEL_FONT_STACK),
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
        removeLayer("$styleId-heading")
        removeLayer("$styleId-marker-icon")
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
                            .apply {
                                if (kind == MapLibreGeometryOverlayKind.Marker) {
                                    put("markerIcon", markerIconImageId)
                                }
                                if (kind == MapLibreGeometryOverlayKind.CurrentLocation) {
                                    put("currentLocationIcon", if (bearingDegrees == null) CURRENT_LOCATION_IMAGE_ID else CURRENT_LOCATION_HEADING_IMAGE_ID)
                                    put("bearingDegrees", bearingDegrees ?: 0.0)
                                }
                            }
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

private val MapLibreGeometryOverlay.markerIconImageId: String
    get() {
        val typeKey = markerType.markerTypeKey()
        val glyph = markerGlyphName(typeKey, supportRequestType)
        val state = if (highlighted) "selected" else "base"
        return "$MARKER_ICON_PREFIX-${typeKey.lowercase()}-${glyph.lowercase()}-$state"
    }

private fun markerBitmap(
    type: String?,
    supportRequestType: String?,
    selected: Boolean
): Bitmap {
    val bitmap =
        Bitmap.createBitmap(
            MARKER_ICON_WIDTH * MARKER_ICON_RASTER_SCALE,
            MARKER_ICON_HEIGHT * MARKER_ICON_RASTER_SCALE,
            Bitmap.Config.ARGB_8888
        )
    val canvas = Canvas(bitmap)
    canvas.scale(MARKER_ICON_RASTER_SCALE.toFloat(), MARKER_ICON_RASTER_SCALE.toFloat())

    val typeKey = type.markerTypeKey()
    val glyph = markerGlyphName(typeKey, supportRequestType)
    val shell = markerShellPath()
    val shadowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0F172A")
            alpha = if (selected) (255 * 0.30f).toInt() else (255 * 0.20f).toInt()
        }
    val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor(markerLegendColor(typeKey, supportRequestType, glyph))
        }
    val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 2.2f
            color = Color.WHITE
        }

    if (selected) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 4.0f
            color = Color.parseColor(MARKER_SELECTED_GLOW_COLOR)
            alpha = (255 * 0.45f).toInt()
        }.also { canvas.drawPath(shell, it) }
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2.4f
            color = Color.parseColor(MARKER_SELECTED_GLOW_COLOR)
        }.also { canvas.drawPath(shell, it) }
    }

    canvas.save()
    canvas.translate(0f, 2f)
    canvas.drawPath(shell, shadowPaint)
    canvas.restore()
    canvas.drawPath(shell, fillPaint)
    canvas.drawPath(shell, strokePaint)

    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        alpha = (255 * if (selected) 0.10f else 0.06f).toInt()
    }.also { canvas.drawOval(8.8f, 6.4f, 31.2f, 23.0f, it) }

    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0F172A")
        alpha = (255 * 0.14f).toInt()
    }.also { paint ->
        val lowerShade =
            Path().apply {
                moveTo(8f, 27.5f)
                cubicTo(11.2f, 33.8f, 17f, 40.1f, 20f, 44f)
                cubicTo(22.9f, 40.3f, 28.3f, 34.4f, 31.7f, 28.3f)
                cubicTo(28.5f, 30.4f, 24.3f, 31.6f, 20f, 31.6f)
                cubicTo(15.6f, 31.6f, 11.4f, 30.2f, 8f, 27.5f)
                close()
            }
        canvas.drawPath(lowerShade, paint)
    }

    val iconPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 2.2f
            color = Color.parseColor("#F8FAFC")
        }
    canvas.save()
    canvas.translate(10f, 8.25f)
    canvas.scale(20f / 24f, 20f / 24f)
    applyMarkerGlyphPlacement(canvas, glyph)
    drawMarkerGlyph(canvas, glyph, iconPaint)
    canvas.restore()

    return bitmap
}

private fun markerShellPath(): Path =
    Path().apply {
        moveTo(20f, 44f)
        cubicTo(16.7f, 39.8f, 4f, 29.9f, 4f, 18.7f)
        cubicTo(4f, 10.4f, 11.1f, 4f, 20f, 4f)
        cubicTo(28.9f, 4f, 36f, 10.4f, 36f, 18.7f)
        cubicTo(36f, 29.9f, 23.3f, 39.8f, 20f, 44f)
        close()
    }

private fun applyMarkerGlyphPlacement(canvas: Canvas, glyph: String) {
    when (glyph) {
        "FIELD", "FOUND", "NOTE" -> canvas.translate(0f, -0.5f)
        "HAND" -> {
            canvas.translate(0f, -2f)
            canvas.scale(0.94f, 0.94f, 12f, 12f)
        }
        "HAND_HELPING" -> canvas.scale(0.9f, 0.9f, 12f, 12f)
    }
}

private fun drawMarkerGlyph(canvas: Canvas, glyph: String, paint: Paint) {
    when (glyph) {
        "CLUE" -> {
            canvas.drawCircle(11f, 11f, 8f, paint)
            canvas.drawLine(16.66f, 16.66f, 21f, 21f, paint)
        }
        "FOUND" -> {
            canvas.drawCircle(9f, 7f, 4f, paint)
            canvas.drawPath(
                Path().apply {
                    moveTo(2f, 21f)
                    lineTo(2f, 19f)
                    cubicTo(2f, 16.8f, 3.8f, 15f, 6f, 15f)
                    lineTo(12f, 15f)
                    cubicTo(14.2f, 15f, 16f, 16.8f, 16f, 19f)
                    lineTo(16f, 21f)
                },
                paint
            )
            canvas.drawLine(16f, 11f, 18f, 13f, paint)
            canvas.drawLine(18f, 13f, 22f, 9f, paint)
        }
        "FIELD" -> {
            canvas.drawPath(
                Path().apply {
                    moveTo(8f, 3f)
                    lineTo(12f, 11f)
                    lineTo(17f, 6f)
                    lineTo(22f, 21f)
                    lineTo(2f, 21f)
                    close()
                },
                paint
            )
        }
        "DRONE" -> {
            canvas.drawRoundRect(9f, 9f, 15f, 15f, 2f, 2f, paint)
            canvas.drawLine(5f, 5f, 9f, 9f, paint)
            canvas.drawLine(15f, 9f, 19f, 5f, paint)
            canvas.drawLine(5f, 19f, 9f, 15f, paint)
            canvas.drawLine(15f, 15f, 19f, 19f, paint)
            canvas.drawCircle(4f, 4f, 2f, paint)
            canvas.drawCircle(20f, 4f, 2f, paint)
            canvas.drawCircle(4f, 20f, 2f, paint)
            canvas.drawCircle(20f, 20f, 2f, paint)
        }
        "DOG" -> {
            canvas.drawCircle(12f, 13f, 6f, paint)
            canvas.drawPath(
                Path().apply {
                    moveTo(7f, 9f)
                    lineTo(5f, 4f)
                    lineTo(10f, 7f)
                    moveTo(17f, 9f)
                    lineTo(19f, 4f)
                    lineTo(14f, 7f)
                    moveTo(10.5f, 15.5f)
                    lineTo(12f, 17f)
                    lineTo(13.5f, 15.5f)
                },
                paint
            )
            canvas.drawPoint(10f, 12f, paint)
            canvas.drawPoint(14f, 12f, paint)
        }
        "HAND", "HAND_HELPING" -> {
            canvas.drawPath(
                Path().apply {
                    moveTo(11f, 12f)
                    lineTo(13f, 12f)
                    cubicTo(15.2f, 12f, 15.2f, 8f, 13f, 8f)
                    lineTo(10f, 8f)
                    cubicTo(9.4f, 8f, 8.9f, 8.2f, 8.6f, 8.6f)
                    lineTo(3f, 14f)
                    moveTo(7f, 18f)
                    lineTo(8.6f, 16.6f)
                    cubicTo(8.9f, 16.2f, 9.4f, 16f, 10f, 16f)
                    lineTo(14f, 16f)
                    cubicTo(15.1f, 16f, 16.1f, 15.6f, 16.8f, 14.8f)
                    lineTo(21.4f, 10.4f)
                    moveTo(2f, 13f)
                    lineTo(8f, 19f)
                },
                paint
            )
        }
        else -> {
            canvas.drawLine(2f, 6f, 6f, 6f, paint)
            canvas.drawLine(2f, 10f, 6f, 10f, paint)
            canvas.drawLine(2f, 14f, 6f, 14f, paint)
            canvas.drawLine(2f, 18f, 6f, 18f, paint)
            canvas.drawRoundRect(4f, 2f, 20f, 22f, 2f, 2f, paint)
            canvas.drawLine(9.5f, 8f, 14.5f, 8f, paint)
            canvas.drawLine(9.5f, 12f, 16f, 12f, paint)
            canvas.drawLine(9.5f, 16f, 14f, 16f, paint)
        }
    }
}

private fun String?.markerTypeKey(): String =
    when (orEmpty().uppercase()) {
        "CLUE" -> "CLUE"
        "PERSON_FOUND" -> "PERSON_FOUND"
        "FIELD_CONDITION" -> "FIELD_CONDITION"
        "SUPPORT_REQUEST" -> "SUPPORT_REQUEST"
        "NOTE" -> "NOTE"
        else -> "UNKNOWN"
    }

private fun markerGlyphName(typeKey: String, supportRequestType: String?): String =
    if (typeKey == "SUPPORT_REQUEST") {
        when (supportRequestType.orEmpty().uppercase()) {
            "DRONE" -> "DRONE"
            "POLICE_DOG" -> "DOG"
            else -> "HAND_HELPING"
        }
    } else {
        when (typeKey) {
            "CLUE" -> "CLUE"
            "PERSON_FOUND" -> "FOUND"
            "FIELD_CONDITION" -> "FIELD"
            "NOTE" -> "NOTE"
            else -> "NOTE"
        }
    }

private fun markerLegendColor(typeKey: String, supportRequestType: String?, glyph: String): String =
    when {
        typeKey == "SUPPORT_REQUEST" && (supportRequestType.orEmpty().uppercase() == "DRONE" || glyph == "DRONE") -> "#06B6D4"
        typeKey == "SUPPORT_REQUEST" && (supportRequestType.orEmpty().uppercase() == "POLICE_DOG" || glyph == "DOG") -> "#F472B6"
        typeKey == "SUPPORT_REQUEST" -> "#8B5CF6"
        typeKey == "CLUE" -> "#F59E0B"
        typeKey == "PERSON_FOUND" -> "#EF4444"
        typeKey == "FIELD_CONDITION" -> "#22C55E"
        typeKey == "NOTE" -> "#3B82F6"
        else -> "#64748B"
    }

private fun currentLocationBitmap(
    fillColor: String,
    strokeColor: String,
    withHeading: Boolean
): Bitmap {
    val bitmap =
        Bitmap.createBitmap(
            CURRENT_LOCATION_IMAGE_SIZE_PX,
            CURRENT_LOCATION_IMAGE_SIZE_PX,
            Bitmap.Config.ARGB_8888
        )
    val canvas = Canvas(bitmap)
    val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor(fillColor)
        }
    val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = CURRENT_LOCATION_MARKER_STROKE_WIDTH_PX
            color = Color.parseColor(strokeColor)
        }
    val center = CURRENT_LOCATION_IMAGE_SIZE_PX / 2f
    if (withHeading) {
        val headingPath =
            Path().apply {
                moveTo(center, center - 34.3f)
                cubicTo(center - 8.12f, center - 27.98f, center - 12.64f, center - 19.86f, center - 16.25f, center + 3.61f)
                quadTo(center, center - 2.71f, center + 16.25f, center + 3.61f)
                cubicTo(center + 12.64f, center - 19.86f, center + 8.12f, center - 27.98f, center, center - 34.3f)
                close()
            }
        canvas.drawPath(headingPath, fillPaint)
        canvas.drawPath(headingPath, strokePaint)
    }
    canvas.drawCircle(center, center, CURRENT_LOCATION_MARKER_RADIUS_PX, fillPaint)
    canvas.drawCircle(center, center, CURRENT_LOCATION_MARKER_RADIUS_PX, strokePaint)
    return bitmap
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
            MapLibreGeometryOverlayKind.CurrentLocation -> false
        }

private val MapLibreGeometryOverlay.supportsLineLayer: Boolean
    get() =
        when (kind) {
            MapLibreGeometryOverlayKind.Overall -> true
            MapLibreGeometryOverlayKind.Unit -> true
            MapLibreGeometryOverlayKind.Team -> true
            MapLibreGeometryOverlayKind.Path -> true
            MapLibreGeometryOverlayKind.Marker -> false
            MapLibreGeometryOverlayKind.CurrentLocation -> false
        }

private val MapLibreGeometryOverlay.supportsCircleLayer: Boolean
    get() =
        when (kind) {
            MapLibreGeometryOverlayKind.Overall -> false
            MapLibreGeometryOverlayKind.Unit -> false
            MapLibreGeometryOverlayKind.Team -> false
            MapLibreGeometryOverlayKind.Path -> false
            MapLibreGeometryOverlayKind.Marker -> false
            MapLibreGeometryOverlayKind.CurrentLocation -> false
        }

private val MapLibreGeometryOverlay.supportsMarkerIconLayer: Boolean
    get() = kind == MapLibreGeometryOverlayKind.Marker

private val MapLibreGeometryOverlay.supportsLabelLayer: Boolean
    get() =
        kind != MapLibreGeometryOverlayKind.Marker && !label.isNullOrBlank()

private val MapLibreGeometryOverlay.supportsHeadingLayer: Boolean
    get() =
        kind == MapLibreGeometryOverlayKind.CurrentLocation

private val MapLibreGeometryOverlay.labelPlacement: String
    get() =
        when (kind) {
            MapLibreGeometryOverlayKind.Path -> SYMBOL_PLACEMENT_LINE
            else -> SYMBOL_PLACEMENT_POINT
        }

internal class MapViewLifecycleBridge(
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
private const val MAP_FOREGROUND_LOAD_COLOR = -15194566
private const val INITIAL_BOUNDS_PADDING_PX = 64
