package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.ChartBand
import com.owlmedia.racecontrol.core.ui.ChartPoint
import com.owlmedia.racecontrol.core.ui.ChartSeries
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.RcLineChart
import com.owlmedia.racecontrol.core.ui.StatCell
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.WeatherResponseDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<WeatherResponseDto>>(UiState.Idle)
    val state: StateFlow<UiState<WeatherResponseDto>> = _state.asStateFlow()

    fun load(year: Int, round: Int) {
        if (_state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.weather(year, round, "R")
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}

@Composable
fun WeatherScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: WeatherViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(title = stringResource(R.string.analysis_weather), onBack = onBack) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, round) },
            modifier = modifier,
        ) { weather ->
            if (!weather.available) {
                EmptyState(
                    icon = Icons.Filled.Cloud,
                    title = stringResource(R.string.no_weather_title),
                    message = stringResource(R.string.no_weather_message),
                )
                return@LoadableContent
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.MD),
            ) {
                Text(
                    text = weather.eventName ?: title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RcTheme.colors.textSecondary,
                )

                RcCard {
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(
                            value = weather.airTemp.celsius(),
                            label = stringResource(R.string.weather_air),
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            value = weather.trackTemp.celsius(),
                            label = stringResource(R.string.weather_track),
                            modifier = Modifier.weight(1f),
                            accent = RcTheme.colors.warning,
                        )
                    }
                    val airMax = weather.airTempMax?.celsius()
                    val trackMax = weather.trackTempMax?.celsius()
                    if (airMax != null || trackMax != null) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = airMax?.let { stringResource(R.string.weather_max, it) }.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = RcTheme.colors.textTertiary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = trackMax?.let { stringResource(R.string.weather_max, it) }.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = RcTheme.colors.textTertiary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (weather.timeline.size > 1) {
                    RcCard {
                        RcLineChart(
                            series = listOf(
                                ChartSeries(
                                    "Air",
                                    RcTheme.colors.info,
                                    weather.timeline.mapNotNull { sample ->
                                        sample.airTemp?.let { ChartPoint(sample.timeSeconds, it) }
                                    },
                                ),
                                ChartSeries(
                                    "Track",
                                    RcTheme.colors.warning,
                                    weather.timeline.mapNotNull { sample ->
                                        sample.trackTemp?.let { ChartPoint(sample.timeSeconds, it) }
                                    },
                                ),
                            ),
                            // Band to the next sample's timestamp rather than a fixed
                            // +60s, so it tiles without gaps or overlaps regardless of
                            // the session's actual weather-sampling interval.
                            bands = weather.timeline.mapIndexedNotNull { index, sample ->
                                if (!sample.rainfall) return@mapIndexedNotNull null
                                val end = weather.timeline.getOrNull(index + 1)?.timeSeconds ?: (sample.timeSeconds + 60)
                                ChartBand(sample.timeSeconds, end, RcTheme.colors.info.copy(alpha = 0.12f))
                            },
                            contentDescription = "Air and track temperature chart with rainfall bands",
                        )
                    }
                }

                RcCard {
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(
                            value = weather.humidity?.let {
                                String.format(Locale.US, "%.0f%%", it)
                            } ?: "–",
                            label = stringResource(R.string.weather_humidity),
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            value = weather.windSpeed?.let {
                                String.format(Locale.US, "%.1f m/s", it)
                            } ?: "–",
                            label = stringResource(R.string.weather_wind),
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            value = weather.pressure?.let {
                                String.format(Locale.US, "%.0f", it)
                            } ?: "–",
                            label = stringResource(R.string.weather_pressure),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                RcCard {
                    StatCell(
                        value = stringResource(
                            if (weather.rainfall == true) R.string.weather_wet
                            else R.string.weather_dry
                        ),
                        label = stringResource(R.string.weather_rain),
                        accent = if (weather.rainfall == true) {
                            RcTheme.colors.info
                        } else {
                            RcTheme.colors.textPrimary
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun Double?.celsius(): String =
    this?.let { String.format(Locale.US, "%.1f°C", it) } ?: "–"
