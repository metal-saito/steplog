package com.cellomsai.steplog.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// System theme: warm cream, used when following device setting in light mode
private val SystemLightColorScheme = lightColorScheme(
    primary = md_theme_system_primary,
    onPrimary = md_theme_system_onPrimary,
    primaryContainer = md_theme_system_primaryContainer,
    onPrimaryContainer = md_theme_system_onPrimaryContainer,
    secondary = md_theme_system_secondary,
    onSecondary = md_theme_system_onSecondary,
    secondaryContainer = md_theme_system_secondaryContainer,
    onSecondaryContainer = md_theme_system_onSecondaryContainer,
    background = md_theme_system_background,
    onBackground = md_theme_system_onBackground,
    surface = md_theme_system_surface,
    onSurface = md_theme_system_onSurface,
    surfaceVariant = md_theme_system_surfaceVariant,
    onSurfaceVariant = md_theme_system_onSurfaceVariant,
    outline = md_theme_system_outline,
    error = md_theme_system_error,
    onError = md_theme_system_onError,
)

// Light theme: cool white
private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
)

enum class AppTheme { SYSTEM, LIGHT, DARK }

@Composable
fun StepLogTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    // Dynamic color is intentionally disabled: our custom palette is central to the quiet design.
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    val colorScheme = when {
        darkTheme -> DarkColorScheme
        appTheme == AppTheme.LIGHT -> LightColorScheme
        else -> SystemLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StepLogTypography,
        content = content,
    )
}
