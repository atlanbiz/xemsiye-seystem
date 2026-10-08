package com.solarpulse.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Brand.Blue600,
    onPrimary = Color.White,
    primaryContainer = Brand.Blue100,
    onPrimaryContainer = Color(0xFF0B2A8F),
    inversePrimary = Brand.Blue300,
    secondary = Brand.Amber,
    onSecondary = Color(0xFF3B2300),
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Brand.Green,
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF14532D),
    background = Color(0xFFF6F9FF),
    onBackground = Brand.Slate900,
    surface = Color.White,
    onSurface = Brand.Slate900,
    surfaceVariant = Brand.Slate100,
    onSurfaceVariant = Brand.Slate500,
    surfaceTint = Brand.Blue600,
    inverseSurface = Brand.Slate800,
    inverseOnSurface = Brand.Slate100,
    error = Brand.Red,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    outline = Brand.Slate300,
    outlineVariant = Brand.Slate200,
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE6EBF3),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Brand.Slate50,
    surfaceContainer = Color(0xFFF2F5FB),
    surfaceContainerHigh = Color(0xFFEAEFF7),
    surfaceContainerHighest = Brand.Slate200,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Brand.Blue400,
    onPrimary = Color(0xFF071C5C),
    primaryContainer = Brand.Blue700,
    onPrimaryContainer = Brand.Blue100,
    inversePrimary = Brand.Blue600,
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF3B2300),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFDCFCE7),
    background = Brand.Slate900,
    onBackground = Brand.Slate100,
    surface = Brand.Slate800,
    onSurface = Brand.Slate100,
    surfaceVariant = Brand.Slate700,
    onSurfaceVariant = Brand.Slate400,
    surfaceTint = Brand.Blue400,
    inverseSurface = Brand.Slate100,
    inverseOnSurface = Brand.Slate800,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    outline = Brand.Slate600,
    outlineVariant = Brand.Slate700,
    scrim = Color.Black,
    surfaceBright = Brand.Slate700,
    surfaceDim = Brand.Slate950,
    surfaceContainerLowest = Color(0xFF0B1220),
    surfaceContainerLow = Color(0xFF162032),
    surfaceContainer = Brand.Slate800,
    surfaceContainerHigh = Color(0xFF263248),
    surfaceContainerHighest = Brand.Slate700,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Base = Typography()

/** System font (covers Arabic/Uyghur shaping); tighter, bolder headings for a data-dense UI. */
private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium,
    labelSmall = Base.labelSmall,
)

/** Big KPI numbers. */
val KpiTextStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)

@Composable
fun SolarPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val solar = if (darkTheme) DarkSolarColors else LightSolarColors
    CompositionLocalProvider(LocalSolarColors provides solar) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes) {
            ProvideVicoTheme(rememberM3VicoTheme(lineColor = scheme.outlineVariant, textColor = scheme.onSurfaceVariant)) {
                content()
            }
        }
    }
}

object SolarTheme {
    val colors: SolarColors
        @Composable @ReadOnlyComposable get() = LocalSolarColors.current
}
