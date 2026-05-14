package com.surimap.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object MarkerDetailDeepLink {
    const val MarkerIdArg: String = "markerId"
    val RoutePattern: String = "${PolicePhoneRoute.MarkerDetail.route}/{$MarkerIdArg}"

    fun route(markerId: String): String {
        val id = markerId.takeIf(String::isNotBlank) ?: return PolicePhoneRoute.MarkerDetail.route
        return "${PolicePhoneRoute.MarkerDetail.route}/${encode(id)}"
    }

    fun markerIdFromRoute(route: String): String? {
        val prefix = "${PolicePhoneRoute.MarkerDetail.route}/"
        if (!route.startsWith(prefix)) {
            return null
        }
        return decode(route.removePrefix(prefix)).takeIf(String::isNotBlank)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
}
