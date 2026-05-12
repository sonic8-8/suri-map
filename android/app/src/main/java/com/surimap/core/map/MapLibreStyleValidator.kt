package com.surimap.core.map

import org.json.JSONObject

enum class MapLibreStyleValidationError {
    MISSING_OSM_ATTRIBUTION,
    EXTERNAL_TILE_URL
}

data class MapLibreStyleValidationResult(
    val errors: List<MapLibreStyleValidationError>
) {
    val isValid: Boolean = errors.isEmpty()
}

class MapLibreStyleValidator {

    fun validate(styleJson: String, allowedTileBaseUrl: String): MapLibreStyleValidationResult {
        val style = JSONObject(styleJson)
        val errors = buildList {
            if (!hasOsmAttribution(style)) {
                add(MapLibreStyleValidationError.MISSING_OSM_ATTRIBUTION)
            }
            if (hasExternalTileUrl(style, allowedTileBaseUrl)) {
                add(MapLibreStyleValidationError.EXTERNAL_TILE_URL)
            }
        }
        return MapLibreStyleValidationResult(errors)
    }

    private fun hasOsmAttribution(style: JSONObject): Boolean {
        val attribution = style.optJSONObject("metadata")?.optString("attribution").orEmpty()
        return attribution.contains("OpenStreetMap", ignoreCase = true)
    }

    private fun hasExternalTileUrl(style: JSONObject, allowedTileBaseUrl: String): Boolean {
        val sources = style.optJSONObject("sources") ?: return false
        val allowedBaseUrl = allowedTileBaseUrl.trimEnd('/').removeSuffix("/api")

        return sources.keys().asSequence().any { sourceKey ->
            val source = sources.optJSONObject(sourceKey) ?: return@any false
            val tiles = source.optJSONArray("tiles") ?: return@any false
            (0 until tiles.length()).any { index ->
                val tileUrl = tiles.optString(index)
                !isLocalTileUrl(tileUrl, allowedBaseUrl)
            }
        }
    }

    private fun isLocalTileUrl(tileUrl: String, allowedBaseUrl: String): Boolean {
        return tileUrl.startsWith("/tiles/") ||
            tileUrl.startsWith("$allowedBaseUrl/tiles/") ||
            tileUrl.startsWith("local://tiles/")
    }
}
