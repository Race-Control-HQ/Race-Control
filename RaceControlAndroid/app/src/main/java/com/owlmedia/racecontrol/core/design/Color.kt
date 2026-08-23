package com.owlmedia.racecontrol.core.design

import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance

/**
 * Brand palette, ported verbatim from the iOS `Theme.Palette` in Theme.swift.
 *
 * Dark-first and OLED-tuned: near-black surfaces rather than pure #000, which
 * avoids the smearing OLED panels show when scrolling true black.
 */
object RcPalette {
    /** Signature Formula 1 red. Fills, tints and large text only; at 3.6:1 on
     *  surface it fails WCAG AA for small text. */
    val RacingRed = Color(0xFFE10600)
    val RacingRedDim = Color(0xFFB00500)
    /** Lighter red for *small* text (5.9:1 on surface, passes AA). */
    val RacingRedText = Color(0xFFFF5A50)

    val Background = Color(0xFF0A0A0C)
    val Surface = Color(0xFF16161A)
    val SurfaceElevated = Color(0xFF202027)
    val Stroke = Color.White.copy(alpha = 0.08f)

    // Contrast on surface (#16161A): 16.2:1 / 7.0:1 / 5.3:1, all pass WCAG AA.
    val TextPrimary = Color(0xFFF2F2F5)
    val TextSecondary = Color(0xFFA0A0AA)
    val TextTertiary = Color(0xFF8A8A94)

    val Positive = Color(0xFF30D158)
    val Negative = Color(0xFFFF453A)
    val Warning = Color(0xFFFF9F0A)
    val Info = Color(0xFF64D2FF)

    val Gold = Color(0xFFFFD700)
    val GoldDeep = Color(0xFFFFB300)

    // Official F1 tyre compound colours.
    val TyreSoft = Color(0xFFF0402C)
    val TyreMedium = Color(0xFFF5D000)
    val TyreHard = Color(0xFFEBEBEB)
    val TyreIntermediate = Color(0xFF40B14B)
    val TyreWet = Color(0xFF1E6FE0)

    // Track flag / safety-car period colours. Distinct hues so five bands
    // overlaid on a chart (or listed back to back) stay tellable apart even
    // for a colour-blind reader relying on the accompanying icon/label too.
    val FlagYellow = Color(0xFFFFD500)
    val FlagDoubleYellow = Color(0xFFFF9500)
    val FlagRed = Color(0xFFFF453A)
    val FlagSafetyCar = Color(0xFFFF6A00)
    val FlagVirtualSafetyCar = Color(0xFFAF52DE)
}

/**
 * Parses a backend-supplied hex colour (`#E10600`, `E10600` or `E10600FF`),
 * falling back to the brand red when absent or malformed.
 *
 * Equivalent to the iOS `Color.team(_:)` helper.
 */
fun teamColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return RcPalette.RacingRed
    val cleaned = hex.trim().removePrefix("#").uppercase()
    val value = cleaned.toLongOrNull(16) ?: return RcPalette.RacingRed
    return when (cleaned.length) {
        6 -> Color(0xFF000000L or value)
        8 -> {
            // RRGGBBAA on the wire; Compose wants AARRGGBB.
            val rgb = value ushr 8
            val alpha = value and 0xFF
            Color((alpha shl 24) or rgb)
        }
        else -> RcPalette.RacingRed
    }
}

/**
 * Team liveries are arbitrary hex from the backend and some are very dark
 * (Sauber's near-black, for instance), which disappears against our #16161A
 * surface. When a colour is too dark to read, lift it until it is legible.
 *
 * Only use this for colour-as-text or colour-as-thin-line; large fills can use
 * the raw livery colour safely.
 */
fun Color.legibleOnSurface(minLuminance: Float = 0.18f): Color {
    if (luminance() >= minLuminance) return this
    var result = this
    var steps = 0
    while (result.luminance() < minLuminance && steps < 12) {
        result = Color(
            red = (result.red + (1f - result.red) * 0.18f).coerceIn(0f, 1f),
            green = (result.green + (1f - result.green) * 0.18f).coerceIn(0f, 1f),
            blue = (result.blue + (1f - result.blue) * 0.18f).coerceIn(0f, 1f),
            alpha = result.alpha,
        )
        steps++
    }
    return result
}
