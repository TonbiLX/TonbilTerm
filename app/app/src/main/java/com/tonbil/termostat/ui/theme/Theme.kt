package com.tonbil.termostat.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TonbilDarkColors = darkColorScheme(
    background = TonbilBackground,
    surface = TonbilSurface,
    surfaceVariant = TonbilSurfaceVariant,
    primary = TonbilPrimary,
    onPrimary = TonbilOnPrimary,
    onBackground = TonbilOnSurface,
    onSurface = TonbilOnSurface,
    onSurfaceVariant = TonbilOnSurfaceVariant,
    error = TonbilError,
    outline = TonbilOutline,
    surfaceContainer = CardDark,
    surfaceContainerHigh = TonbilSurfaceVariant,
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF1C1B1F),
    inversePrimary = Color(0xFF8B5000),
)

@Composable
fun TonbilTermTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = TonbilBackground.toArgb()
            window.navigationBarColor = TonbilBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = TonbilDarkColors,
        typography = TonbilTypography,
        content = content,
    )
}
