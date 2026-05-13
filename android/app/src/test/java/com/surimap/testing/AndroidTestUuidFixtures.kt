package com.surimap.testing

import java.nio.charset.StandardCharsets
import java.util.UUID

fun incidentIdFixture(alias: String): String = uuidFixture("incident", alias)

fun opIdFixture(alias: String): String = uuidFixture("op", alias)

fun policePhoneIdFixture(alias: String): String = uuidFixture("police-phone", alias)

fun markerIdFixture(alias: String): String = uuidFixture("marker", alias)

fun photoIdFixture(alias: String): String = uuidFixture("photo", alias)

fun pathIdFixture(alias: String): String = uuidFixture("path", alias)

fun dutyShiftIdFixture(alias: String): String = uuidFixture("duty-shift", alias)

fun manifestIdFixture(alias: String): String = uuidFixture("manifest", alias)

fun areaIdFixture(alias: String): String = uuidFixture("area", alias)

fun operationIdFixture(alias: String): String =
    uuidFixture("operation", alias)

fun operationIdFactory(vararg aliases: String): (String) -> String {
    val values = aliases.map(::operationIdFixture)
    var next = 0
    return { prefix ->
        check(next < values.size) { "No operationId fixture for prefix: $prefix" }
        values[next++]
    }
}

private fun uuidFixture(namespace: String, alias: String): String =
    UUID.nameUUIDFromBytes("suri-map:$namespace:$alias".toByteArray(StandardCharsets.UTF_8)).toString()
