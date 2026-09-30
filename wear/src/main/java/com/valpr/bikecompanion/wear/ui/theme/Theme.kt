package com.valpr.bikecompanion.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val WearPrimary = Color(0xFF00E676)
val WearPrimaryVariant = Color(0xFF00B0FF)
val WearSecondary = Color(0xFFFFB300)
val WearError = Color(0xFFFF1744)
val WearBackground = Color(0xFF000000)
val WearSurface = Color(0xFF121212)
val WearOnPrimary = Color(0xFF000000)
val WearOnBackground = Color(0xFFFFFFFF)

private val WearColorPalette = Colors(
    primary = WearPrimary,
    primaryVariant = WearPrimaryVariant,
    secondary = WearSecondary,
    error = WearError,
    background = WearBackground,
    surface = WearSurface,
    onPrimary = WearOnPrimary,
    onBackground = WearOnBackground,
    onSurface = WearOnBackground
)

@Composable
fun BikeCompanionWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColorPalette,
        content = content
    )
}
