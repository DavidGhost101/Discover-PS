package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = DiscoveryGreenDark,
    onPrimaryContainer = Color(0xFF86EFAC),
    secondary = DiscoveryGold,
    onSecondary = Color(0xFF332000),
    secondaryContainer = Color(0xFF573E00),
    onSecondaryContainer = DiscoveryGoldLight,
    tertiary = Color(0xFF60A5FA),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569)
)

private val LightColorScheme = lightColorScheme(
    primary = DiscoveryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = DiscoveryGoldDark,
    onSecondary = Color.White,
    secondaryContainer = DiscoveryGoldLight,
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = InfoBlue,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onBackground = SchoolNavy,
    onSurface = SchoolNavy,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = SchoolSlate,
    outline = SchoolBorder
)

@Composable
fun DiscoveryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our brand colors by default
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
