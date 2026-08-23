package com.owlmedia.racecontrol.feature.circuits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.tabular
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.PositionBadge
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.SectionHeader
import com.owlmedia.racecontrol.core.ui.StatCell
import com.owlmedia.racecontrol.core.ui.TeamLogo
import com.owlmedia.racecontrol.core.ui.TyreBadge
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.CircuitMapDto
import com.owlmedia.racecontrol.data.remote.dto.ResultEntryDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CircuitDetailViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<CircuitMapDto>>(UiState.Idle)
    val state: StateFlow<UiState<CircuitMapDto>> = _state.asStateFlow()

    private val _podium = MutableStateFlow<List<ResultEntryDto>>(emptyList())
    val podium: StateFlow<List<ResultEntryDto>> = _podium.asStateFlow()

    private val _totalLaps = MutableStateFlow<Int?>(null)
    val totalLaps: StateFlow<Int?> = _totalLaps.asStateFlow()

    fun load(year: Int, round: Int) {
        if (_state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading

            // The map and the podium are independent calls; running them in
            // parallel halves the wait on a cold backend.
            val mapDeferred = async { repository.circuitMap(year, round) }
            val resultsDeferred = async { repository.results(year, round, "R") }

            mapDeferred.await()
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }

            resultsDeferred.await().onSuccess { results ->
                _podium.value = results.results.take(3)
                _totalLaps.value = results.totalLaps?.intValue
            }
        }
    }
}

@Composable
fun CircuitDetailScreen(
    year: Int,
    round: Int,
    circuitName: String,
    onBack: () -> Unit,
    onOpenReplay: (String) -> Unit,
    onOpenResults: (String) -> Unit,
    viewModel: CircuitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val podium by viewModel.podium.collectAsStateWithLifecycle()
    val totalLaps by viewModel.totalLaps.collectAsStateWithLifecycle()

    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(title = circuitName, onBack = onBack) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, round) },
            modifier = modifier,
        ) { map ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.MD),
            ) {
                RcCard {
                    TrackMap(
                        map = map,
                        contentDescriptionText = stringResource(
                            R.string.track_map_description,
                            map.eventName ?: circuitName,
                        ),
                    )
                }

                RcCard {
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(
                            value = map.lengthMeters
                                ?.let { String.format(java.util.Locale.US, "%.3f km", it / 1000.0) }
                                ?: "–",
                            label = stringResource(R.string.circuit_length),
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            value = map.corners.size.takeIf { it > 0 }?.toString() ?: "–",
                            label = stringResource(R.string.circuit_corners),
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            value = totalLaps?.toString() ?: "–",
                            label = stringResource(R.string.circuit_laps),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                map.fastestLap?.takeIf { it.time != null }?.let { lap ->
                    val accent = teamColor(lap.teamColor).legibleOnSurface()
                    RcCard {
                        SectionHeader(stringResource(R.string.fastest_lap))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = lap.driverName ?: lap.driver.orEmpty(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RcTheme.colors.textPrimary,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TeamLogo(url = lap.teamLogoUrl, size = 14.dp)
                                    Text(
                                        text = lap.team.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accent,
                                    )
                                }
                            }
                            lap.compound?.let { TyreBadge(compound = it, size = 24.dp) }
                            Text(
                                text = lap.time.orEmpty(),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                fontWeight = FontWeight.Bold,
                                color = RcTheme.colors.racingRedText,
                            )
                        }
                    }
                }

                if (podium.isNotEmpty()) {
                    RcCard {
                        SectionHeader(stringResource(R.string.podium))
                        podium.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
                            ) {
                                PositionBadge(
                                    text = entry.position?.roundToInt()?.toString() ?: "–",
                                    highlight = true,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = entry.fullName ?: entry.abbreviation.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RcTheme.colors.textPrimary,
                                    )
                                    Text(
                                        text = entry.teamName.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = RcTheme.colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }

                val title = map.eventName ?: circuitName
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SM)) {
                    Button(
                        onClick = { onOpenReplay(title) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayCircleFilled, contentDescription = null)
                        Text(
                            text = stringResource(R.string.watch_replay),
                            modifier = Modifier.padding(start = Dimens.SM),
                        )
                    }
                    OutlinedButton(
                        onClick = { onOpenResults(title) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Filled.FormatListNumbered,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(R.string.view_results),
                            modifier = Modifier.padding(start = Dimens.SM),
                        )
                    }
                }
            }
        }
    }
}

/** Standalone track map, reached from the race analysis grid. */
@Composable
fun TrackMapScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: CircuitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(
        title = stringResource(R.string.analysis_track_map),
        onBack = onBack,
    ) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, round) },
            modifier = modifier,
        ) { map ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.MD),
            ) {
                Text(
                    text = map.eventName ?: title,
                    style = MaterialTheme.typography.titleMedium,
                    color = RcTheme.colors.textSecondary,
                )
                RcCard {
                    TrackMap(
                        map = map,
                        height = 380.dp,
                        contentDescriptionText = stringResource(
                            R.string.track_map_description,
                            map.eventName ?: title,
                        ),
                    )
                }
                Text(
                    text = "Coloured by speed: red is slowest, green fastest. " +
                        "Blue marks the DRS zones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textTertiary,
                )
            }
        }
    }
}
