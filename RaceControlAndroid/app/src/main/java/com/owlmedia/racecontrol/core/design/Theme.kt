package com.owlmedia.racecontrol.core.design

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext

/**
 * Semantic colours Material 3 has no slot for. Tyre compounds and F1 team
 * liveries are data, not decoration, so they live alongside the M3 scheme
 * rather than being forced into `tertiary`/`error`.
 */
data class RcColors(
    val positive: Color = RcPalette.Positive,
    val negative: Color = RcPalette.Negative,
    val warning: Color = RcPalette.Warning,
    val info: Color = RcPalette.Info,
    val textPrimary: Color = RcPalette.TextPrimary,
    val textSecondary: Color = RcPalette.TextSecondary,
    val textTertiary: Color = RcPalette.TextTertiary,
    val racingRed: Color = RcPalette.RacingRed,
    val racingRedDim: Color = RcPalette.RacingRedDim,
    val racingRedText: Color = RcPalette.RacingRedText,
    val gold: Color = RcPalette.Gold,
    val goldDeep: Color = RcPalette.GoldDeep,
    val stroke: Color = RcPalette.Stroke,
    val surfaceElevated: Color = RcPalette.SurfaceElevated,
)

val LocalRcColors: ProvidableCompositionLocal<RcColors> = staticCompositionLocalOf { RcColors() }

/**
 * True when the user has switched animations off system-wide (Developer
 * options, or Accessibility > Remove animations). The Compose equivalent of
 * SwiftUI's `\.accessibilityReduceMotion`.
 */
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

private val RcDarkScheme = darkColorScheme(
    primary = RcPalette.RacingRed,
    onPrimary = Color.White,
    primaryContainer = RcPalette.RacingRedDim,
    onPrimaryContainer = Color.White,
    secondary = RcPalette.Info,
    onSecondary = Color.Black,
    tertiary = RcPalette.Warning,
    onTertiary = Color.Black,
    background = RcPalette.Background,
    onBackground = RcPalette.TextPrimary,
    surface = RcPalette.Surface,
    onSurface = RcPalette.TextPrimary,
    surfaceVariant = RcPalette.SurfaceElevated,
    onSurfaceVariant = RcPalette.TextSecondary,
    surfaceContainer = RcPalette.Surface,
    surfaceContainerHigh = RcPalette.SurfaceElevated,
    surfaceContainerHighest = RcPalette.SurfaceElevated,
    surfaceContainerLow = RcPalette.Background,
    surfaceContainerLowest = RcPalette.Background,
    outline = Color.White.copy(alpha = 0.16f),
    outlineVariant = RcPalette.Stroke,
    error = RcPalette.Negative,
    onError = Color.Black,
    scrim = Color.Black.copy(alpha = 0.6f),
)

/**
 * App theme.
 *
 * Deliberately **dark-only and not dynamically coloured**. Material You would
 * derive the palette from the user's wallpaper, which would collide with the
 * official F1 team and tyre colours the app uses to convey meaning: a green
 * "soft" tyre is simply wrong. This is a documented deviation from the Material
 * default (see docs/FEATURES.md §0).
 *
 * The system dark-mode setting is intentionally ignored for the same reason the iOS
 * app sets `.preferredColorScheme(.dark)`.
 */
@Composable
fun RaceControlTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }

    CompositionLocalProvider(
        LocalRcColors provides RcColors(),
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = RcDarkScheme,
            typography = RcTypography,
            content = content,
        )
    }
}

/** Shorthand: `RcTheme.colors.positive`. */
object RcTheme {
    val colors: RcColors
        @Composable get() = LocalRcColors.current
    val reduceMotion: Boolean
        @Composable get() = LocalReduceMotion.current
}
