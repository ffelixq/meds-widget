package io.github.ffelixq.medswidget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.ffelixq.medswidget.domain.ThemePreference

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF256C5A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFACEFD6),
        onPrimaryContainer = Color(0xFF002118),
        surface = Color(0xFFFFFBFE),
        surfaceVariant = Color(0xFFE1E7E3),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF90D5BD),
        onPrimary = Color(0xFF00382B),
        primaryContainer = Color(0xFF07513F),
        onPrimaryContainer = Color(0xFFACEFD6),
        surface = Color(0xFF121412),
        surfaceVariant = Color(0xFF414945),
    )

@Suppress("FunctionNaming")
@Composable
fun MedsWidgetTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark =
        when (preference) {
            ThemePreference.SYSTEM -> isSystemInDarkTheme()
            ThemePreference.LIGHT -> false
            ThemePreference.DARK -> true
        }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
