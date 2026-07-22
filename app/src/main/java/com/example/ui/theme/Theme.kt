package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = MinimalPrimary,
    onPrimary = MinimalOnPrimary,
    primaryContainer = MinimalPrimaryContainer,
    onPrimaryContainer = MinimalOnPrimaryContainer,
    secondary = MinimalSecondary,
    onSecondary = MinimalOnSecondary,
    secondaryContainer = MinimalSecondaryContainer,
    onSecondaryContainer = MinimalOnSecondaryContainer,
    tertiary = MinimalTertiary,
    background = MinimalBackground,
    surface = MinimalSurface,
    onBackground = MinimalOnBackground,
    onSurface = MinimalOnSurface,
    error = ErrorRed
)

private val DarkColorScheme = darkColorScheme(
    primary = MinimalPrimaryDark,
    onPrimary = MinimalOnPrimaryDark,
    primaryContainer = MinimalPrimaryContainerDark,
    onPrimaryContainer = MinimalOnPrimaryContainerDark,
    secondary = MinimalSecondaryDark,
    onSecondary = MinimalOnSecondaryDark,
    secondaryContainer = MinimalSecondaryContainerDark,
    onSecondaryContainer = MinimalOnSecondaryContainerDark,
    tertiary = MinimalTertiaryDark,
    background = MinimalBackgroundDark,
    surface = MinimalSurfaceDark,
    onBackground = MinimalOnBackgroundDark,
    onSurface = MinimalOnSurfaceDark,
    error = ErrorRed
)

@Composable
fun SinzoreInvestmentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ but we can default to false to preserve our custom brand identity colors!
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
