package com.stopaddict.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = NeutralWhite,
    primaryContainer = GreenSoft,
    onPrimaryContainer = GreenDeep,
    secondary = GreenAccent,
    onSecondary = NeutralWhite,
    background = NeutralLight,
    onBackground = NeutralDark,
    surface = NeutralWhite,
    onSurface = NeutralDark,
    error = StatusDanger,
)

private val DarkColors = darkColorScheme(
    primary = GreenSoft,
    onPrimary = GreenDeep,
    primaryContainer = GreenDeep,
    onPrimaryContainer = GreenSoft,
    secondary = GreenAccent,
    onSecondary = NeutralWhite,
    background = NeutralDark,
    onBackground = NeutralLight,
    surface = SurfaceDark,
    onSurface = NeutralLight,
    error = StatusDanger,
)

@Composable
fun StopAddictTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
