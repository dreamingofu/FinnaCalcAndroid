//
// Theme.kt
//
// The renovated FinnaCalc design system, ported from FinnaCalcIOS
// Core/DesignSystem/Theme.swift (which itself traces to the design project's
// tokens/{colors,typography,radius}.css). Same brand — white/blue/slate, round
// corners — dark-first surface: black page, slate-900 cards, blue-500 brand,
// IBM Plex typography.
//
// Deviation from iOS: SwiftUI resolves light/dark per-color via UIColor traits;
// Compose has no per-color dynamic resolution, so tokens live in an immutable
// FCColorScheme (light + dark instances) provided through a CompositionLocal by
// FinnaTheme. Call sites read `Theme.colors.primary` inside composition — the
// same token names as iOS.
//

package com.finnacalc.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.R

// MARK: Raw palette (design tokens/colors.css --fc-*)

private object P {
    val white = Color(0xFFFFFFFF)
    val black = Color(0xFF000000)
    val ink = Color(0xFF020817)
    val slate900 = Color(0xFF0F172A)
    val slate800 = Color(0xFF1E293B)
    val slate700 = Color(0xFF334155)
    val slate500 = Color(0xFF64748B)
    val slate400 = Color(0xFF94A3B8)
    val slate300 = Color(0xFFCBD5E1)
    val slate200 = Color(0xFFE2E8F0)
    val slate100 = Color(0xFFF1F5F9)
    val slate50 = Color(0xFFF8FAFC)
    val blue500 = Color(0xFF3B82F6)
    val blue600 = Color(0xFF2563EB)
    val blue700 = Color(0xFF1D4ED8)
    val blue800 = Color(0xFF1E40AF)
    val green600 = Color(0xFF16A34A)
    val green400 = Color(0xFF4ADE80)
    val red600 = Color(0xFFDC2626)
    val red500 = Color(0xFFEF4444)
    val amber500 = Color(0xFFF59E0B)
}

// MARK: Semantic color scheme — token names preserved from iOS Theme.

class FCColorScheme(
    val background: Color,          // surface-page
    val surfaceSunken: Color,
    val foreground: Color,          // text-strong
    val textBody: Color,            // text-body
    val card: Color,                // surface-card
    val cardForeground: Color,
    val primary: Color,             // brand
    /** The vivid blue of the FinnaBot mark — `primary` reads navy on large fills. */
    val brandBlue: Color,
    val primaryForeground: Color,   // brand-onfill
    val brandHover: Color,
    val brandPress: Color,
    val secondary: Color,           // surface-muted
    val mutedForeground: Color,     // text-muted
    val destructive: Color,         // negative action
    val destructiveForeground: Color,
    val border: Color,              // border-subtle
    val borderStrong: Color,
    val caution: Color,
    /** Positive result figures — green-600 (light) / green-400 (dark). */
    val positive: Color,
    /** Negative / cost figures — red-600 (light) / red-500 (dark). */
    val negative: Color,
    val isDark: Boolean,
) {
    val popover: Color get() = card
    val popoverForeground: Color get() = cardForeground
    val secondaryForeground: Color get() = foreground
    val muted: Color get() = secondary
    val accent: Color get() = secondary
    val accentForeground: Color get() = foreground
    val input: Color get() = border
    val ring: Color get() = primary
    val brandTint: Color get() = primary.copy(alpha = 0.14f)  // icon chips / result tints
    val cautionTint: Color get() = caution.copy(alpha = 0.12f)
}

val FCLightColors = FCColorScheme(
    background = P.white,
    surfaceSunken = P.slate50,
    foreground = P.ink,
    textBody = P.slate700,
    card = P.white,
    cardForeground = P.ink,
    primary = P.blue600,
    brandBlue = Color(0xFF005EFF),
    primaryForeground = P.slate50,
    brandHover = P.blue700,
    brandPress = P.blue800,
    secondary = P.slate100,
    mutedForeground = P.slate500,
    destructive = P.red600,
    destructiveForeground = P.white,
    border = P.slate200,
    borderStrong = P.slate300,
    caution = P.amber500,
    positive = P.green600,
    negative = P.red600,
    isDark = false,
)

// Dark page is pure black (the ink/sunken navies read blue on OLED).
val FCDarkColors = FCColorScheme(
    background = P.black,
    surfaceSunken = P.black,
    foreground = P.slate50,
    textBody = P.slate300,
    card = P.slate900,
    cardForeground = P.slate50,
    primary = P.blue500,
    brandBlue = Color(0xFF2E7DFF),
    primaryForeground = P.ink,
    brandHover = P.blue600,
    brandPress = P.blue700,
    secondary = P.slate800,
    mutedForeground = P.slate400,
    destructive = P.red500,
    destructiveForeground = P.white,
    border = P.slate800,
    borderStrong = P.slate700,
    caution = P.amber500,
    positive = P.green400,
    negative = P.red500,
    isDark = true,
)

