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

private val DarkColorScheme = darkColorScheme(
    primary = SnsRedAccent,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = SnsOrange,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    tertiary = SnsSilverText,
    background = SnsDarkBg,
    onBackground = SnsSilverText,
    surface = SnsCardBg,
    onSurface = SnsSilverText,
    outline = SnsBorder
)

private val LightColorScheme = lightColorScheme(
    primary = SnsRedAccent,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = SnsOrange,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    tertiary = SnsSilverText,
    background = SnsDarkBg,
    onBackground = SnsSilverText,
    surface = SnsCardBg,
    onSurface = SnsSilverText,
    outline = SnsBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = ThemeManager.isDark, // Reactive Theme switch
    dynamicColor: Boolean = false, // Use our professional custom colors
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
