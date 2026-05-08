package com.vunbo.watchtogether.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = TextOnPrimary,
    primaryContainer = PrimaryMuted,
    onPrimaryContainer = PrimaryVariant,
    secondary = Secondary,
    onSecondary = Color(0xFF0D0D0D),
    secondaryContainer = SecondaryMuted,
    onSecondaryContainer = SecondaryVariant,
    tertiary = Tertiary,
    onTertiary = TextOnPrimary,
    tertiaryContainer = Color(0xFF4A2020),
    onTertiaryContainer = TertiaryVariant,
    error = ErrorRed,
    onError = TextOnPrimary,
    errorContainer = Color(0xFF4A2020),
    onErrorContainer = ErrorRed,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3A3A5C),
    outlineVariant = Color(0xFF2A2A4A),
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = Primary,
    scrim = Color(0xAA000000)
)

@Composable
fun WatchTogetherTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WatchTogetherTypography,
        content = content
    )
}

