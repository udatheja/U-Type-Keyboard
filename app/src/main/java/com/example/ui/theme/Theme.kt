package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.udara.Utype.data.KeyboardThemeId

private val UTypeDarkColorScheme = darkColorScheme(
    primary = EmeraldAccentDark,
    onPrimary = EmeraldOnAccentDark,
    primaryContainer = EmeraldCardElevatedDark,
    onPrimaryContainer = EmeraldAccentDark,
    secondary = EmeraldMutedDark,
    onSecondary = EmeraldBgDark,
    background = EmeraldBgDark,
    onBackground = EmeraldTextDark,
    surface = EmeraldSurfaceDark,
    onSurface = EmeraldTextDark,
    surfaceVariant = EmeraldCardDark,
    onSurfaceVariant = EmeraldMutedDark,
    outline = EmeraldOutlineDark,
    outlineVariant = EmeraldCardDark,
)

private val UTypeLightColorScheme = lightColorScheme(
    primary = MintAccentLight,
    onPrimary = MintOnAccentLight,
    primaryContainer = MintSurfaceLight,
    onPrimaryContainer = MintAccentLight,
    secondary = MintMutedLight,
    onSecondary = MintCardLight,
    background = MintBgLight,
    onBackground = MintTextLight,
    surface = MintCardLight,
    onSurface = MintTextLight,
    surfaceVariant = MintSurfaceLight,
    onSurfaceVariant = MintMutedLight,
    outline = MintOutlineLight,
    outlineVariant = MintSurfaceLight,
)

@Composable
fun UTypeTheme(
    themeId: KeyboardThemeId = KeyboardThemeId.DARK,
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
