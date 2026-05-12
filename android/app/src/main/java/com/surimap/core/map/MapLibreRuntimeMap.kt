package com.surimap.core.map

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
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
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
    Path
}

data class MapLibreGeometryOverlay(
    val id: String,
    val kind: MapLibreGeometryOverlayKind,
    val geoJson: String,
    val highlighted: Boolean = false
) {
    fun signature(): String = "${kind.name}:$id:$highlighted:$geoJson"
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
                    installMapLibreTileHttp(state)
                    appliedOverlaySignature = null
                    appliedOverlayStyleIds = emptySet()
                    appliedCameraSignature = null
                    mapLibreMap.setStyle(styleUrl) {
                        appliedStyleUrl = styleUrl
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

private fun installMapLibreTileHttp(state: MapLibreRuntimeMapState) {
    MapLibreTileHttpInstaller.install(
        MapLibreTileCallFactory(
            tileBaseUrl = state.apiBaseUrl,
            accessTokenProvider = { state.accessToken },
            policePhoneIdProvider = { state.policePhoneId }
        )
    )
}

private val MapLibreGeometryOverlay.styleId: String
    get() = "suri-${kind.name.lowercase()}-${id.ifBlank { kind.name }.replace(UNSAFE_STYLE_ID_CHARS, "-")}"

private val MapLibreGeometryOverlay.sourceId: String
    get() = "$styleId-source"

private val MapLibreGeometryOverlay.fillLayerId: String
    get() = "$styleId-fill"

private val MapLibreGeometryOverlay.lineLayerId: String
    get() = "$styleId-line"

private fun MapLibreRuntimeMapState.geometryOverlaySignature(): String =
    geometryOverlays.joinToString("|") { it.signature() }

private fun Style.upsertGeometryOverlay(overlay: MapLibreGeometryOverlay) {
    val sourceJson = runCatching { overlay.featureCollectionJson() }.getOrNull() ?: return
    val source = getSourceAs<GeoJsonSource>(overlay.sourceId)
    if (source == null) {
        addSource(GeoJsonSource(overlay.sourceId, sourceJson))
    } else {
        source.setGeoJson(sourceJson)
    }

    if (overlay.supportsFillLayer && getLayer(overlay.fillLayerId) == null) {
        addLayer(
            FillLayer(overlay.fillLayerId, overlay.sourceId).withProperties(
                fillColor(overlay.fillColor),
                fillOpacity(overlay.fillOpacity),
                fillOutlineColor(overlay.lineColor)
            )
        )
    }
    if (getLayer(overlay.lineLayerId) == null) {
        addLayer(
            LineLayer(overlay.lineLayerId, overlay.sourceId).withProperties(
                lineColor(overlay.lineColor),
                lineWidth(if (overlay.highlighted) 3.5f else 2.25f),
                lineOpacity(0.88f)
            )
        )
    }
}

private fun Style.removeGeometryOverlays(styleIds: Set<String>) {
    styleIds.forEach { styleId ->
        removeLayer("$styleId-line")
        removeLayer("$styleId-fill")
        removeSource("$styleId-source")
    }
}

private fun MapLibreGeometryOverlay.featureCollectionJson(): String =
    JSONObject()
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
                    )
                    .put("geometry", JSONObject(geoJson))
            )
        )
        .toString()

private val MapLibreGeometryOverlay.supportsFillLayer: Boolean
    get() =
        when (kind) {
            MapLibreGeometryOverlayKind.Overall -> true
            MapLibreGeometryOverlayKind.Unit -> true
            MapLibreGeometryOverlayKind.Team -> true
            MapLibreGeometryOverlayKind.Path -> false
        }

private val MapLibreGeometryOverlay.fillColor: String
    get() =
        when (kind) {
            MapLibreGeometryOverlayKind.Overall -> "#1D4ED8"
            MapLibreGeometryOverlayKind.Unit -> "#047857"
            MapLibreGeometryOverlayKind.Team -> "#C2410C"
            MapLibreGeometryOverlayKind.Path -> "#2563EB"
        }

private val MapLibreGeometryOverlay.lineColor: String
    get() =
        when (kind) {
            MapLibreGeometryOverlayKind.Overall -> "#1E40AF"
            MapLibreGeometryOverlayKind.Unit -> "#065F46"
            MapLibreGeometryOverlayKind.Team -> "#9A3412"
            MapLibreGeometryOverlayKind.Path -> "#2563EB"
        }

private val MapLibreGeometryOverlay.fillOpacity: Float
    get() =
        when (kind) {
            MapLibreGeometryOverlayKind.Overall -> 0.10f
            MapLibreGeometryOverlayKind.Unit -> 0.13f
            MapLibreGeometryOverlayKind.Team -> 0.18f
            MapLibreGeometryOverlayKind.Path -> 0.0f
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
