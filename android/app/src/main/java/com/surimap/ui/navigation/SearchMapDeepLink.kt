package com.surimap.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object SearchMapDeepLink {
    const val FocusMarkerIdArg: String = "focusMarkerId"
    val RoutePattern: String = "${PolicePhoneRoute.SearchMap.route}?$FocusMarkerIdArg={$FocusMarkerIdArg}"

    fun markerFocusRoute(markerId: String): String {
        val id = markerId.takeIf(String::isNotBlank) ?: return PolicePhoneRoute.SearchMap.route
        return "${PolicePhoneRoute.SearchMap.route}?$FocusMarkerIdArg=${encode(id)}"
    }

    fun focusMarkerIdFromRoute(route: String): String? {
        if (!route.startsWith("${PolicePhoneRoute.SearchMap.route}?")) {
            return null
        }
        val query = route.substringAfter("?", missingDelimiterValue = "")
        return query
            .split("&")
            .firstNotNullOfOrNull { part ->
                val key = part.substringBefore("=", missingDelimiterValue = "")
                if (key != FocusMarkerIdArg) {
                    null
                } else {
                    decode(part.substringAfter("=", missingDelimiterValue = "")).takeIf(String::isNotBlank)
                }
            }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
}
