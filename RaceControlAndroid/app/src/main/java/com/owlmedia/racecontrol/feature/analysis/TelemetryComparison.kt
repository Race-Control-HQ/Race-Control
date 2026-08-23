package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.tabular
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.SectionHeader
import com.owlmedia.racecontrol.data.remote.dto.TelemetryTraceDto
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Head-to-head summary shown when exactly two drivers are selected: the numbers
 * behind the shape of the two traces.
 */
@Composable
fun TelemetryComparison(a: TelemetryTraceDto, b: TelemetryTraceDto) {
    val colorA = teamColor(a.teamColor).legibleOnSurface()
    val colorB = teamColor(b.teamColor).legibleOnSurface()

    RcCard {
        SectionHeader("${a.code} vs ${b.code}")

        Row(Modifier.fillMaxWidth()) {
            Text(
                text = a.code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorA,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = b.code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorB,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(
            color = RcTheme.colors.stroke,
            modifier = Modifier.padding(vertical = Dimens.SM),
        )

        CompareLine(
            label = "Lap time",
            valueA = a.lapTime ?: "–",
            valueB = b.lapTime ?: "–",
        )
        CompareLine(
            label = "Top speed",
            valueA = a.speed.maxOrNull()?.roundToInt()?.let { "$it km/h" } ?: "–",
            valueB = b.speed.maxOrNull()?.roundToInt()?.let { "$it km/h" } ?: "–",
        )
        CompareLine(
            label = "Min speed",
            valueA = a.speed.minOrNull()?.roundToInt()?.let { "$it km/h" } ?: "–",
            valueB = b.speed.minOrNull()?.roundToInt()?.let { "$it km/h" } ?: "–",
        )
        CompareLine(
            label = "Full throttle",
            valueA = a.fullThrottlePercent(),
            valueB = b.fullThrottlePercent(),
        )
        CompareLine(
            label = "Braking",
            valueA = a.brakingPercent(),
            valueB = b.brakingPercent(),
        )
        CompareLine(
            label = "Compound",
            valueA = a.compound ?: "–",
            valueB = b.compound ?: "–",
        )
    }
}

@Composable
private fun CompareLine(label: String, valueA: String, valueB: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = valueA,
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = RcTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = RcTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = valueB,
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = RcTheme.colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Share of the lap spent at 99%+ throttle: a decent proxy for track layout. */
private fun TelemetryTraceDto.fullThrottlePercent(): String {
    if (throttle.isEmpty()) return "–"
    val full = throttle.count { it >= 99.0 }
    return String.format(Locale.US, "%.0f%%", full * 100.0 / throttle.size)
}

private fun TelemetryTraceDto.brakingPercent(): String {
    if (brake.isEmpty()) return "–"
    val braking = brake.count { it > 0 }
    return String.format(Locale.US, "%.0f%%", braking * 100.0 / brake.size)
}
