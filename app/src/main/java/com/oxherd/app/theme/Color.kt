package com.oxherd.app.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * Semantic design tokens for the Oxherd application theme.
 */
data class OxherdColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentContainer: Color,
    val accentContent: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val error: Color
)

val DarkOxherdColors = OxherdColors(
    isDark = true,
    background = Color(0xFF0C0E14),
    surface = Color(0xFF141823),
    surfaceVariant = Color(0xFF1C2230),
    surfaceElevated = Color(0xFF222838),
    border = Color(0xFF263044),
    borderSubtle = Color(0xFF1A2230),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B),
    accent = Color(0xFF3B82F6),
    accentContainer = Color(0xFF1E3A5F),
    accentContent = Color(0xFF93C5FD),
    success = Color(0xFF10B981),
    successContainer = Color(0xFF143826),
    warning = Color(0xFFF59E0B),
    error = Color(0xFFEF4444)
)

val LightOxherdColors = OxherdColors(
    isDark = false,
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    surfaceElevated = Color(0xFFFFFFFF),
    border = Color(0xFFE2E8F0),
    borderSubtle = Color(0xFFEEF2F6),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF94A3B8),
    accent = Color(0xFF2563EB),
    accentContainer = Color(0xFFDBEAFE),
    accentContent = Color(0xFF1D4ED8),
    success = Color(0xFF059669),
    successContainer = Color(0xFFD1FAE5),
    warning = Color(0xFFD97706),
    error = Color(0xFFDC2626)
)

val LocalOxherdColors = staticCompositionLocalOf<OxherdColors> {
    DarkOxherdColors
}
