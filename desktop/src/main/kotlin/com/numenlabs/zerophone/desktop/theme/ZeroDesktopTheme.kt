package com.numenlabs.zerophone.desktop.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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

/**
 * Desktop mirror of the `:core:ui` ZeroPhone brand theme (the Android module
 * cannot be shared with the JVM target). Keep the palette, type scale and
 * shapes in sync with core/ui/src/main/kotlin/.../theme/{Color,Type,Shapes}.kt.
 */

private val BrandPrimaryLight = Color(0xFF266A53)
private val MintDarkPrimary = Color(0xFF90D5B8)

private val LightColorScheme: ColorScheme = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFABF2D3),
    onPrimaryContainer = Color(0xFF002115),
    inversePrimary = MintDarkPrimary,
    secondary = Color(0xFF4B635A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE9DB),
    onSecondaryContainer = Color(0xFF07201A),
    tertiary = Color(0xFF6D5D30),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6E1A5),
    onTertiaryContainer = Color(0xFF231B00),
    background = Color(0xFFF9F7F0),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF9F7F0),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDDE4DC),
    onSurfaceVariant = Color(0xFF404943),
    surfaceDim = Color(0xFFD8D8CF),
    surfaceBright = Color(0xFFF9F7F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F1EA),
    surfaceContainer = Color(0xFFEEEDE5),
    surfaceContainerHigh = Color(0xFFE8E7DF),
    surfaceContainerHighest = Color(0xFFE2E1DA),
    inverseSurface = Color(0xFF2E312D),
    inverseOnSurface = Color(0xFFEFF1E9),
    outline = Color(0xFF6F7973),
    outlineVariant = Color(0xFFBFC9C1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = MintDarkPrimary,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0A5140),
    onPrimaryContainer = Color(0xFFABF2D3),
    inversePrimary = BrandPrimaryLight,
    secondary = Color(0xFFB2CCC0),
    onSecondary = Color(0xFF1D352B),
    secondaryContainer = Color(0xFF344C41),
    onSecondaryContainer = Color(0xFFCDE9DB),
    tertiary = Color(0xFFD9C58C),
    onTertiary = Color(0xFF392F00),
    tertiaryContainer = Color(0xFF524600),
    onTertiaryContainer = Color(0xFFF6E1A5),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE2E3DB),
    surface = Color(0xFF111411),
    onSurface = Color(0xFFE2E3DB),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    surfaceDim = Color(0xFF111411),
    surfaceBright = Color(0xFF373A36),
    surfaceContainerLowest = Color(0xFF0B0F0C),
    surfaceContainerLow = Color(0xFF191C19),
    surfaceContainer = Color(0xFF1D201D),
    surfaceContainerHigh = Color(0xFF282B27),
    surfaceContainerHighest = Color(0xFF333632),
    inverseSurface = Color(0xFFE2E3DB),
    inverseOnSurface = Color(0xFF2E312D),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val ZeroTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 68.sp,
        lineHeight = 76.sp,
        letterSpacing = (-2).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 54.sp,
        lineHeight = 62.sp,
        letterSpacing = (-1.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.3.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
)

val ZeroShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ZeroDesktopTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = ZeroTypography,
        shapes = ZeroShapes,
        content = content
    )
}
