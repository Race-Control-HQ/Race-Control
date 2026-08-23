package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.TyreCompound
import com.owlmedia.racecontrol.core.design.tabular
import com.owlmedia.racecontrol.core.ui.BarSegment
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.StackedBar
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.StrategyDriverDto
import com.owlmedia.racecontrol.data.remote.dto.StrategyResponseDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class StrategyViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<StrategyResponseDto>>(UiState.Idle)
    val state: StateFlow<UiState<StrategyResponseDto>> = _state.asStateFlow()

    fun load(year: Int, round: Int) {
        if (_state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.strategy(year, round)
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}

@Composable
fun StrategyScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    onOpenDriver: (String) -> Unit = {},
    viewModel: StrategyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(title = stringResource(R.string.analysis_strategy), onBack = onBack) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, round) },
            modifier = modifier,
        ) { data ->
            if (data.drivers.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Timeline,
                    title = stringResource(R.string.no_strategy_title),
                    message = stringResource(R.string.no_strategy_message),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(Dimens.MD),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SM),
                ) {
                    item(key = "legend") { CompoundLegend() }
                    items(
                        items = data.drivers,
                        key = { it.code },
                        contentType = { "strategy" },
                    ) { driver ->
                        StrategyRow(driver = driver, totalLaps = data.totalLaps, onOpenDriver = onOpenDriver)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompoundLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.SM),
        horizontalArrangement = Arrangement.spacedBy(Dimens.MD),
    ) {
        listOf("SOFT", "MEDIUM", "HARD", "INTERMEDIATE", "WET").forEach { compound ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TyreCompound.color(compound))
                )
                Text(
                    text = compound.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = RcTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun StrategyRow(
    driver: StrategyDriverDto,
    totalLaps: Int,
    onOpenDriver: (String) -> Unit,
) {
    val segments = driver.stints.map { stint ->
        BarSegment(
            value = stint.laps.toFloat().coerceAtLeast(1f),
            color = TyreCompound.color(stint.compound),
            label = stint.compound,
        )
    }

    val description = buildString {
        append("${driver.code}, ${driver.pitStops} pit stops. ")
        driver.stints.forEach { stint ->
            append(
                "${TyreCompound.contentDescription(stint.compound)} " +
                    "laps ${stint.startLap} to ${stint.endLap}. "
            )
        }
        if (driver.retired) {
            append("Retired${driver.status?.let { ": $it" } ?: ""}. ")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.SM)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = driver.code,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (driver.driverId != null) RcTheme.colors.info else RcTheme.colors.textPrimary,
                modifier = Modifier
                    .width(48.dp)
                    .clickable(enabled = driver.driverId != null) {
                        onOpenDriver(driver.driverId ?: return@clickable)
                    },
            )
            if (driver.retired) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RcTheme.colors.racingRed.copy(alpha = 0.2f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                        .semantics {
                            contentDescription = driver.status ?: "Retired"
                        },
                ) {
                    Text(
                        text = "DNF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RcTheme.colors.racingRed,
                    )
                }
            }
            Text(
                text = driver.teamName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = RcTheme.colors.textSecondary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Dimens.SM),
            )
            Text(
                text = if (driver.pitStops == 1) {
                    stringResource(R.string.pit_stop_one)
                } else {
                    stringResource(R.string.pit_stops, driver.pitStops)
                },
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = RcTheme.colors.textTertiary,
            )
        }
        Spacer(Modifier.height(6.dp))
        StackedBar(segments = segments, height = 20.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = driver.stints.joinToString("  ") {
                "${TyreCompound.letter(it.compound)}${it.laps}"
            },
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = RcTheme.colors.textTertiary,
        )
    }
}
