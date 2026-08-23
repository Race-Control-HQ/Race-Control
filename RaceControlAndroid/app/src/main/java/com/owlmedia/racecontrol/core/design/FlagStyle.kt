package com.owlmedia.racecontrol.core.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.owlmedia.racecontrol.data.remote.dto.FlagPeriodType

/** Colour + icon per flag/safety-car period type, ported from iOS `FlagStyle`. */
object FlagStyle {

    fun color(type: FlagPeriodType): Color = when (type) {
        FlagPeriodType.YELLOW -> RcPalette.FlagYellow
        FlagPeriodType.DOUBLE_YELLOW -> RcPalette.FlagDoubleYellow
        FlagPeriodType.RED -> RcPalette.FlagRed
        FlagPeriodType.SAFETY_CAR -> RcPalette.FlagSafetyCar
        FlagPeriodType.VIRTUAL_SAFETY_CAR -> RcPalette.FlagVirtualSafetyCar
        FlagPeriodType.UNKNOWN -> RcPalette.TextTertiary
    }

    /** No checkered-flag-for-yellow confusion: safety cars get a car icon, every
     *  flag period (yellow/double-yellow/red) gets an actual flag icon. */
    fun icon(type: FlagPeriodType): ImageVector = when (type) {
        FlagPeriodType.SAFETY_CAR, FlagPeriodType.VIRTUAL_SAFETY_CAR -> Icons.Filled.DirectionsCar
        else -> Icons.Filled.Flag
    }
}
