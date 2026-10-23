package com.oxherd.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

object OxherdTheme {
    val colors: OxherdColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOxherdColors.current
}

@Composable
fun OxherdTheme(
    themeMode: String = "SYSTEM",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }

    val oxherdColors = if (isDark) DarkOxherdColors else LightOxherdColors

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> darkColorScheme(
            primary = oxherdColors.accent,
            onPrimary = Color.White,
            primaryContainer = oxherdColors.accentContainer,
            onPrimaryContainer = oxherdColors.accentContent,
            background = oxherdColors.background,
            onBackground = oxherdColors.textPrimary,
            surface = oxherdColors.surface,
            onSurface = oxherdColors.textPrimary,
            surfaceVariant = oxherdColors.surfaceVariant,
            onSurfaceVariant = oxherdColors.textSecondary,
            outline = oxherdColors.border,
            outlineVariant = oxherdColors.borderSubtle,
            error = oxherdColors.error
        )
        else -> lightColorScheme(
            primary = oxherdColors.accent,
            onPrimary = Color.White,
            primaryContainer = oxherdColors.accentContainer,
            onPrimaryContainer = oxherdColors.accentContent,
            background = oxherdColors.background,
            onBackground = oxherdColors.textPrimary,
            surface = oxherdColors.surface,
            onSurface = oxherdColors.textPrimary,
            surfaceVariant = oxherdColors.surfaceVariant,
            onSurfaceVariant = oxherdColors.textSecondary,
            outline = oxherdColors.border,
            outlineVariant = oxherdColors.borderSubtle,
            error = oxherdColors.error
        )
    }

    CompositionLocalProvider(
        LocalOxherdColors provides oxherdColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
