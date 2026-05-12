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
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

data class MapLibreRuntimeMapState(
    val apiBaseUrl: String = BuildConfig.SURI_MAP_API_BASE_URL,
    val styleId: String = "osm-local",
    val accessToken: String? = null,
    val policePhoneId: String? = null
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
            if (appliedStyleUrl != styleUrl) {
                installMapLibreTileHttp(state)
                view.getMapAsync { mapLibreMap ->
                    mapLibreMap.uiSettings.setAttributionEnabled(true)
                    mapLibreMap.uiSettings.setLogoEnabled(true)
                    mapLibreMap.setStyle(styleUrl) {
                        appliedStyleUrl = styleUrl
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
