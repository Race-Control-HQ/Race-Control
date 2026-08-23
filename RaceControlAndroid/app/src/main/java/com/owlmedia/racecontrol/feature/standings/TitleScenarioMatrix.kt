package com.owlmedia.racecontrol.feature.standings

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcPalette
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.data.remote.dto.TitleScenariosResponseDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TitleScenariosViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {
    private val _data = MutableStateFlow<TitleScenariosResponseDto?>(null)
    val data = _data.asStateFlow()
    fun load(year: Int, throughRound: Int?) {
        if (_data.value?.year == year && _data.value?.throughRound == throughRound) return
        viewModelScope.launch {
            repository.titleScenarios(year, throughRound = throughRound)
                .onSuccess { _data.value = it }
        }
    }
}

@Composable
fun TitleScenarioMatrix(
    year: Int,
    throughRound: Int?,
    viewModel: TitleScenariosViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    LaunchedEffect(year, throughRound) { viewModel.load(year, throughRound) }
    val value = data ?: return
    if (!value.available || value.drivers.size < 2) return
    val d1 = value.drivers[0]
    val d2 = value.drivers[1]
    val maxMargin = value.cells.maxOfOrNull { kotlin.math.abs(it.margin) }?.coerceAtLeast(1.0) ?: 1.0
    val tertiaryTextArgb = RcTheme.colors.textTertiary.toArgb()
    RcCard {
        Column(Modifier.padding(Dimens.SM)) {
            Text("TITLE PERMUTATIONS", color = RcTheme.colors.textSecondary)
            Text(
                "Championship after the next race",
                color = RcTheme.colors.textPrimary,
            )
            Text(
                "${d1.code} ${formatPoints(d1.points)} pts · ${d2.code} ${formatPoints(d2.points)} pts · " +
                    "${value.roundsRemaining} race${if (value.roundsRemaining == 1) "" else "s"} remaining",
                color = RcTheme.colors.textTertiary,
            )
            Text(
                "Rows are ${d1.code}'s finish; columns are ${d2.code}'s. " +
                    "Each tile is ${d1.code}'s projected margin: + ahead, − behind.",
                color = RcTheme.colors.textTertiary,
            )
            value.summary?.let {
                Text(
                    "$it The tile numbers show how the margin changes.",
                    color = RcTheme.colors.textPrimary,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            value.clinchText?.let { Text(it, color = RcTheme.colors.positive, modifier = Modifier.padding(vertical = 6.dp)) }
            Text(
                "Green = ${d1.code} ahead · Red = ${d2.code} ahead · Bright = title clinched · Grey = tied",
                color = RcTheme.colors.textTertiary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Canvas(Modifier.fillMaxWidth().height(350.dp)) {
                val count = value.positions.size.coerceAtLeast(1)
                val labelWidth = 40.dp.toPx()
                val labelHeight = 28.dp.toPx()
                val cell = minOf(
                    (size.width - labelWidth) / count,
                    (size.height - labelHeight) / count,
                )
                val byKey = value.cells.associateBy { it.d1Position to it.d2Position }
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = tertiaryTextArgb
                    textSize = 9.dp.toPx()
                    textAlign = Paint.Align.CENTER
                }
                val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 8.dp.toPx()
                    textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                fun baseline(center: Float, paint: Paint) =
                    center - (paint.ascent() + paint.descent()) / 2
                fun positionLabel(position: Int) = if (position == 0) "DNF" else "P$position"
                value.positions.forEachIndexed { row, d1 ->
                    val centerY = labelHeight + row * cell + cell / 2
                    drawContext.canvas.nativeCanvas.drawText(
                        positionLabel(d1),
                        labelWidth / 2,
                        baseline(centerY, labelPaint),
                        labelPaint,
                    )
                    value.positions.forEachIndexed { column, d2 ->
                        if (row == 0) {
                            val centerX = labelWidth + column * cell + cell / 2
                            drawContext.canvas.nativeCanvas.drawText(
                                positionLabel(d2),
                                centerX,
                                baseline(labelHeight / 2, labelPaint),
                                labelPaint,
                            )
                        }
                        val scenario = byKey[d1 to d2]
                        val outcome = scenario?.outcome
                        val strength = scenario?.let {
                            (0.2 + 0.42 * (kotlin.math.abs(it.margin) / maxMargin)).toFloat()
                        } ?: 0.55f
                        val color = when (outcome) {
                            "D1_CLINCHED" -> RcPalette.Positive
                            "D2_CLINCHED" -> RcPalette.Negative
                            "D1_LEADS" -> RcPalette.Positive.copy(alpha = strength)
                            "D2_LEADS" -> RcPalette.Negative.copy(alpha = strength)
                            else -> RcPalette.TextTertiary.copy(alpha = 0.55f)
                        }
                        val left = labelWidth + column * cell + 1
                        val top = labelHeight + row * cell + 1
                        drawRect(
                            color,
                            Offset(left, top),
                            Size(cell - 2, cell - 2),
                        )
                        scenario?.let {
                            drawContext.canvas.nativeCanvas.drawText(
                                formatMargin(it.margin),
                                left + (cell - 2) / 2,
                                baseline(top + (cell - 2) / 2, marginPaint),
                                marginPaint,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatPoints(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun formatMargin(value: Double): String = when {
    value == 0.0 -> "TIE"
    value > 0 -> "+${formatPoints(value)}"
    else -> formatPoints(value)
}
