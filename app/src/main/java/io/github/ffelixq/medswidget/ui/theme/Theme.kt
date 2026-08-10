package io.github.ffelixq.medswidget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ffelixq.medswidget.domain.ThemePreference

private val AppleBlue = Color(0xFF007AFF)
private val AppleBlueDark = Color(0xFF0A84FF)
private val AppleGreen = Color(0xFF34C759)
private val AppleGreenDark = Color(0xFF30D158)
private val AppleOrange = Color(0xFFFF9500)
private val AppleOrangeDark = Color(0xFFFF9F0A)
private val AppleRed = Color(0xFFFF3B30)
private val AppleRedDark = Color(0xFFFF453A)

private val LightColors =
    lightColorScheme(
        primary = AppleBlue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE7F2FF),
        onPrimaryContainer = Color(0xFF002C5B),
        secondary = AppleGreen,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE7F8EA),
        onSecondaryContainer = Color(0xFF083C16),
        tertiary = AppleOrange,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFF1DD),
        onTertiaryContainer = Color(0xFF4A2A00),
        error = AppleRed,
        onError = Color.White,
        background = Color(0xFFF2F2F7),
        onBackground = Color(0xFF111114),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF111114),
        surfaceVariant = Color(0xFFE9E9EE),
        onSurfaceVariant = Color(0xFF5B5B63),
        outline = Color(0xFFC7C7CC),
    )

private val DarkColors =
    darkColorScheme(
        primary = AppleBlueDark,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF0B3157),
        onPrimaryContainer = Color(0xFFD8E9FF),
        secondary = AppleGreenDark,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF123A1C),
        onSecondaryContainer = Color(0xFFD4F8DA),
        tertiary = AppleOrangeDark,
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF4A310B),
        onTertiaryContainer = Color(0xFFFFE4B7),
        error = AppleRedDark,
        onError = Color.Black,
        background = Color(0xFF000000),
        onBackground = Color(0xFFF5F5F7),
        surface = Color(0xFF1C1C1E),
        onSurface = Color(0xFFF5F5F7),
        surfaceVariant = Color(0xFF2C2C2E),
        onSurfaceVariant = Color(0xFFAEAEB2),
        outline = Color(0xFF48484A),
    )

private val AppleTypography =
    Typography(
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.4).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.25).sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.15).sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 22.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.1.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.1.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.2.sp,
            ),
    )

private val AppleShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp),
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
        typography = AppleTypography,
        shapes = AppleShapes,
        content = content,
    )
}
