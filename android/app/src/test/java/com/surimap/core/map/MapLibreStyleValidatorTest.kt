package com.surimap.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapLibreStyleValidatorTest {

    @Test
    fun acceptsStyleWhenSourcesUseLocalTilesAndAttributionIsPresent() {
        val result = MapLibreStyleValidator().validate(
            styleJson = """
                {
                  "version": 8,
                  "sources": {
                    "suri-map": {
                      "type": "vector",
                      "tiles": ["https://suri-map.example.com/tiles/osm-local/{z}/{x}/{y}.pbf"]
                    },
                    "relative": {
                      "type": "vector",
                      "tiles": ["/tiles/osm-local/{z}/{x}/{y}.pbf"]
                    }
                  },
                  "layers": [],
                  "metadata": {"attribution": "OpenStreetMap contributors"}
                }
            """.trimIndent(),
            allowedTileBaseUrl = "https://suri-map.example.com/api"
        )

        assertTrue(result.isValid)
        assertEquals(emptyList<MapLibreStyleValidationError>(), result.errors)
    }

    @Test
    fun rejectsExternalTileHostsAndMissingAttribution() {
        val result = MapLibreStyleValidator().validate(
            styleJson = """
                {
                  "version": 8,
                  "sources": {
                    "external": {
                      "type": "vector",
                      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.pbf"]
                    }
                  },
                  "layers": [],
                  "metadata": {}
                }
            """.trimIndent(),
            allowedTileBaseUrl = "https://suri-map.example.com/api"
        )

        assertEquals(
            listOf(
                MapLibreStyleValidationError.MISSING_OSM_ATTRIBUTION,
                MapLibreStyleValidationError.EXTERNAL_TILE_URL
            ),
            result.errors
        )
    }
}
