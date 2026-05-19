package com.surimap.feature.search.domain

data class AssignedSearchAreaBoundary(
    val searchAreaId: String,
    val label: String,
    val geoJson: String
)

data class SearchAreaBoundaryFix(
    val lon: Double,
    val lat: Double
)

sealed interface SearchAreaBoundarySignal {
    data class Exited(val boundary: AssignedSearchAreaBoundary) : SearchAreaBoundarySignal
    data class Reentered(val boundary: AssignedSearchAreaBoundary?) : SearchAreaBoundarySignal
}

class SearchAreaBoundaryMonitor(
    private val repeatCooldownMs: Long = DEFAULT_REPEAT_COOLDOWN_MS
) {
    private var exitedBoundaryId: String? = null
    private var lastExitAlertAtMs: Long = Long.MIN_VALUE

    fun evaluate(
        boundaries: List<AssignedSearchAreaBoundary>,
        fix: SearchAreaBoundaryFix,
        nowMs: Long
    ): SearchAreaBoundarySignal? {
        if (boundaries.isEmpty()) {
            exitedBoundaryId = null
            return null
        }

        val insideBoundary = boundaries.firstOrNull { boundary -> boundary.contains(fix) }
        if (insideBoundary != null) {
            val wasOutside = exitedBoundaryId != null
            exitedBoundaryId = null
            return if (wasOutside) SearchAreaBoundarySignal.Reentered(insideBoundary) else null
        }

        val boundary = boundaries.first()
        val shouldNotify =
            exitedBoundaryId != boundary.searchAreaId ||
                lastExitAlertAtMs == Long.MIN_VALUE ||
                nowMs - lastExitAlertAtMs >= repeatCooldownMs
        if (!shouldNotify) {
            return null
        }

        exitedBoundaryId = boundary.searchAreaId
        lastExitAlertAtMs = nowMs
        return SearchAreaBoundarySignal.Exited(boundary)
    }

    private fun AssignedSearchAreaBoundary.contains(fix: SearchAreaBoundaryFix): Boolean {
        val ring = geoJson.outerRingLonLatPairs()
        if (ring.size < 4) {
            return true
        }

        var inside = false
        var previous = ring.lastIndex
        for (current in ring.indices) {
            val currentPoint = ring[current]
            val previousPoint = ring[previous]
            val currentLon = currentPoint.lon
            val currentLat = currentPoint.lat
            val previousLon = previousPoint.lon
            val previousLat = previousPoint.lat

            val intersects =
                (currentLat > fix.lat) != (previousLat > fix.lat) &&
                    fix.lon < (previousLon - currentLon) * (fix.lat - currentLat) /
                    (previousLat - currentLat) + currentLon
            if (intersects) {
                inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun String.outerRingLonLatPairs(): List<LonLat> {
        val coordinatesIndex = indexOf("\"coordinates\"")
        if (coordinatesIndex < 0) {
            return emptyList()
        }
        val values =
            NUMBER_REGEX.findAll(substring(coordinatesIndex))
                .mapNotNull { match -> match.value.toDoubleOrNull() }
                .toList()
        if (values.size < 8) {
            return emptyList()
        }
        return values.chunked(2)
            .filter { pair -> pair.size == 2 }
            .map { pair -> LonLat(lon = pair[0], lat = pair[1]) }
    }

    private data class LonLat(val lon: Double, val lat: Double)

    private companion object {
        const val DEFAULT_REPEAT_COOLDOWN_MS = 60_000L
        val NUMBER_REGEX = Regex("-?\\d+(?:\\.\\d+)?")
    }
}
