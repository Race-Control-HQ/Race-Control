package com.owlmedia.racecontrol.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 8dp baseline grid, matching the iOS `Theme.Space` / `Theme.Radius` scales.
 *
 * The one intentional difference: [MinTouch] is 48dp, not the 44pt the iOS
 * build uses. 44 is Apple's minimum; Android's is 48 and copying the smaller
 * value would fail Material's accessibility bar.
 */
object Dimens {
    val XS = 4.dp
    val SM = 8.dp
    val MD = 16.dp
    val LG = 24.dp
    val XL = 32.dp

    val MinTouch = 48.dp

    val RadiusSm = 8.dp
    val RadiusMd = 14.dp
    val RadiusLg = 20.dp
}

object RcShapes {
    val Small = RoundedCornerShape(Dimens.RadiusSm)
    val Medium = RoundedCornerShape(Dimens.RadiusMd)
    val Large = RoundedCornerShape(Dimens.RadiusLg)
}