val LocalFCColors = staticCompositionLocalOf { FCLightColors }

// MARK: Theme — token access + radii + type scale, mirroring iOS `enum Theme`.

object Theme {
    val colors: FCColorScheme
        @Composable @ReadOnlyComposable get() = LocalFCColors.current

    /**
     * Maximum width for a page's content column. Phone-width layouts are the
     * design source, so tablets cap here and center (see the shell's screen
     * wrapper). Mirrors iOS Theme.readableWidth (760pt).
     */
    val readableWidth: Dp = 760.dp

    // Corner radii — tokens/radius.css
    object Radius {
        val sm = 8.dp    // chips, small controls
        val md = 10.dp   // buttons, inputs
        val lg = 12.dp   // cards
        val xl = 16.dp   // large cards, sheets
        val xxl = 20.dp  // hero panels, modal sheets
    }

    // Type scale — tokens/typography.css. Sp via sans()/figure().
    object FontSize {
        const val xs = 12
        const val sm = 14
        const val base = 16
        const val lg = 18
        const val xl = 20
        const val xl2 = 24
        const val xl3 = 30
        const val xl4 = 36
        const val xl5 = 44
    }

    /** Display/body face — IBM Plex Sans (bundled in res/font). */
    val sansFamily = FontFamily(
        Font(R.font.ibmplexsans_regular, FontWeight.Normal),
        Font(R.font.ibmplexsans_medium, FontWeight.Medium),
        Font(R.font.ibmplexsans_semibold, FontWeight.SemiBold),
        Font(R.font.ibmplexsans_bold, FontWeight.Bold),
    )

    /** IBM Plex Mono — kept bundled for the rare true-mono use (matches iOS). */
    val monoFamily = FontFamily(
        Font(R.font.ibmplexmono_regular, FontWeight.Normal),
        Font(R.font.ibmplexmono_medium, FontWeight.Medium),
        Font(R.font.ibmplexmono_semibold, FontWeight.SemiBold),
    )

    /** Display/body text style — IBM Plex Sans. */
    fun sans(size: Int, weight: FontWeight = FontWeight.Normal): TextStyle =
        TextStyle(fontFamily = sansFamily, fontSize = size.sp, fontWeight = weight)

    /**
     * Numeric figure style. Maps to the global sans (IBM Plex Sans) so
     * currency / rate / percentage figures render in the same face as body
     * text — semibold default so figure call sites keep their weight
     * (mirrors iOS Theme.figure).
     */
    fun figure(size: Int, weight: FontWeight = FontWeight.SemiBold): TextStyle =
        TextStyle(fontFamily = sansFamily, fontSize = size.sp, fontWeight = weight)

    // Elevation — ink-tinted, low-spread. Compose shadow() takes a dp radius +
    // tint; the (color-alpha, blur, y) triples from iOS map onto the closest
    // Modifier.fcShadow rendering in Shadow.kt.
    enum class Elevation(val alpha: Float, val radius: Dp, val y: Dp) {
        Sm(0.05f, 1.5.dp, 1.dp),
        Md(0.10f, 5.dp, 2.dp),
        Lg(0.18f, 16.dp, 12.dp),
        Brand(0.35f, 12.dp, 8.dp),
    }
}

// MARK: FinnaTheme root

/**
 * Provides the FinnaCalc token scheme (and a minimally-mapped Material color
 * scheme so Material widgets — ripples, sheets, text selection — blend in).
 */
@Composable
fun FinnaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val fc = if (darkTheme) FCDarkColors else FCLightColors
    val material = if (darkTheme) {
        darkColorScheme(
            primary = fc.primary,
            onPrimary = fc.primaryForeground,
            background = fc.background,
            onBackground = fc.foreground,
            surface = fc.card,
            onSurface = fc.cardForeground,
            surfaceVariant = fc.secondary,
            onSurfaceVariant = fc.mutedForeground,
            error = fc.destructive,
            onError = fc.destructiveForeground,
            outline = fc.border,
        )
    } else {
        lightColorScheme(
            primary = fc.primary,
            onPrimary = fc.primaryForeground,
            background = fc.background,
            onBackground = fc.foreground,
            surface = fc.card,
            onSurface = fc.cardForeground,
            surfaceVariant = fc.secondary,
            onSurfaceVariant = fc.mutedForeground,
            error = fc.destructive,
            onError = fc.destructiveForeground,
            outline = fc.border,
        )
    }
    CompositionLocalProvider(LocalFCColors provides fc) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
