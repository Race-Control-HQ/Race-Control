package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.FlagStyle
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.core.util.FlexibleDate
import com.owlmedia.racecontrol.core.util.formatTimeWithZone
import com.owlmedia.racecontrol.data.remote.dto.FlagPeriodType
import com.owlmedia.racecontrol.data.remote.dto.RaceControlCategory
import com.owlmedia.racecontrol.data.remote.dto.RaceControlMessageDto
import com.owlmedia.racecontrol.data.remote.dto.RaceControlResponseDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coarser buckets than [RaceControlCategory] for the filter row: car events
 * and stewards' messages both read as "an incident happened", so splitting
 * them into separate chips would just add width without adding clarity.
 */
enum class RaceControlFilter {
    ALL, FLAGS, SAFETY_CAR, DRS, INCIDENTS;

    fun matches(category: RaceControlCategory): Boolean = when (this) {
        ALL -> true
        FLAGS -> category == RaceControlCategory.FLAG
        SAFETY_CAR -> category == RaceControlCategory.SAFETY_CAR
        DRS -> category == RaceControlCategory.DRS
        INCIDENTS -> category == RaceControlCategory.CAR_EVENT || category == RaceControlCategory.OTHER
    }
}

@HiltViewModel
class RaceControlViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<RaceControlResponseDto>>(UiState.Idle)
    val state: StateFlow<UiState<RaceControlResponseDto>> = _state.asStateFlow()

    private val _filter = MutableStateFlow(RaceControlFilter.ALL)
    val filter: StateFlow<RaceControlFilter> = _filter.asStateFlow()

    fun load(year: Int, round: Int) {
        if (_state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.raceControl(year, round)
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    fun setFilter(value: RaceControlFilter) {
        _filter.value = value
    }
}

/**
 * The complete, unfiltered race-control message log for a race: DRS, car
 * events and "Other" stewards' messages (investigations, penalties,
 * reprimands) alongside the flag/safety-car messages [FlagsScreen] already
 * shows. Complementary to Flags rather than a superset view of it: Flags
 * collapses periods for a quick read, this is the raw chronological feed.
 */
@Composable
fun RaceControlScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: RaceControlViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(
        title = stringResource(R.string.analysis_race_control),
        onBack = onBack,
    ) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, round) },
            modifier = modifier,
        ) { data ->
            if (data.messages.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Article,
                    title = stringResource(R.string.no_race_control_title),
                    message = stringResource(R.string.no_race_control_message),
                )
                return@LoadableContent
            }

            val visible = remember(data, filter) {
                data.messages.filter { filter.matches(it.categoryType) }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.MD, vertical = Dimens.SM),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
                ) {
                    RaceControlFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { viewModel.setFilter(option) },
                            label = { Text(stringResource(option.labelRes())) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RcTheme.colors.racingRed.copy(alpha = 0.3f),
                                selectedLabelColor = RcTheme.colors.textPrimary,
                            ),
                        )
                    }
                }

                if (visible.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Article,
                        title = stringResource(R.string.no_race_control_title),
                        message = stringResource(R.string.no_race_control_filter_message),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = Dimens.MD,
                            end = Dimens.MD,
                            bottom = Dimens.MD,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SM),
                    ) {
                        items(
                            items = visible,
                            key = { "${it.time}-${it.lap}-${it.message}" },
                            contentType = { "message" },
                        ) { message ->
                            RaceControlMessageRow(message)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RaceControlFilter.labelRes(): Int = when (this) {
    RaceControlFilter.ALL -> R.string.race_control_filter_all
    RaceControlFilter.FLAGS -> R.string.race_control_filter_flags
    RaceControlFilter.SAFETY_CAR -> R.string.race_control_filter_safety_car
    RaceControlFilter.DRS -> R.string.race_control_filter_drs
    RaceControlFilter.INCIDENTS -> R.string.race_control_filter_incidents
}

private fun categoryIcon(category: RaceControlCategory): ImageVector = when (category) {
    RaceControlCategory.FLAG -> Icons.Filled.Flag
    RaceControlCategory.SAFETY_CAR -> Icons.Filled.DirectionsCar
    RaceControlCategory.DRS -> Icons.Filled.Bolt
    RaceControlCategory.CAR_EVENT -> Icons.Filled.ReportProblem
    RaceControlCategory.OTHER -> Icons.Filled.Article
}

/**
 * Colour accent for a row. Only Flag/SafetyCar rows get one of the five
 * [FlagStyle] colours, matching the palette [FlagsScreen] already uses for
 * the same underlying flag text, so a user who has seen both screens reads
 * "yellow" the same way in either. DRS/car-event/other rows use a neutral
 * tint instead: they are not part of the flag vocabulary.
 */
private fun accentColor(message: RaceControlMessageDto): Color? = when (message.categoryType) {
    RaceControlCategory.FLAG -> {
        val normalized = message.flag?.trim()?.uppercase()?.replace(" ", "_")
        FlagStyle.color(FlagPeriodType.from(normalized))
    }
    RaceControlCategory.SAFETY_CAR -> {
        val isVirtual = message.flag.orEmpty().contains("VIRTUAL", ignoreCase = true) ||
            message.message.orEmpty().contains("VIRTUAL SAFETY CAR", ignoreCase = true)
        FlagStyle.color(if (isVirtual) FlagPeriodType.VIRTUAL_SAFETY_CAR else FlagPeriodType.SAFETY_CAR)
    }
    else -> null
}

@Composable
private fun RaceControlMessageRow(message: RaceControlMessageDto) {
    val color = accentColor(message) ?: RcTheme.colors.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.MD),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.MD),
    ) {
        Icon(
            imageVector = categoryIcon(message.categoryType),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val lap = message.lap
                if (lap != null) {
                    Text(
                        text = stringResource(R.string.flag_lap_single, lap),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = RcTheme.colors.textPrimary,
                    )
                }
                val code = message.driverCode
                if (!code.isNullOrBlank()) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RcTheme.colors.textSecondary,
                        modifier = Modifier
                            .clip(RcShapes.Small)
                            .background(RcTheme.colors.surfaceElevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                val timeText = remember(message.time) { FlexibleDate.parse(message.time)?.formatTimeWithZone() }
                if (timeText != null) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = RcTheme.colors.textTertiary,
                    )
                }
            }
            Text(
                text = message.message ?: message.flag ?: "–",
                style = MaterialTheme.typography.bodyMedium,
                color = RcTheme.colors.textPrimary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
