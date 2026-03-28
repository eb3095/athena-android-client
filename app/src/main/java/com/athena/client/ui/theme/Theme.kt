package com.athena.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AthenaPrimary,
    secondary = AthenaSecondary,
    background = AthenaBackground,
    surface = AthenaSurface,
    surfaceVariant = AthenaSurfaceVariant,
    onPrimary = AthenaOnPrimary,
    onBackground = AthenaOnBackground,
    onSurface = AthenaOnSurface,
    error = AthenaError
)

private val LightColorScheme = lightColorScheme(
    primary = AthenaPrimary,
    secondary = AthenaSecondary,
    background = AthenaOnBackground,
    surface = AthenaOnPrimary,
    onPrimary = AthenaOnPrimary,
    onBackground = AthenaBackground,
    onSurface = AthenaBackground,
    error = AthenaError
)

@Composable
fun AthenaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
