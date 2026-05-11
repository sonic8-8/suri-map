package com.surimap.core.sync

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant

internal fun canonicalBodyHash(payload: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(payload.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
    return "sha256:$digest"
}

internal fun jsonObject(vararg fields: Pair<String, String?>): String {
    return fields
        .filter { (_, value) -> value != null }
        .joinToString(separator = ",", prefix = "{", postfix = "}") { (name, value) ->
            "${jsonString(name)}:$value"
        }
}

internal fun jsonArray(values: List<String>): String {
    return values.joinToString(separator = ",", prefix = "[", postfix = "]")
}

internal fun jsonString(value: String): String {
    return buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
}

internal fun jsonInstant(value: Instant): String = jsonString(value.toString())

internal fun jsonNumber(value: Long): String = value.toString()

internal fun jsonNumber(value: Int): String = value.toString()

internal fun jsonNumber(value: Double): String {
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

internal fun jsonBoolean(value: Boolean): String = value.toString()
