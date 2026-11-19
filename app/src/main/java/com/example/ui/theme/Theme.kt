package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.udara.Utype.data.KeyboardThemeId

private val UTypeDarkColorScheme = darkColorScheme(
    primary = MatteAccentDark,
    onPrimary = MatteOnAccentDark,
    primaryContainer = MatteCardDark,
    onPrimaryContainer = MatteTextDark,
    secondary = MatteMutedDark,
    onSecondary = MatteBgDark,
    background = MatteBgDark,
    onBackground = MatteTextDark,
    surface = MatteSurfaceDark,
    onSurface = MatteTextDark,
    surfaceVariant = MatteCardDark,
    onSurfaceVariant = MatteMutedDark,
    outline = MatteDividerDark,
    outlineVariant = MatteDividerDark,
)

private val UTypeLightColorScheme = lightColorScheme(
    primary = MatteAccentLight,
    onPrimary = MatteOnAccentLight,
    primaryContainer = MatteCardLight,
    onPrimaryContainer = MatteTextLight,
    secondary = MatteMutedLight,
    onSecondary = MatteSurfaceLight,
    background = MatteBgLight,
    onBackground = MatteTextLight,
    surface = MatteSurfaceLight,
    onSurface = MatteTextLight,
    surfaceVariant = MatteCardLight,
    onSurfaceVariant = MatteMutedLight,
    outline = MatteDividerLight,
    outlineVariant = MatteDividerLight,
)

@Composable
fun UTypeTheme(
    themeId: KeyboardThemeId = KeyboardThemeId.CLASSIC_DARK,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeId) {
        KeyboardThemeId.LIGHT -> false
        KeyboardThemeId.SYSTEM -> systemDark
        else -> true
    }

    MaterialTheme(
        colorScheme = if (useDark) UTypeDarkColorScheme else UTypeLightColorScheme,
        typography = Typography,
        content = content,
    )
}
