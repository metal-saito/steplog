package com.cellomsai.steplog.ui.theme

import androidx.compose.ui.graphics.Color

// System (warm/cream) palette
val PrimarySage = Color(0xFF8FA68E)
val SecondaryFuji = Color(0xFFAFA3C7)
val BackgroundCream = Color(0xFFFAF7F2)
val SurfaceWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF3A3A3A)
val TextSecondary = Color(0xFF8A8A8A)
val DividerSoft = Color(0xFFE8E5E0)

// Light (cool/white) palette
val PrimarySlate = Color(0xFF7A9BAF)
val SecondaryLavender = Color(0xFFA0A8C8)
val BackgroundWhite = Color(0xFFFFFFFF)
val SurfaceLight = Color(0xFFF5F7FA)
val TextPrimaryLight = Color(0xFF2D2D2D)
val TextSecondaryLight = Color(0xFF7A8A98)
val DividerLight = Color(0xFFDDE3EA)

// Dark Mode
val DarkBackground = Color(0xFF1A1A1A)
val DarkSurface = Color(0xFF2A2A2A)
val DarkTextPrimary = Color(0xFFE0E0E0)
val DarkPrimarySage = Color(0xFF6D8A6C)
val DarkSecondaryFuji = Color(0xFF8D82A5)

// Condition level colors (0=calm → 5=very difficult)
val ConditionColors = listOf(
    Color(0xFFE3EDE0),
    Color(0xFFD0E0CC),
    Color(0xFFB8D0B2),
    Color(0xFFEFD9B8),
    Color(0xFFE5BFAF),
    Color(0xFFD6A5A0)
)

// Material3 roles – System light (warm cream)
val md_theme_system_primary = PrimarySage
val md_theme_system_onPrimary = Color(0xFFFFFFFF)
val md_theme_system_primaryContainer = Color(0xFFD4E8D2)
val md_theme_system_onPrimaryContainer = Color(0xFF1A3019)
val md_theme_system_secondary = SecondaryFuji
val md_theme_system_onSecondary = Color(0xFFFFFFFF)
val md_theme_system_secondaryContainer = Color(0xFFE8E3F5)
val md_theme_system_onSecondaryContainer = Color(0xFF2A2040)
val md_theme_system_background = BackgroundCream
val md_theme_system_onBackground = TextPrimary
val md_theme_system_surface = SurfaceWhite
val md_theme_system_onSurface = TextPrimary
val md_theme_system_surfaceVariant = Color(0xFFF0EDE8)
val md_theme_system_onSurfaceVariant = TextSecondary
val md_theme_system_outline = DividerSoft
val md_theme_system_error = Color(0xFFB85C5C)
val md_theme_system_onError = Color(0xFFFFFFFF)

// Material3 roles – Light (cool white)
val md_theme_light_primary = PrimarySlate
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFCFE4F0)
val md_theme_light_onPrimaryContainer = Color(0xFF102030)
val md_theme_light_secondary = SecondaryLavender
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE2E6F8)
val md_theme_light_onSecondaryContainer = Color(0xFF1A1F38)
val md_theme_light_background = BackgroundWhite
val md_theme_light_onBackground = TextPrimaryLight
val md_theme_light_surface = SurfaceLight
val md_theme_light_onSurface = TextPrimaryLight
val md_theme_light_surfaceVariant = Color(0xFFEAEEF4)
val md_theme_light_onSurfaceVariant = TextSecondaryLight
val md_theme_light_outline = DividerLight
val md_theme_light_error = Color(0xFFB85C5C)
val md_theme_light_onError = Color(0xFFFFFFFF)

// Material3 roles – Dark
val md_theme_dark_primary = DarkPrimarySage
val md_theme_dark_onPrimary = Color(0xFF0D1F0D)
val md_theme_dark_primaryContainer = Color(0xFF2D4A2C)
val md_theme_dark_onPrimaryContainer = Color(0xFFB8D4B6)
val md_theme_dark_secondary = DarkSecondaryFuji
val md_theme_dark_onSecondary = Color(0xFF1A1525)
val md_theme_dark_secondaryContainer = Color(0xFF3A3050)
val md_theme_dark_onSecondaryContainer = Color(0xFFD0CAE8)
val md_theme_dark_background = DarkBackground
val md_theme_dark_onBackground = DarkTextPrimary
val md_theme_dark_surface = DarkSurface
val md_theme_dark_onSurface = DarkTextPrimary
val md_theme_dark_surfaceVariant = Color(0xFF3A3A3A)
val md_theme_dark_onSurfaceVariant = Color(0xFFB0B0B0)
val md_theme_dark_outline = Color(0xFF4A4A4A)
val md_theme_dark_error = Color(0xFFCF8080)
val md_theme_dark_onError = Color(0xFF1A0000)
