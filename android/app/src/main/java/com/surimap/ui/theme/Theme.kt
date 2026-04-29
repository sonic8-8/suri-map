package com.surimap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SuriGreen,
    secondary = SuriOrange,
    error = SuriRed,
    background = SuriBackground,
    surface = SuriBackground,
)

@Composable
fun SuriMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}

