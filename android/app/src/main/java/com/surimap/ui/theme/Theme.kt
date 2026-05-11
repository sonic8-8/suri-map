package com.surimap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors =
    darkColorScheme(
        primary = PoliPrimary,
        onPrimary = PoliFgPrimary,
        primaryContainer = PoliPrimarySoft,
        onPrimaryContainer = PoliPrimaryFg,
        secondary = PoliPrimaryMid,
        onSecondary = PoliFgPrimary,
        background = PoliBgBase,
        onBackground = PoliFgPrimary,
        surface = PoliBgSurface,
        onSurface = PoliFgPrimary,
        surfaceVariant = PoliBgInput,
        onSurfaceVariant = PoliFgSecondary,
        surfaceContainer = PoliBgSurface,
        surfaceContainerHigh = PoliBgElevated,
        surfaceContainerHighest = PoliBgInput,
        outline = PoliBorder,
        outlineVariant = PoliBorderStrong,
        error = PoliEmphasis,
        onError = PoliFgPrimary
    )

@Composable
fun SuriMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = PoliTypography,
        shapes = PoliShapes,
        content = content
    )
}
