package com.solarpulse.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand palette — docs/PLATFORM.md §5. */
object Brand {
    val Blue50 = Color(0xFFEFF5FF)
    val Blue100 = Color(0xFFDBE8FE)
    val Blue200 = Color(0xFFBFD5FE)
    val Blue300 = Color(0xFF93B8FD)
    val Blue400 = Color(0xFF6094FA)
    val Blue500 = Color(0xFF3B74F6)
    val Blue600 = Color(0xFF2557EB) // primary
    val Blue700 = Color(0xFF1D44D8)

    val Green = Color(0xFF22C55E)
    val Amber = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)
    val Violet = Color(0xFF8B5CF6)
    val Sky = Color(0xFF0EA5E9)

    val Slate50 = Color(0xFFF8FAFC)
    val Slate100 = Color(0xFFF1F5F9)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate400 = Color(0xFF94A3B8)
    val Slate500 = Color(0xFF64748B)
    val Slate600 = Color(0xFF475569)
    val Slate700 = Color(0xFF334155)
    val Slate800 = Color(0xFF1E293B)
    val Slate900 = Color(0xFF0F172A)
    val Slate950 = Color(0xFF020617)

    /** Categorical chart colours, same order as the web. */
    val Chart = listOf(Blue500, Green, Amber, Violet, Sky, Red)
}

/** Semantic colours that Material's scheme has no slot for. */
@Immutable
data class SolarColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val violet: Color,
    val sky: Color,
    val cardBorder: Color,
    val subtle: Color,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val isDark: Boolean,
)

val LightSolarColors = SolarColors(
    success = Brand.Green,
    warning = Brand.Amber,
    danger = Brand.Red,
    violet = Brand.Violet,
    sky = Brand.Sky,
    cardBorder = Color(0x14000000),
    subtle = Brand.Slate500,
    backgroundTop = Color(0xFFE6EFFF),
    backgroundBottom = Color(0xFFFBFCFF),
    isDark = false,
)

val DarkSolarColors = SolarColors(
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    violet = Color(0xFFA78BFA),
    sky = Color(0xFF38BDF8),
    cardBorder = Color(0x1FFFFFFF),
    subtle = Brand.Slate400,
    backgroundTop = Brand.Slate900,
    backgroundBottom = Brand.Slate900,
    isDark = true,
)

val LocalSolarColors = staticCompositionLocalOf { LightSolarColors }
