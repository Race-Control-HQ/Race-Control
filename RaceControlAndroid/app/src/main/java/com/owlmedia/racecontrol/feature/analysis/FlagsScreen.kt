package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiFlags
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
import com.owlmedia.racecontrol.core.ui.SectionHeader
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.FlagEventDto
import com.owlmedia.racecontrol.data.remote.dto.FlagPeriodDto
import com.owlmedia.racecontrol.data.remote.dto.FlagPeriodType
import com.owlmedia.racecontrol.data.remote.dto.FlagsResponseDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import com.owlmedia.racecontrol.core.util.FlexibleDate
import com.owlmedia.racecontrol.core.util.formatTimeWithZone
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FlagsViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<FlagsResponseDto>>(UiState.Idle)
    val state: StateFlow<UiState<FlagsResponseDto>> = _state.asStateFlow()

    fun load(year: Int, round: Int) {
        if (_state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.flags(year, round)
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}

/**
 * Chronological list of collapsed flag/safety-car periods (primary), with the
 * raw race-control timeline available underneath for detail; mirrors the iOS
 * `FlagsView` split between [FlagsResponseDto.periods] and `.events`.
 */
@Composable
fun FlagsScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: FlagsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(
        title = stringResource(R.string.analysis_flags),
        onBack = onBack,
    ) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, round) },
            modifier = modifier,
        ) { data ->
            if (data.periods.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.EmojiFlags,
                    title = stringResource(R.string.no_flags_title),
                    message = stringResource(R.string.no_flags_message),
                )
                return@LoadableContent
            }

            var timelineExpanded by remember { mutableStateOf(false) }

            LazyColumn(
                contentPadding = PaddingValues(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.SM),
            ) {
                item(key = "summary") {
                    Text(
                        text = pluralStringResource(
                            R.plurals.flag_periods_count,
                            data.periods.size,
                            data.periods.size,
                        ) + " " + stringResource(R.string.flags_across_laps, data.totalLaps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RcTheme.colors.textSecondary,
                    )
                }

                items(
                    items = data.periods,
                    key = { "${it.type}-${it.startLap}-${it.endLap}" },
                    contentType = { "period" },
                ) { period ->
                    FlagPeriodRow(period)
                }

                if (data.events.isNotEmpty()) {
                    item(key = "timeline-header") {
                        TimelineHeader(
                            count = data.events.size,
                            expanded = timelineExpanded,
                            onToggle = { timelineExpanded = !timelineExpanded },
                        )
                    }
                    if (timelineExpanded) {
                        items(
                            items = data.events,
                            key = { "event-${it.time}-${it.lap}-${it.message}" },
                            contentType = { "event" },
                        ) { event ->
                            FlagEventRow(event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlagPeriodType.label(): String = stringResource(
    when (this) {
        FlagPeriodType.YELLOW -> R.string.flag_type_yellow
        FlagPeriodType.DOUBLE_YELLOW -> R.string.flag_type_double_yellow
        FlagPeriodType.RED -> R.string.flag_type_red
        FlagPeriodType.SAFETY_CAR -> R.string.flag_type_safety_car
        FlagPeriodType.VIRTUAL_SAFETY_CAR -> R.string.flag_type_virtual_safety_car
        FlagPeriodType.UNKNOWN -> R.string.flag_type_unknown
    }
)

@Composable
private fun FlagPeriodRow(period: FlagPeriodDto) {
    val color = FlagStyle.color(period.periodType)
    val lapRange = if (period.startLap == period.endLap) {
        stringResource(R.string.flag_lap_single, period.startLap)
    } else {
        stringResource(R.string.flag_lap_range, period.startLap, period.endLap)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.MD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.MD),
    ) {
        Icon(
            imageVector = FlagStyle.icon(period.periodType),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = period.periodType.label(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = RcTheme.colors.textPrimary,
            )
            Text(
                text = lapRange,
                style = MaterialTheme.typography.bodySmall,
                color = RcTheme.colors.textSecondary,
            )
            val reason = period.reason
            if (!reason.isNullOrBlank()) {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textTertiary,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun TimelineHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "timeline-chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .clickable(onClick = onToggle)
            .padding(vertical = Dimens.SM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(
            text = stringResource(R.string.flag_timeline_header, count),
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = RcTheme.colors.textSecondary,
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation),
        )
    }
}

@Composable
private fun FlagEventRow(event: FlagEventDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.XS),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(RcShapes.Small)
                .background(RcTheme.colors.textTertiary),
        )
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val lap = event.lap
                if (lap != null) {
                    Text(
                        text = stringResource(R.string.flag_lap_single, lap),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = RcTheme.colors.textPrimary,
                    )
                }
                val code = event.driverCode
                if (!code.isNullOrBlank()) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RcTheme.colors.textSecondary,
                    )
                }
                val timeText = remember(event.time) { FlexibleDate.parse(event.time)?.formatTimeWithZone() }
                if (timeText != null) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = RcTheme.colors.textTertiary,
                    )
                }
            }
            Text(
                text = event.message ?: event.flag ?: event.category ?: "–",
                style = MaterialTheme.typography.bodySmall,
                color = RcTheme.colors.textSecondary,
            )
        }
    }
}
