package com.owlmedia.racecontrol.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription

/** Compound colour + single-letter badge, ported from iOS `TyreCompound`. */
object TyreCompound {

    fun color(compound: String?): Color = when (compound?.uppercase()) {
        "SOFT" -> RcPalette.TyreSoft
        "MEDIUM" -> RcPalette.TyreMedium
        "HARD" -> RcPalette.TyreHard
        "INTERMEDIATE" -> RcPalette.TyreIntermediate
        "WET" -> RcPalette.TyreWet
        else -> RcPalette.TextTertiary
    }

    fun letter(compound: String?): String =
        compound?.uppercase()?.firstOrNull()?.toString() ?: "?"

    /** Spoken by TalkBack, which cannot make sense of a lone "S". */
    fun contentDescription(compound: String?): String = when (compound?.uppercase()) {
        "SOFT" -> "Soft tyres"
        "MEDIUM" -> "Medium tyres"
        "HARD" -> "Hard tyres"
        "INTERMEDIATE" -> "Intermediate tyres"
        "WET" -> "Wet tyres"
        else -> "Unknown tyre compound"
    }
}
