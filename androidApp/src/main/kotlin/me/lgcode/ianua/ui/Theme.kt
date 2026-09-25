package me.lgcode.ianua.ui

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

// The same palette as extension/static/ianua.css.
private val Light = lightColorScheme(
    primary = Color(0xFF8A4B2A),
    onPrimary = Color.White,
    background = Color(0xFFF6F3EE),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFF6F3EE),
    onSurface = Color(0xFF1F1B16),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD08A5F),
    onPrimary = Color(0xFF1B1814),
    background = Color(0xFF1B1814),
    onBackground = Color(0xFFEFE9E1),
    surface = Color(0xFF1B1814),
    onSurface = Color(0xFFEFE9E1),
)

@Composable
fun IanuaTheme(dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
